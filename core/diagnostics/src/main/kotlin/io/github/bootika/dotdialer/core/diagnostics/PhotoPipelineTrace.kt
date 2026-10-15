package io.github.bootika.dotdialer.core.diagnostics

/** Per-attempt monotonic measurements, never contact identities or wall-clock durations. */
class PhotoPipelineTrace(private val session: Int, private val attempt: Int,
    private val now: () -> Long, private val emit: (DiagnosticDetail.PhotoPipeline) -> Unit) {
    private val started = now()
    private var previous = started
    private var ended = false
    var recorderFailures = 0
        private set
    fun mark(stage: PhotoPipelineStage, uri: Boolean = false, decoded: Boolean = false) {
        if (ended) return
        val current = maxOf(previous, now())
        val event = DiagnosticDetail.PhotoPipeline(session, attempt, stage, current - started,
            current - previous, uri, decoded)
        previous = current
        ended = stage in setOf(PhotoPipelineStage.METADATA_READY, PhotoPipelineStage.PUBLISHED,
            PhotoPipelineStage.CANCELLED, PhotoPipelineStage.DISCARDED, PhotoPipelineStage.FAILED)
        try { emit(event) } catch (failure: Exception) { recorderFailures++ }
    }
}
