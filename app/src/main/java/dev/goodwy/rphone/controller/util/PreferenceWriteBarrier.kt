package dev.goodwy.rphone.controller.util

import kotlinx.coroutines.*

/** Setters enqueue synchronously; a backup can fence writes from every manager instance. */
internal object PreferenceWriteBarrier {
    private val lock = Any()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tail: Job? = null
    private var failure: Throwable? = null

    fun enqueue(write: suspend () -> Unit) {
        val job = synchronized(lock) {
            val previous = tail
            scope.launch(start = CoroutineStart.LAZY) {
                previous?.join()
                try { write() } catch (error: Exception) {
                    synchronized(lock) { failure = error }
                    android.util.Log.e("Preferences", "Preference persistence failed", error)
                }
            }.also { tail = it }
        }
        job.start()
    }

    suspend fun awaitPending() {
        val barrier = synchronized(lock) { tail }
        barrier?.join()
        synchronized(lock) { failure }?.let { throw java.io.IOException("Preference persistence failed", it) }
    }
}
