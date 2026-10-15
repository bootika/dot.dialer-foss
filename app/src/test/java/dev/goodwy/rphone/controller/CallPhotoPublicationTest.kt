package dev.goodwy.rphone.controller

import io.github.bootika.dotdialer.core.diagnostics.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CallPhotoPublicationTest {
    @Test fun cancelledQueuedAttemptStillHasTerminalRecord() = runTest {
        val events = mutableListOf<DiagnosticDetail.PhotoPipeline>()
        val trace = PhotoPipelineTrace(1, 10, { 0L }, events::add)
        trace.mark(PhotoPipelineStage.REQUESTED)
        val job = launch(start = CoroutineStart.LAZY) { fail("cancelled job must not begin decoding") }
        job.completeCallPhotoTrace(trace)
        job.cancel(); job.join()
        assertEquals(listOf(PhotoPipelineStage.REQUESTED, PhotoPipelineStage.CANCELLED), events.map { it.stage })
    }
    @Test fun decodePendingCannotPublishAndCancelledAttemptIsDistinct() = runTest {
        val decoded = CompletableDeferred<String?>()
        val events = mutableListOf<DiagnosticDetail.PhotoPipeline>()
        var time = 10L
        val trace = PhotoPipelineTrace(1, 7, { time }, events::add)
        trace.mark(PhotoPipelineStage.REQUESTED)
        var published = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            publishCallPhotoNotification(true, { decoded.await() }, { true }, { it.orEmpty() },
                { published = true }, trace)
        }
        assertFalse(published)
        time = 37
        job.cancel(); job.join()
        assertEquals(PhotoPipelineStage.CANCELLED, events.last().stage)
        assertEquals(27L, events.last().elapsedMs)
        assertTrue(events.all { it.attempt == 7 })
        assertFalse(published)
    }

    @Test fun stalePhotoIsDiscardedAndNullPhotoStillPublishesFallback() = runTest {
        val events = mutableListOf<DiagnosticDetail.PhotoPipeline>()
        val trace = PhotoPipelineTrace(2, 8, { 1L }, events::add)
        var builds = 0
        assertFalse(publishCallPhotoNotification(true, { "photo" }, { false }, { builds++; "notification" },
            { fail("stale publication") }, trace))
        assertEquals(0, builds)
        assertEquals(PhotoPipelineStage.DISCARDED, events.last().stage)
        assertTrue(events.last().decodedPhoto)
        var fallback = ""
        assertTrue(publishCallPhotoNotification(false, { null as String? }, { true }, { it ?: "fallback" }, { fallback = it }))
        assertEquals("fallback", fallback)
    }

    @Test fun buildAndPublishFailuresCannotBeLoggedAsSuccess() = runTest {
        for (buildFailure in listOf(true, false)) {
            val events = mutableListOf<DiagnosticDetail.PhotoPipeline>()
            val trace = PhotoPipelineTrace(3, 9, { 4L }, events::add)
            try {
                publishCallPhotoNotification(true, { "photo" }, { true },
                    { if (buildFailure) throw IllegalStateException("fixture") else "notification" },
                    { throw IllegalStateException("fixture") }, trace)
                fail("must preserve failure")
            } catch (_: IllegalStateException) {
                assertEquals(PhotoPipelineStage.FAILED, events.last().stage)
                assertFalse(events.any { it.stage == PhotoPipelineStage.PUBLISHED })
            }
        }
    }
}
