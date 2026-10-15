package dev.goodwy.rphone.view.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.goodwy.rphone.view.theme.CallActionColors
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.color_call_end
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallInteractionTest {
    @Test fun allThresholdsShareTheSameOpaqueIdleAndCommittedActionPalette() {
        assertEquals(0xFF00BB77.toInt(), color_call_button.toArgb())
        assertEquals(0xFFC83E4D.toInt(), color_call_end.toArgb())
        for ((action, idle) in listOf(color_call_button to CallActionColors.answerIdle, color_call_end to CallActionColors.rejectIdle)) {
            for (threshold in listOf(.7f, .8f, .85f, SAMSUNG_RADIAL_TRIGGER_FRACTION)) {
                assertEquals(idle, incomingActionSurfaceColor(action, 0f, threshold))
                assertEquals(action, incomingActionSurfaceColor(action, threshold, threshold))
                assertEquals(action, incomingActionSurfaceColor(action, -threshold, threshold))
                assertEquals(incomingActionSurfaceColor(action, threshold * .5f, threshold), incomingActionSurfaceColor(action, .5f, 1f))
                assertEquals(1f, idle.alpha, 0f)
            }
        }
    }


    @Test
    fun sliderHandleKeepsEqualInsetsAtBothEnds() {
        val inset = 6f
        val handle = 96f
        val track = 320f
        val travel = incomingSliderTravelPx(track, handle, inset)

        val left = incomingSliderOffsetPx(0f, true, travel, inset)
        val right = incomingSliderOffsetPx(1f, true, travel, inset)

        assertEquals(inset, left, 0.001f)
        assertEquals(track - inset, right + handle, 0.001f)
        assertEquals(right, incomingSliderOffsetPx(0f, false, travel, inset), 0.001f)
    }

    @Test
    fun defaultSliderUsesShorterTravelAndCommitsNearTheEnd() {
        val travel = incomingSliderTravelPx(
            trackWidthPx = DEFAULT_INCOMING_SWIPE_TRACK_WIDTH_DP,
            handleWidthPx = 96f,
            horizontalInsetPx = 6f,
        )
        val triggerDistance = travel * DEFAULT_INCOMING_SWIPE_TRIGGER_FRACTION

        assertEquals(180f, travel, 0.001f)
        assertEquals(144f, triggerDistance, 0.001f)
        assertTrue(
            incomingActionCommitReached(
                DEFAULT_INCOMING_SWIPE_TRIGGER_FRACTION,
                DEFAULT_INCOMING_SWIPE_TRIGGER_FRACTION,
            ),
        )
    }

    @Test
    fun sliderGeometryAdaptsToNarrowAndWideTracks() {
        val inset = 6f
        val handle = 96f

        listOf(240f, 272f, 320f, 480f).forEach { track ->
            val travel = incomingSliderTravelPx(track, handle, inset)
            assertEquals(track - inset, inset + travel + handle, 0.001f)

            val centeredTravel = incomingCenteredSliderTravelPx(track, handle, inset)
            assertEquals(
                track - inset,
                track / 2f + centeredTravel + handle / 2f,
                0.001f,
            )
        }
    }

    @Test
    fun centeredAnswerDeclineHandleKeepsItsVisualInsetOnEveryTrackWidth() {
        val inset = 12f
        val handle = 110f

        listOf(272f, 320f, 360f, 412f).forEach { track ->
            val travel = incomingCenteredSliderTravelPx(track, handle, inset)

            assertTrue(travel > 0f)
            assertEquals(inset, track / 2f - travel - handle / 2f, 0.001f)
            assertEquals(track - inset, track / 2f + travel + handle / 2f, 0.001f)
        }
    }

    @Test
    fun feedbackIsFullyCommittedAtAndBeyondTrigger() {
        assertEquals(0f, incomingActionFeedbackProgress(0f), 0.001f)
        assertTrue(incomingActionFeedbackProgress(0.4f) in 0f..1f)
        assertEquals(1f, incomingActionFeedbackProgress(INCOMING_ACTION_TRIGGER_FRACTION), 0.001f)
        assertEquals(1f, incomingActionFeedbackProgress(2f), 0.001f)
        assertEquals(1f, incomingActionFeedbackProgress(-2f), 0.001f)
        assertEquals(1f, incomingActionAlpha(INCOMING_ACTION_TRIGGER_FRACTION), 0.001f)
    }

    @Test
    fun releaseCommitsAtTheExactVisualAndHapticThresholdInEitherDirection() {
        assertTrue(incomingActionCommitReached(INCOMING_ACTION_TRIGGER_FRACTION))
        assertTrue(incomingActionCommitReached(-INCOMING_ACTION_TRIGGER_FRACTION))
        assertTrue(incomingActionCommitReached(INCOMING_ACTION_TRIGGER_FRACTION + 0.01f))
        assertTrue(!incomingActionCommitReached(INCOMING_ACTION_TRIGGER_FRACTION - 0.01f))
        assertTrue(!incomingActionCommitReached(0f, 0f))
    }

    @Test
    fun feedbackColorReachesExactActionColorAtTrigger() {
        val idle = Color.White
        val action = Color.Green

        assertEquals(idle, incomingActionColor(idle, action, 0f))
        assertEquals(action, incomingActionColor(idle, action, INCOMING_ACTION_TRIGGER_FRACTION))
        assertEquals(1f, incomingActionSurfaceColor(action, 0f).alpha, 0.001f)
        assertEquals(action, incomingActionSurfaceColor(action, INCOMING_ACTION_TRIGGER_FRACTION))
    }

    @Test
    fun resizePreservesProgressAndBothRestingEnds() {
        assertEquals(0f, incomingSliderOffsetAfterResize(0f, 212f, 180f, true), 0.001f)
        assertEquals(180f, incomingSliderOffsetAfterResize(212f, 212f, 180f, false), 0.001f)
        assertEquals(90f, incomingSliderOffsetAfterResize(106f, 212f, 180f, true), 0.001f)
        assertEquals(90f, incomingSliderOffsetAfterResize(106f, 212f, 180f, false), 0.001f)
    }
}
