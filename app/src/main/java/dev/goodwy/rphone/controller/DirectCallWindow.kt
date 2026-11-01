package dev.goodwy.rphone.controller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread owned, process-local intent. Never restored as a real Telecom call. */
internal object DirectCallWindow {
    data class Request(
        val id: String, val number: String, val contactId: String?, val commit: (String) -> Unit,
        val accountKey: String? = null, val submitted: Boolean = false, val cancelling: Boolean = false,
    )
    private val current = MutableStateFlow<Request?>(null)
    val state = current.asStateFlow()
    private var delivered: Pair<String, () -> Unit>? = null

    fun begin(number: String, contactId: String?, accountKey: String? = null, commit: (String) -> Unit): Boolean {
        if (current.value != null) return false
        current.value = Request(java.util.UUID.randomUUID().toString(), number, contactId, commit, accountKey)
        return true
    }

    fun cancel() { current.value = null; delivered = null }

    fun commit(id: String) {
        val request = current.value?.takeIf { it.id == id && !it.submitted } ?: return
        current.value = request.copy(submitted = true)
        request.commit(id)
    }

    /** True when cancellation can close the presentation now; otherwise wait for Telecom. */
    fun requestCancellation(): Boolean {
        val request = current.value ?: return true
        if (!request.submitted) { cancel(); return true }
        current.value = request.copy(cancelling = true)
        delivered?.takeIf { it.first == request.id }?.second?.let { disconnect ->
            disconnect(); cancel(); return true
        }
        return false
    }

    fun bindTelecomCall(id: String?, outgoing: Boolean, disconnect: () -> Unit) {
        val request = current.value?.takeIf { it.submitted && it.id == id && outgoing } ?: return
        delivered = request.id to disconnect
        if (request.cancelling) { disconnect(); cancel() }
    }

    fun consume(id: String?) {
        if (id != null && current.value?.id == id) cancel()
    }

    fun fail(id: String?) {
        if (id != null && current.value?.id == id) cancel()
    }

    const val DURATION_MILLIS = 1_000L
    const val REQUEST_EXTRA = "dev.goodwy.rphone.extra.DIRECT_CALL_REQUEST"
}
