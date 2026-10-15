package dev.goodwy.rphone.view.screen

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.controller.util.isTelecomConference
import dev.goodwy.rphone.controller.util.selectOtherLogicalTelecomCall

internal data class TelecomCallSnapshot(
    val state: Int,
    val details: Call.Details?,
    val children: List<Call>,
    val isConference: Boolean,
    val conferenceableCalls: List<Call>,
)

private fun Call.snapshot() = TelecomCallSnapshot(
    state = currentTelecomState(),
    details = details,
    children = children.toList(),
    isConference = isTelecomConference(),
    conferenceableCalls = conferenceableCalls.toList(),
)

/** Call objects mutate in place: equal repository lists must not suppress participant updates. */
@Composable
internal fun rememberTelecomCallSnapshot(call: Call): State<TelecomCallSnapshot> {
    val snapshot = remember(call) { mutableStateOf(call.snapshot(), neverEqualPolicy()) }
    DisposableEffect(call) {
        val callback = object : Call.Callback() {
            private fun refresh() { snapshot.value = call.snapshot() }
            override fun onStateChanged(call: Call, state: Int) = refresh()
            override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
            override fun onChildrenChanged(call: Call, children: MutableList<Call>) = refresh()
            override fun onParentChanged(call: Call, parent: Call?) = refresh()
            override fun onConferenceableCallsChanged(call: Call, calls: MutableList<Call>) = refresh()
            override fun onCallDestroyed(call: Call) = refresh()
        }
        call.registerCallback(callback, Handler(Looper.getMainLooper()))
        snapshot.value = call.snapshot()
        onDispose { call.unregisterCallback(callback) }
    }
    return snapshot
}

/** Parent/state changes can invalidate the secondary call without a new allCalls list. */
@Composable
internal fun rememberOtherLogicalTelecomCall(calls: List<Call>, current: Call): State<Call?> {
    val selected = remember(calls, current) {
        mutableStateOf(selectOtherLogicalTelecomCall(calls, current), neverEqualPolicy())
    }
    DisposableEffect(calls, current) {
        val observed = (calls + current).distinct()
        val callback = object : Call.Callback() {
            private fun refresh() { selected.value = selectOtherLogicalTelecomCall(calls, current) }
            override fun onStateChanged(call: Call, state: Int) = refresh()
            override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
            override fun onChildrenChanged(call: Call, children: MutableList<Call>) = refresh()
            override fun onParentChanged(call: Call, parent: Call?) = refresh()
            override fun onCallDestroyed(call: Call) = refresh()
        }
        val handler = Handler(Looper.getMainLooper())
        observed.forEach { it.registerCallback(callback, handler) }
        selected.value = selectOtherLogicalTelecomCall(calls, current)
        onDispose { observed.forEach { it.unregisterCallback(callback) } }
    }
    return selected
}
