package dev.goodwy.rphone.view.screen.recordings

import dev.goodwy.rphone.recordings.RecordingJob

internal data class RecordingJobPresentation(val current: List<RecordingJob>, val history: List<RecordingJob>)

private data class RecordingRequest(val recordingId: String, val operation: String, val language: String, val sourceTextId: Long)

/** Catalogue order is newest first. Preserve attempts; only superseded failures change their UI section. */
internal fun recordingJobPresentation(jobsNewestFirst: List<RecordingJob>): RecordingJobPresentation {
    val seen = mutableSetOf<RecordingRequest>()
    val current = mutableListOf<RecordingJob>()
    val history = mutableListOf<RecordingJob>()
    for (job in jobsNewestFirst) {
        val latest = seen.add(RecordingRequest(job.recordingId, job.operation, job.language, job.sourceTextId))
        if (job.state in setOf("completed", "cancelled") || job.state == "failed" && !latest) history += job
        else current += job
    }
    return RecordingJobPresentation(current.toList(), history.toList())
}