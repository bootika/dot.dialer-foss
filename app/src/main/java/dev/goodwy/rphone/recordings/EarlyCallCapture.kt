package dev.goodwy.rphone.recordings

import android.telecom.Call
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.EOFException
import java.io.IOException

internal fun captureCallStateAllowed(state: Int) = state in setOf(
    Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_RINGING, Call.STATE_ACTIVE)

/** One explicit request, one early attempt, at most one fallback on this call's connection. */
internal suspend fun captureEarlyCall(
    connected: () -> Boolean,
    valid: () -> Boolean,
    stopped: () -> Boolean,
    open: () -> CaptureChannel,
    waiting: () -> Unit,
    frame: (ByteArray) -> Unit,
    closeAttempt: suspend () -> Unit,
) {
    var waitForConnection = false
    while (!stopped() && valid()) {
        if (waitForConnection) {
            waiting()
            while (!stopped() && valid() && !connected()) delay(100)
            if (stopped() || !valid()) return
        }
        val beganConnected = connected()
        var writingFrame = false
        try {
            open().use { channel ->
                check(channel.status() == "STARTED")
                while (!stopped() && valid()) {
                    val bytes = channel.frame()
                    if (stopped() || !valid()) return
                    if (bytes.isEmpty()) throw EOFException("Capture ended")
                    require(bytes.size in 4..65536 && bytes.size % 4 == 0)
                    writingFrame = true
                    frame(bytes)
                    writingFrame = false
                }
            }
            return
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (stopped() || !valid()) return
            // Storage, validation and authentication failures must not become a deferred retry.
            if (writingFrame || error !is IOException) throw error
            if (beganConnected || waitForConnection) throw error
            waitForConnection = true
        } finally {
            closeAttempt()
        }
        // Allow the worker to release both voice paths before the connected fallback.
        delay(200)
    }
}
