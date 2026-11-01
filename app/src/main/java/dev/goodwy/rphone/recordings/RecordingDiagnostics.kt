package dev.goodwy.rphone.recordings

import android.util.Log

/** Metadata only: no exception messages, audio paths, caller data, transcript or provider bodies. */
internal object RecordingDiagnostics {
    fun failure(stage: String, error: Exception) {
        if(error is kotlinx.coroutines.CancellationException)throw error
        Log.w("RecordingIntegration", "$stage: ${error.javaClass.simpleName}")
        val safeStage = when (stage) {
            "untrusted_reply", "invalid_reply" -> "bridge_reply"
            "RecordingSyncEngine", "RecordingSyncJob" -> "bridge_sync"
            "RecordingsLibrary" -> "library"
            else -> return
        }
        val code = when (error) {
            is SecurityException -> "permission"
            is java.io.IOException -> "storage"
            is IllegalArgumentException -> "invalid_response"
            else -> "internal"
        }
        io.github.bootika.dotdialer.observability.AppTelemetry.operationalFailure(safeStage, code)
    }
}
