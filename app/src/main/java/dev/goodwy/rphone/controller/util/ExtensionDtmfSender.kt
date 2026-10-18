package dev.goodwy.rphone.controller.util

import android.telecom.Call
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One manual sequence bound to one live target. No dialing, post-dial suffix or automatic start. */
internal class ExtensionDtmfSender(
    private val scope: CoroutineScope,
    private val allowed: (Call) -> Boolean,
) {
    private var job: Job? = null
    private var target: Call? = null
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    fun send(call: Call, digits: String, onDigit: (Char) -> Unit = {}): Boolean {
        if (!scope.isActive || mutableBusy.value || !digits.matches(Regex("[0-9*#]{1,24}")) || !live(call)) return false
        val handle = call.details?.handle
        val account = call.details?.accountHandle
        fun sameTarget() = live(call) && call.details?.handle == handle && call.details?.accountHandle == account
        target = call
        mutableBusy.value = true
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) { if (state != Call.STATE_ACTIVE) cancel() }
            override fun onDetailsChanged(call: Call, details: Call.Details) { if (!sameTarget()) cancel() }
            override fun onParentChanged(call: Call, parent: Call?) { if (parent != null) cancel() }
            override fun onChildrenChanged(call: Call, children: MutableList<Call>) { if (!sameTarget()) cancel() }
        }
        call.registerCallback(callback)
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for (digit in digits) {
                    if (!sameTarget()) break
                    call.playDtmfTone(digit)
                    try { onDigit(digit); delay(150) } finally { call.stopDtmfTone() }
                    delay(100)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ExtensionDTMF", "Sequence interrupted", error) }
        }.also { task ->
            task.invokeOnCompletion {
                call.unregisterCallback(callback)
                runCatching { call.stopDtmfTone() }
                target = null
                mutableBusy.value = false
            }
            task.start()
        }
        return true
    }

    fun cancel() {
        job?.cancel()
        target?.let { runCatching { it.stopDtmfTone() } }
    }
    private fun live(call: Call) = call.currentTelecomState() == Call.STATE_ACTIVE &&
        !call.isTelecomConference() && call.parent == null && allowed(call)
}
