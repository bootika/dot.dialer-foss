package dev.goodwy.rphone.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** Foreground checks never run inline with Activity/call handling or repeatedly on every resume. */
internal class ForegroundOwnershipRefresh(
    private val scope: CoroutineScope,
    private val reachable: () -> Boolean,
    private val busy: () -> Boolean,
    private val elapsed: () -> Long,
    private val failure: (Exception) -> Unit = { _ -> Unit },
    private val refresh: () -> Unit,
) {
    private var lastAttempt: Long? = null
    fun onForeground() {
        scope.launch {
            yield()
            try {
                if (busy() || !reachable()) return@launch
                val now=elapsed()
                if (lastAttempt?.let {now-it in 0L until 300000L} == true) return@launch
                lastAttempt=now
                refresh()
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { failure(error) }
        }
    }
}
