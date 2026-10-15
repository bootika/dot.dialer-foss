package io.github.bootika.dotdialer.core.diagnostics

import org.junit.Assert.*
import org.junit.Test

class PhotoPipelineTraceTest {
    @Test fun monotonicStepsAndTerminalCannotProduceLateSuccess() {
        var time = 50L
        val events = mutableListOf<DiagnosticDetail.PhotoPipeline>()
        val trace = PhotoPipelineTrace(1, 2, { time }, events::add)
        trace.mark(PhotoPipelineStage.REQUESTED)
        time = 60; trace.mark(PhotoPipelineStage.DECODE_STARTED, true)
        time = 85; trace.mark(PhotoPipelineStage.DECODE_COMPLETED, true, true)
        time = 80; trace.mark(PhotoPipelineStage.CANCELLED)
        trace.mark(PhotoPipelineStage.PUBLISHED)
        assertEquals(4, events.size)
        assertEquals(25L, events[2].stepMs)
        assertEquals(35L, events.last().elapsedMs)
        assertEquals(0L, events.last().stepMs)
    }
    @Test fun safeTypedEventAndRecorderFailureCannotBreakCall() {
        val trace = PhotoPipelineTrace(1, 3, { 0L }, { throw IllegalStateException("fixture") })
        trace.mark(PhotoPipelineStage.PUBLISHED)
        assertEquals(1, trace.recorderFailures)
        val event = DiagnosticDetail.PhotoPipeline(1, 3, PhotoPipelineStage.PUBLISHED, 9, 4, true, true)
        assertEquals(DiagnosticLevel.DEBUG, event.level)
        assertFalse(event.javaClass.declaredFields.any { it.type == String::class.java || it.type.isArray })
        val output = DiagnosticDetailFormatter.format(event)
        assertTrue(output.contains("attempt=3"))
        assertTrue(output.contains("elapsed_ms=9"))
        assertFalse(output.contains("fixture"))
    }
}
