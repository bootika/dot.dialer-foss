package dev.goodwy.rphone.controller

import io.github.bootika.dotdialer.core.call.CallPresentationPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class CallUiTransfer(val token: Long, val launchRequested: Boolean = false)

/** Process-local outgoing handoff; no identity, settings, call control or persistent state. */
internal class CallUiHandoffController(private val scope: CoroutineScope) {
    private val lock = Any()
    private var generation = 0L
    private var requestedToken: Long? = null
    private var timeout: Job? = null
    private val pending = MutableStateFlow<CallUiTransfer?>(null)
    val state: StateFlow<CallUiTransfer?> = pending.asStateFlow()

    fun beginOutgoing(): Long = synchronized(lock) {
        val token = ++generation
        timeout?.cancel()
        requestedToken = null
        pending.value = CallUiTransfer(token)
        timeout = scope.launch {
            delay(CallPresentationPolicy.OUTGOING_SESSION_GRACE_MILLIS)
            cancel(token)
        }
        token
    }

    /** Null means another caller already requested this same outgoing surface. */
    fun claimLaunch(): Long? = synchronized(lock) {
        if (pending.value == null && requestedToken == generation) return@synchronized null
        val transfer = pending.value ?: run { beginOutgoing(); requireNotNull(pending.value) }
        if (transfer.launchRequested) return@synchronized null
        pending.value = transfer.copy(launchRequested = true)
        requestedToken = transfer.token
        transfer.token
    }

    fun cancel(token: Long) = synchronized(lock) {
        if (pending.value?.token == token || requestedToken == token) {
            timeout?.cancel()
            timeout = null
            pending.value = null
            requestedToken = null
        }
    }

    fun contentReady(token: Long? = pending.value?.token ?: requestedToken) = synchronized(lock) {
        if (token != null && (pending.value?.token == token || requestedToken == token)) {
            requestedToken = token
            pending.value = null
        }
    }

    fun reset() = synchronized(lock) {
        (pending.value?.token ?: requestedToken)?.let(::cancel)
    }
}

internal object CallUiHandoff {
    val controller = CallUiHandoffController(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
}

internal fun showHomeCallBanner(ongoing: Boolean, handoff: Boolean, callUiVisible: Boolean): Boolean =
    ongoing && !handoff && !callUiVisible
