package dev.goodwy.rphone.controller

import android.telecom.Call
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.modal.`interface`.CallSession
import kotlinx.coroutines.delay

/** Keeps pre-Telecom intent lifetime separate from the real session's exit presentation. */
@Composable
internal fun rememberDirectCallRequest(hasManagedCall: () -> Boolean, cancel: () -> Unit): DirectCallWindow.Request? {
    val request by DirectCallWindow.state.collectAsStateWithLifecycle()
    BackHandler(enabled = request?.submitted == false, onBack = cancel)
    LaunchedEffect(request?.id) {
        val pending = request ?: return@LaunchedEffect
        if (pending.submitted) return@LaunchedEffect
        withFrameNanos { }
        delay(DirectCallWindow.DURATION_MILLIS)
        if (hasManagedCall()) cancel() else DirectCallWindow.commit(pending.id)
    }
    return request
}

@Composable
internal fun rememberPresentedCallSession(
    session: CallSession?,
    outgoing: PendingOutgoingCallLaunch,
    hasDeferredPresentation: Boolean,
    onExpired: () -> Unit,
): CallSession? {
    var retained by remember { mutableStateOf<CallSession?>(null) }
    var replacedCall by remember { mutableStateOf<Call?>(null) }
    val waitingForReplacement = (outgoing.isPending || hasDeferredPresentation) &&
        (session == null || session.state == Call.STATE_DISCONNECTING || session.state == Call.STATE_DISCONNECTED)
    val visibleSession = session?.takeUnless { waitingForReplacement || it.call === replacedCall }
    SideEffect {
        if (waitingForReplacement) {
            replacedCall = session?.call ?: retained?.call ?: replacedCall
            retained = null
        }
    }
    LaunchedEffect(outgoing.deadlineMillis) {
        if (outgoing.awaitExpiry()) { DirectCallWindow.cancel(); onExpired() }
    }
    LaunchedEffect(visibleSession, outgoing.deadlineMillis) {
        visibleSession?.let {
            if (DirectCallWindow.state.value?.submitted == false) DirectCallWindow.cancel()
            DirectCallWindow.consume(directCallRequestId(it.call))
            retained = it
            outgoing.consume()
        }
    }
    return if (waitingForReplacement) null else visibleSession ?: retained
}
