package dev.goodwy.rphone.recordings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DictateBridgeTimeoutTest {
    @Test fun ownedDeadlineBecomesRecoverableDisconnect() = runTest {
        try {
            bridgeRequestStep(10) { delay(20); "late" }
            fail("Owned deadline must become a recoverable failure")
        } catch (failure: DictateFailure) {
            assertEquals("DISCONNECTED", failure.code)
        }
    }

    @Test fun parentDeadlineRemainsCancellation() = runTest {
        try {
            withTimeout(5) { bridgeRequestStep(100) { delay(20); "late" } }
            fail("Parent deadline must cancel the caller")
        } catch (expected: CancellationException) {
            assertTrue(expected is kotlinx.coroutines.TimeoutCancellationException)
        }
    }

    @Test fun providerFailureAndSuccessfulReplyKeepTheirMeaning() = runTest {
        assertEquals("ready", bridgeRequestStep(10) { "ready" })
        val expected = DictateFailure("API_KEY_REQUIRED")
        try {
            bridgeRequestStep(10) { throw expected }
            fail("Provider failure must escape")
        } catch (actual: DictateFailure) {
            assertSame(expected, actual)
        }
    }
}
