package io.github.bootika.dotdialer.core.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallPresentationPolicyTest {
    @Test
    fun `full screen is the default for incoming calls`() {
        assertTrue(
            CallPresentationPolicy.shouldLaunchCallActivity(isIncomingRinging = true)
        )
    }

    @Test
    fun `incoming calls respect an explicit compact preference`() {
        assertFalse(
            CallPresentationPolicy.shouldLaunchCallActivity(
                isIncomingRinging = true,
                alwaysFullScreen = false,
            )
        )
    }

    @Test
    fun `outgoing calls always open the call activity`() {
        assertTrue(
            CallPresentationPolicy.shouldLaunchCallActivity(
                isIncomingRinging = false,
                alwaysFullScreen = false,
            )
        )
    }

    @Test
    fun `visible full screen call suppresses the duplicate incoming alert`() {
        assertFalse(
            CallPresentationPolicy.shouldAlertIncomingNotification(
                isIncomingRinging = true,
                suppressIncomingAlert = true,
            )
        )
        assertFalse(
            CallPresentationPolicy.shouldAttachFullScreenIntent(
                isIncomingRinging = true,
                alwaysFullScreen = true,
                suppressIncomingAlert = true,
            )
        )
        assertFalse(
            CallPresentationPolicy.shouldUseHighPriorityCallChannel(
                isIncomingRinging = true,
                suppressIncomingAlert = true,
                requestedHighPriority = true,
            )
        )
    }

    @Test
    fun `incoming notification keeps its alert when no call activity is visible`() {
        assertTrue(
            CallPresentationPolicy.shouldAlertIncomingNotification(
                isIncomingRinging = true,
                suppressIncomingAlert = false,
            )
        )
        assertTrue(
            CallPresentationPolicy.shouldAttachFullScreenIntent(
                isIncomingRinging = true,
                alwaysFullScreen = true,
                suppressIncomingAlert = false,
            )
        )
    }

    @Test
    fun `ongoing call can still use the requested high priority channel`() {
        assertTrue(
            CallPresentationPolicy.shouldUseHighPriorityCallChannel(
                isIncomingRinging = false,
                suppressIncomingAlert = false,
                requestedHighPriority = true,
            )
        )
    }

    @Test
    fun `only managed calls and pending launch own the screen`() {
        assertTrue(CallPresentationPolicy.shouldFinishBeforeRendering(false, false))
        assertFalse(CallPresentationPolicy.shouldFinishBeforeRendering(true, false))
        assertFalse(CallPresentationPolicy.shouldFinishBeforeRendering(false, true))
        assertFalse(CallPresentationPolicy.shouldFinishBeforeRendering(true, true))
    }

    @Test
    fun `foreground outgoing launch survives the telecom registration race`() {
        assertFalse(
            CallPresentationPolicy.shouldFinishBeforeRendering(
                repositoryHasActiveCall = false,
                pendingOutgoingLaunch = true,
            )
        )
        assertTrue(
            CallPresentationPolicy.shouldFinishBeforeRendering(
                repositoryHasActiveCall = false,
                pendingOutgoingLaunch = false,
            )
        )
        assertEquals(
            3_000L,
            CallPresentationPolicy.missingSessionDismissDelayMillis(awaitingOutgoingSession = true),
        )
        assertEquals(
            400L,
            CallPresentationPolicy.missingSessionDismissDelayMillis(awaitingOutgoingSession = false),
        )
    }
}
