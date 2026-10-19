package dev.goodwy.rphone.recordings

import android.util.Log

/** Metadata only: no exception messages, audio paths, caller data, transcript or provider bodies. */
internal object RecordingDiagnostics {
    fun failure(stage: String, error: Exception) {
        if(error is kotlinx.coroutines.CancellationException)throw error
        Log.w("RecordingIntegration", "$stage: ${error.javaClass.simpleName}")
    }
}
