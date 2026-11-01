package dev.goodwy.rphone.recordings

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull

internal enum class CaptureFailureStage {
    DISCOVERY, CONNECT, SESSION, HANDSHAKE,
    AVAILABILITY, RECOVER, HEARTBEAT, CAPTURE, STOP_ATTEMPT, STOP_FINAL, STOP_REQUEST,
    FINALIZE, AUTHORIZATION, PROBE, QUIET, PAIR, FINISH, BOOTSTRAP, CLOSE_LAUNCH,
    CLOSE_TIMEOUT, CLEAN_PAIR, DISCONNECT, QUIET_QUIT, QUIT, CONTACT_LOOKUP,
    SETUP_SETTINGS, MODULE_SWITCH, NOTIFICATION_PERMISSION,
}

/** Local, bounded diagnostics: never retain/log exception messages, causes or stack traces. */
internal class CaptureFailureReporter(private val write: (String) -> Unit) {
    private val reported = linkedSetOf<String>()
    @Synchronized fun report(stage: CaptureFailureStage, error: Exception, cleanup: Boolean = false) {
        // Active work must not turn cancellation into an unavailable/failed state.
        // Cleanup remains best effort so one failed release cannot prevent the others.
        if (error is CancellationException && !cleanup) throw error
        val code = "${stage.name}:${error.javaClass.simpleName}"
        if (code in reported) return
        if (reported.size == 64) reported.remove(reported.first())
        reported.add(code)
        write(code)
    }
}

internal val captureFailures = CaptureFailureReporter {
    Log.w("CallCaptureFailure", it)
    val stage = when (it.substringBefore(':')) {
        "CAPTURE" -> "capture"
        "FINALIZE" -> "capture_finalize"
        "RECOVER" -> "capture_recover"
        else -> null // Discovery, availability, cleanup and user permissions are expected states.
    }
    if (stage != null && !it.endsWith(":CancellationException"))
        io.github.bootika.dotdialer.observability.AppTelemetry.operationalFailure(stage, "internal")
}

/** Only this step's deadline becomes an ordinary setup failure; parent cancellation still escapes. */
internal suspend fun <T: Any> captureSetupStep(timeoutMs: Long, block: suspend CoroutineScope.() -> T): T =
    checkNotNull(withTimeoutOrNull(timeoutMs, block)) { "Capture setup timeout" }
