package dev.goodwy.rphone.controller

import android.os.SystemClock
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticLevel
import io.github.bootika.dotdialer.core.diagnostics.PhotoPipelineStage
import io.github.bootika.dotdialer.core.diagnostics.PhotoPipelineTrace
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

internal fun beginCallPhotoTrace(sessionId: String?, attempt: Int, metadata: Boolean = false): PhotoPipelineTrace? {
    if (!AppDiagnostics.allows(DiagnosticLevel.DEBUG)) return null
    val session = sessionId?.removePrefix("call-")?.toIntOrNull() ?: return null
    return PhotoPipelineTrace(session, attempt, SystemClock::elapsedRealtime, AppDiagnostics::recordDetail).also {
        it.mark(if (metadata) PhotoPipelineStage.METADATA_REQUESTED else PhotoPipelineStage.REQUESTED)
    }
}

internal fun Job.completeCallPhotoTrace(trace: PhotoPipelineTrace?) {
    if (trace == null) return
    invokeOnCompletion { cause ->
        if (cause is CancellationException) trace.mark(PhotoPipelineStage.CANCELLED)
        else if (cause != null) trace.mark(PhotoPipelineStage.FAILED)
    }
}

/** Preserve publication ordering and cancellation; diagnostics cannot turn stale work into a notification. */
internal suspend fun <Photo : Any, Notification : Any> publishCallPhotoNotification(
    uriPresent: Boolean, decode: suspend () -> Photo?, current: () -> Boolean,
    build: (Photo?) -> Notification, publish: (Notification) -> Unit,
    trace: PhotoPipelineTrace? = null,
): Boolean {
    try {
        trace?.mark(PhotoPipelineStage.DECODE_STARTED, uriPresent)
        val photo = decode()
        trace?.mark(PhotoPipelineStage.DECODE_COMPLETED, uriPresent, photo != null)
        if (!current()) { trace?.mark(PhotoPipelineStage.DISCARDED, uriPresent, photo != null); return false }
        val notification = build(photo)
        trace?.mark(PhotoPipelineStage.NOTIFICATION_BUILT, uriPresent, photo != null)
        publish(notification)
        trace?.mark(PhotoPipelineStage.PUBLISHED, uriPresent, photo != null)
        return true
    } catch (cancelled: CancellationException) {
        trace?.mark(PhotoPipelineStage.CANCELLED, uriPresent)
        throw cancelled
    } catch (failure: Exception) {
        trace?.mark(PhotoPipelineStage.FAILED, uriPresent)
        throw failure
    }
}
