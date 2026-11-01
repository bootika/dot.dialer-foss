package dev.goodwy.rphone.recordings

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CaptureFailureReporterTest {
    @Test fun repeatedFailuresNeverExposeMessagesCausesOrFloodTheLog() {
        val events=mutableListOf<String>()
        val reporter=CaptureFailureReporter(events::add)
        val failure=IllegalStateException("test.user@example.test private token",RuntimeException("private cause"))
        repeat(10000){reporter.report(CaptureFailureStage.HEARTBEAT,failure)}
        assertEquals(listOf("HEARTBEAT:IllegalStateException"),events)
        reporter.report(CaptureFailureStage.FINALIZE,failure)
        assertEquals(2,events.size)
        assertTrue(events.none{it.contains("private") || it.contains("@") || it.contains("cause")})
    }
    @Test fun cancellationEscapesActiveWorkButCleanupCanContinue() {
        val events=mutableListOf<String>()
        val reporter=CaptureFailureReporter(events::add)
        val cancelled=CancellationException("private cancellation context")
        val actual=assertThrows(CancellationException::class.java){reporter.report(CaptureFailureStage.PAIR,cancelled)}
        assertSame(cancelled,actual)
        assertTrue(events.isEmpty())
        reporter.report(CaptureFailureStage.CLOSE_LAUNCH,cancelled,cleanup=true)
        assertEquals(listOf("CLOSE_LAUNCH:CancellationException"),events)
    }
    @Test fun setupDeadlineIsReportedButOuterCancellationIsNotConverted() = kotlinx.coroutines.test.runTest {
        try {
            captureSetupStep(10){kotlinx.coroutines.delay(20);true}
            fail("Owned timeout must fail setup")
        } catch(error: IllegalStateException) {
            assertFalse(error is CancellationException)
        }
        try {
            kotlinx.coroutines.withTimeout(5){captureSetupStep(100){kotlinx.coroutines.delay(20);true}}
            fail("Outer cancellation must escape")
        } catch(cancelled: CancellationException) {
            assertTrue(cancelled is kotlinx.coroutines.TimeoutCancellationException)
        }
        assertEquals("ready",captureSetupStep(10){"ready"})
    }

}
