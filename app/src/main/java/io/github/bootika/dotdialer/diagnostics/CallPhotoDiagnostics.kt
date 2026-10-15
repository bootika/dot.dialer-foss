package io.github.bootika.dotdialer.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import dev.goodwy.rphone.controller.eligibleCallPhotoNumber
import dev.goodwy.rphone.controller.telecomCallerPhotoUri
import dev.goodwy.rphone.controller.util.CallPhotoHydrationOutcome
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.data.manager.CallStateManager
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticDetail
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel
import io.github.bootika.dotdialer.core.diagnostics.PhotoDiagnosticStage
import io.github.bootika.dotdialer.core.diagnostics.PhotoHydrationDiagnostic
import io.github.bootika.dotdialer.core.diagnostics.PhotoPipelineStage
import io.github.bootika.dotdialer.core.diagnostics.PhotoPipelineTrace
import dev.goodwy.rphone.controller.beginCallPhotoTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.IdentityHashMap

/** Main-thread session map; async read-only probes, never modifies call routing or contacts. */
internal class CallPhotoDiagnostics(
    private val context: Context, private val scope: CoroutineScope,
    private val metadata: CallStateManager, private val sessionId: (Call) -> String?,
) {
    private class Entry(val number: String, val session: Int, var signature: DiagnosticDetail.PhotoState? = null,
        var job: Job? = null, var probes: Int = 0, val metadataTrace: PhotoPipelineTrace? = null)
    private val calls = IdentityHashMap<Call, Entry>()
    private val identities = IdentityHashMap<Call, DiagnosticDetail.CallIdentity>()
    private var projectionType = -1
    private var lastConnection: DiagnosticDetail.ConnectionState? = null
    private var projection: ProjectionDiagnosticMonitor? = null
    private var projectionStarted = false
    private var connectionJob: Job? = null
    private val levelObserver = scope.launch {
        AppDiagnostics.levels().collect { level ->
            if (level == DiagnosticLevel.NORMAL) {
                (calls.keys.toList() + identities.keys.toList()).distinct().forEach(::remove)
                connectionJob?.cancel()
                projection?.close(); projection = null; projectionStarted = false
            }
        }
    }
    private val observer = scope.launch {
        metadata.metadataVersion.collect {
            calls.keys.toList().forEach { snapshot(it, PhotoDiagnosticStage.METADATA) }
        }
    }

    fun update(call: Call) {
        if (!AppDiagnostics.allows(DiagnosticLevel.DEBUG)) { remove(call); return }
        connectionSnapshot()
        recordIdentity(call)
        val number = eligibleCallPhotoNumber(call)
        if (number.isBlank()) { removePhoto(call); return }
        if (calls[call]?.number != number) {
            removePhoto(call)
            val session = sessionId(call)?.removePrefix("call-")?.toIntOrNull() ?: return
            calls[call] = Entry(number, session, metadataTrace = beginCallPhotoTrace("call-$session", 0, metadata = true))
        }
        snapshot(call, PhotoDiagnosticStage.UPDATE)
    }

    fun hydration(call: Call, outcome: CallPhotoHydrationOutcome) {
        val result = when (outcome) {
            CallPhotoHydrationOutcome.Updated -> PhotoHydrationDiagnostic.UPDATED
            CallPhotoHydrationOutcome.AlreadyAvailable -> PhotoHydrationDiagnostic.AVAILABLE
            CallPhotoHydrationOutcome.Disabled -> PhotoHydrationDiagnostic.DISABLED
            CallPhotoHydrationOutcome.NotEligible -> PhotoHydrationDiagnostic.INELIGIBLE
            CallPhotoHydrationOutcome.ConsentRequired -> PhotoHydrationDiagnostic.CONSENT
            CallPhotoHydrationOutcome.Cooldown -> PhotoHydrationDiagnostic.COOLDOWN
            is CallPhotoHydrationOutcome.Failed -> PhotoHydrationDiagnostic.FAILED
        }
        snapshot(call, PhotoDiagnosticStage.HYDRATION, result)
    }

    fun providerChanged() = calls.keys.toList().forEach { snapshot(it, PhotoDiagnosticStage.PROVIDER) }

    private fun snapshot(call: Call, stage: PhotoDiagnosticStage, hydration: PhotoHydrationDiagnostic = PhotoHydrationDiagnostic.NONE) {
        recordIdentity(call)
        val entry = calls[call] ?: return
        if (!AppDiagnostics.allows(DiagnosticLevel.DEBUG) || eligibleCallPhotoNumber(call) != entry.number) {
            remove(call); return
        }
        val resolved = metadata.callerMetadataMap.value[entry.number]
        if (resolved != null) entry.metadataTrace?.mark(PhotoPipelineStage.METADATA_READY, !resolved.photoUri.isNullOrBlank())
        val event = DiagnosticDetail.PhotoState(entry.session, stage, call.currentTelecomState(),
            resolved != null, resolved?.isLocalContact == true, !resolved?.photoUri.isNullOrBlank(),
            telecomCallerPhotoUri(call) != null, hydration)
        if (event == entry.signature) return
        entry.signature = event
        AppDiagnostics.recordDetail(event)
        if (!AppDiagnostics.allows(DiagnosticLevel.VERBOSE) || entry.probes >= 20) return
        entry.job?.cancel()
        entry.job = scope.launch {
            delay(200)
            entry.probes++
            val result = withContext(Dispatchers.IO) { readContactPhotoProbe(context, entry.number, entry.session) }
            if (calls[call] === entry && eligibleCallPhotoNumber(call) == entry.number) AppDiagnostics.recordDetail(result)
        }
    }

    @Suppress("DEPRECATION")
    fun audioChanged(state: CallAudioState?) {
        if (state == null || !AppDiagnostics.allows(DiagnosticLevel.DEBUG)) return
        val permission = Build.VERSION.SDK_INT < 31 ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        AppDiagnostics.recordDetail(DiagnosticDetail.AudioState(state.route,
            state.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0, permission, state.supportedRouteMask))
        connectionSnapshot()
    }

    private fun connectionSnapshot() {
        if (!AppDiagnostics.allows(DiagnosticLevel.DEBUG)) return
        if (!projectionStarted) {
            projectionStarted = true
            try { projection = ProjectionDiagnosticMonitor(context) { type -> projectionType = type; connectionSnapshot() } }
            catch (failure: RuntimeException) { projectionType = -1 }
        }
        connectionJob?.cancel()
        connectionJob = scope.launch {
            delay(50)
            val projection = projectionType
            val event = withContext(Dispatchers.IO) { readCarConnection(context, projection) }
            if (lastConnection != event) { lastConnection = event; AppDiagnostics.recordDetail(event) }
        }
    }

    private fun recordIdentity(call: Call) {
        if(!AppDiagnostics.allows(DiagnosticLevel.DEBUG)) return
        val session = sessionId(call)?.removePrefix("call-")?.toIntOrNull() ?: return
        val event = readCallIdentityDiagnostic(context,call,session)
        if(identities[call] != event) { identities[call] = event; AppDiagnostics.recordDetail(event) }
    }
    fun remove(call: Call) { identities.remove(call); removePhoto(call) }
    private fun removePhoto(call: Call) { calls.remove(call)?.let { it.job?.cancel(); it.metadataTrace?.mark(PhotoPipelineStage.DISCARDED) } }
    fun close() { projection?.close(); connectionJob?.cancel(); levelObserver.cancel(); observer.cancel(); (calls.keys.toList() + identities.keys.toList()).distinct().forEach(::remove) }
}
