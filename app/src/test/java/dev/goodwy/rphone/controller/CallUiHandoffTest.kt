package dev.goodwy.rphone.controller

import io.github.bootika.dotdialer.core.call.CallPresentationPolicy
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CallUiHandoffTest {
    @Test fun foregroundAndServiceClaimOnlyOneLaunchInEitherOrder() = runTest {
        val handoff = CallUiHandoffController(backgroundScope)
        val token = handoff.beginOutgoing()
        assertEquals(token, handoff.claimLaunch())
        assertNull(handoff.claimLaunch())
        handoff.contentReady(token)
        assertNull(handoff.state.value)
        // A delayed service callback must not reopen the screen after the user pressed Back.
        assertNull(handoff.claimLaunch())
        handoff.reset()
        assertNotNull(handoff.claimLaunch())
        handoff.reset()
    }

    @Test fun timeoutsAndLaunchFailureRestoreBannerAndPermitRetry() = runTest {
        val handoff = CallUiHandoffController(backgroundScope)
        val token = handoff.beginOutgoing()
        handoff.claimLaunch()
        runCurrent()
        advanceTimeBy(CallPresentationPolicy.OUTGOING_SESSION_GRACE_MILLIS + 1)
        runCurrent()
        assertNull(handoff.state.value)
        assertNotNull(handoff.claimLaunch())
        handoff.reset()
        val failed = handoff.beginOutgoing()
        assertTrue(failed > token)
        handoff.cancel(failed)
        assertNull(handoff.state.value)
        assertNotNull(handoff.claimLaunch())
        handoff.reset()
    }

    @Test fun staleReadyOrFailureCannotReleaseNewerTransfer() = runTest {
        val handoff = CallUiHandoffController(backgroundScope)
        val old = handoff.beginOutgoing()
        handoff.claimLaunch()
        val current = handoff.beginOutgoing()
        handoff.claimLaunch()
        handoff.contentReady(old)
        handoff.cancel(old)
        assertEquals(current, handoff.state.value?.token)
        assertTrue(handoff.state.value?.launchRequested == true)
        handoff.contentReady(current)
        assertNull(handoff.state.value)
        handoff.reset()
    }

    @Test fun bannerIsSuppressedOnlyDuringHandoffOrVisibleCallSurface() {
        assertTrue(showHomeCallBanner(true, false, false))
        assertFalse(showHomeCallBanner(true, true, false))
        assertFalse(showHomeCallBanner(true, false, true))
        assertFalse(showHomeCallBanner(false, false, false))
    }
}
