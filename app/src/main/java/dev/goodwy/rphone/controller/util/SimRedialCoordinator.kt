package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.R
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Application-owned transaction: Activity dismissal must not cancel a requested redial. */
internal class SimRedialCoordinator(
    context: Context,
    private val repository: ICallRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val accounts: () -> List<CallingAccountInfo> = { getCallingAccounts(context).filter { it.isSim } },
    private val dial: (String, PhoneAccountHandle) -> Unit = { number, handle -> makeCall(context.applicationContext, number, handle) },
) {
    private val appContext = context.applicationContext
    private val pending = MutableStateFlow(false)
    val busy = pending.asStateFlow()

    fun canSwitch(call: Call): Boolean = !pending.value && eligibleSimRedial(call, repository.allCalls.value) &&
        accounts().any { it.handle == call.details?.accountHandle } &&
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun request(call: Call, target: PhoneAccountHandle): Boolean {
        if (!canSwitch(call)) return false
        val original = call.details?.accountHandle ?: return false
        if (target == original || accounts().none { it.handle == target }) return false
        val number = selectedPhoneNumber(call.details?.handle?.schemeSpecificPart) ?: return false
        pending.value = true
        scope.launch {
            try {
                coroutineScope {
                    val competing = launch(start = CoroutineStart.UNDISPATCHED) {
                        repository.allCalls.collect { calls ->
                            if (calls.any { it !== call && it.currentTelecomState() != Call.STATE_DISCONNECTED })
                                this@coroutineScope.cancel("Another call superseded SIM redial")
                        }
                    }
                    try {
                        if (!eligibleSimRedial(call, repository.allCalls.value)) return@coroutineScope
                        call.disconnect()
                        withTimeout(10_000) { awaitDisconnected(call) }
                        ensureActive()
                        if (call.currentTelecomState() != Call.STATE_DISCONNECTED || call.details?.accountHandle != original ||
                            repository.allCalls.value.any { it !== call && it.currentTelecomState() != Call.STATE_DISCONNECTED } ||
                            accounts().none { it.handle == target } ||
                            ContextCompat.checkSelfPermission(appContext, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return@coroutineScope
                        // An explicit handle avoids changing or consulting the user's preferred SIM.
                        dial(number, target)
                    } finally { competing.cancel() }
                }
            } catch (_: TimeoutCancellationException) {
                Toast.makeText(appContext, R.string.sim_redial_unavailable, Toast.LENGTH_SHORT).show()
            } catch (_: CancellationException) {
                // Timeout, replacement calls and cancellation must never start a second call.
            } catch (failure: RuntimeException) {
                android.util.Log.w("SimRedial", "Unable to complete SIM redial", failure)
                Toast.makeText(appContext, R.string.sim_redial_unavailable, Toast.LENGTH_SHORT).show()
            } finally { pending.value = false }
        }
        return true
    }
}

internal fun eligibleSimRedial(call: Call, calls: List<Call>): Boolean {
    val details = call.details ?: return false
    val number = selectedPhoneNumber(details.handle?.schemeSpecificPart) ?: return false
    @Suppress("DEPRECATION")
    val emergency = PhoneNumberUtils.isEmergencyNumber(number)
    return !emergency && details.callDirection == Call.Details.DIRECTION_OUTGOING &&
        details.handlePresentation == TelecomManager.PRESENTATION_ALLOWED &&
        details.accountHandle != null && call.parent == null && !call.isTelecomConference() &&
        call.currentTelecomState() in setOf(Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_ACTIVE) &&
        calls.any { it === call } && calls.none { it !== call && it.currentTelecomState() != Call.STATE_DISCONNECTED }
}

private suspend fun awaitDisconnected(call: Call) {
    val completed = CompletableDeferred<Unit>()
    val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED) completed.complete(Unit)
        }
    }
    call.registerCallback(callback, Handler(Looper.getMainLooper()))
    try {
        if (call.currentTelecomState() == Call.STATE_DISCONNECTED) completed.complete(Unit)
        completed.await()
    } finally { call.unregisterCallback(callback) }
}
