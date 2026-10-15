package dev.goodwy.rphone.controller

import android.net.Uri
import android.os.Build
import android.telecom.Call
import dev.goodwy.rphone.controller.util.CallPhotoHydrationOutcome
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.data.manager.CallStateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.IdentityHashMap

/** Main-thread, service-owned photo enrichment. No Activity or Bluetooth vendor dependency. */
internal class CallPhotoSessionCoordinator(
    private val scope: CoroutineScope,
    private val metadata: CallStateManager,
    private val hydrate: suspend (String) -> CallPhotoHydrationOutcome,
    changes: Flow<Unit>,
    private val isPresent: (Call) -> Boolean,
    private val diagnostics: io.github.bootika.dotdialer.diagnostics.CallPhotoDiagnostics? = null,
    private val regionForCall: (Call) -> String? = { null },
) {
    private class Entry(val number: String, var name: String?, var fallback: String?, var region: String?, var job: Job? = null)
    private val entries = IdentityHashMap<Call, Entry>()
    private var providerRefresh: Job? = null
    private val observer = scope.launch {
        changes.collect {
            providerRefresh?.cancel()
            providerRefresh = scope.launch {
                delay(100)
                diagnostics?.providerChanged()
                entries.entries.filter { current(it.key, it.value) }.map { it.value }
                    .distinctBy { it.number }.forEach { refresh(it, force = true) }
            }
        }
    }

    fun update(call: Call) {
        diagnostics?.update(call)
        if (call.currentTelecomState() in listOf(Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED)) {
            pause(call)
            return
        }
        val number = eligibleCallPhotoNumber(call)
        if (!isPresent(call) || number.isBlank()) {
            remove(call)
            return
        }
        val name = call.details?.callerDisplayName?.takeIf {
            call.details?.callerDisplayNamePresentation == android.telecom.TelecomManager.PRESENTATION_ALLOWED
        }
        val fallback = telecomCallerPhotoUri(call)
        val old = entries[call]
        if (old?.number == number) {
            old.name = name
            old.fallback = fallback
            old.region = regionForCall(call)
            refresh(old)
            return
        }
        remove(call)
        val entry = Entry(number, name, fallback, regionForCall(call))
        entries[call] = entry
        val initialLookup = refresh(entry)
        entry.job = scope.launch {
            try {
                initialLookup?.join()
                if (!current(call, entry)) return@launch
                val outcome = hydrate(number)
                if (current(call, entry)) diagnostics?.hydration(call, outcome)
                if (current(call, entry) && outcome == CallPhotoHydrationOutcome.Updated) {
                    refresh(entry, force = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Photo enrichment failure must not interrupt Telecom or disclose contact data.
                android.util.Log.w("CallPhotos", "Photo enrichment failed (${failure.javaClass.simpleName})")
            }
        }
    }

    private fun current(call: Call, entry: Entry): Boolean =
        entries[call] === entry && isPresent(call) && eligibleCallPhotoNumber(call) == entry.number

    private fun refresh(entry: Entry, force: Boolean = false): Job? =
        metadata.onNewCallReceived(entry.number, entry.name, entry.fallback, force, entry.region)

    private fun pause(call: Call) {
        val entry = entries[call] ?: return
        entry.job?.cancel()
        if (entries.entries.none { it.key !== call && it.value.number == entry.number && current(it.key, it.value) }) {
            // Retain the last resolved identity until missed-call delivery captures it or removal.
            metadata.cancelPendingLookup(entry.number)
        }
    }

    fun remove(call: Call) {
        diagnostics?.remove(call)
        val entry = entries.remove(call) ?: return
        entry.job?.cancel()
        if (entries.values.none { it.number == entry.number }) metadata.onCallEnded(entry.number)
    }

    fun close() {
        diagnostics?.close()
        observer.cancel()
        providerRefresh?.cancel()
        entries.keys.toList().forEach(::remove)
    }

    fun audioChanged(state: android.telecom.CallAudioState?) = diagnostics?.audioChanged(state)
}

internal fun eligibleCallPhotoNumber(call: Call): String {
    if (call.currentTelecomState() in listOf(Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED)) return ""
    val number = Uri.decode(notificationCallerNumber(call))
    return number.takeIf { it.any(Char::isDigit) && '*' !in it && '#' !in it }.orEmpty()
}

internal fun telecomCallerPhotoUri(call: Call, sdk: Int = Build.VERSION.SDK_INT): String? {
    if (Build.VERSION.SDK_INT < 34 || sdk < 34 || eligibleCallPhotoNumber(call).isBlank()) return null
    return try {
        call.details?.contactPhotoUri?.takeIf { it.scheme == "content" }?.toString()
    } catch (failure: RuntimeException) {
        android.util.Log.w("CallPhotos", "Telecom photo unavailable (${failure.javaClass.simpleName})")
        null
    }
}

/** Use the photo already supplied by Telecom while our contact lookup is still pending. */
internal fun notificationCallPhotoUri(call: Call, cached: String?, sdk: Int = Build.VERSION.SDK_INT): String? {
    if (eligibleCallPhotoNumber(call).isBlank()) return null
    return cached?.takeIf { it.isNotBlank() } ?: telecomCallerPhotoUri(call, sdk)
}
