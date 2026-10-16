package dev.goodwy.rphone.controller

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.bootika.dotdialer.core.call.CallPresentationPolicy
import kotlinx.coroutines.delay

/** A one-shot launch grace, distinct from the lifetime of the resulting Telecom session. */
internal class PendingOutgoingCallLaunch {
    var deadlineMillis by mutableStateOf<Long?>(null)
        private set

    val isPending get() = deadlineMillis?.let { SystemClock.elapsedRealtime() < it } == true

    fun restore(savedState: Bundle?, requested: Boolean) {
        if (savedState == null) {
            start(requested)
        } else {
            deadlineMillis = savedState.getLong(STATE_DEADLINE, 0L)
                .takeIf { it > SystemClock.elapsedRealtime() }
        }
    }

    fun start(requested: Boolean) {
        deadlineMillis = if (requested) {
            SystemClock.elapsedRealtime() + CallPresentationPolicy.OUTGOING_SESSION_GRACE_MILLIS
        } else null
    }

    fun consume() {
        deadlineMillis = null
    }

    fun save(outState: Bundle) {
        outState.putLong(STATE_DEADLINE, deadlineMillis ?: 0L)
    }

    suspend fun awaitExpiry(): Boolean {
        val deadline = deadlineMillis ?: return false
        delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        if (deadlineMillis != deadline) return false
        consume()
        return true
    }

    private companion object {
        const val STATE_DEADLINE = "outgoing_launch_deadline"
    }
}
