package dev.goodwy.rphone.view.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungRadialAnswerButtonsTest {

    @Test
    fun handleCenterCanReachTheTrackCircumference() {
        assertEquals(144, SAMSUNG_RADIAL_CONTAINER_DIAMETER_DP)
        assertEquals(144, SAMSUNG_RADIAL_TRACK_DIAMETER_DP)
        assertEquals(72, SAMSUNG_RADIAL_HANDLE_DIAMETER_DP)
        assertEquals(72f, SAMSUNG_RADIAL_MAX_DRAG_DP)
        assertEquals(
            SAMSUNG_RADIAL_TRACK_DIAMETER_DP / 2f,
            SAMSUNG_RADIAL_MAX_DRAG_DP,
        )
        assertEquals(59.04f, samsungRadialTriggerDistanceDp(), 0.001f)
        assertTrue(SAMSUNG_RADIAL_TRACK_DIAMETER_DP > 108)
        assertTrue(samsungRadialTriggerDistanceDp() > 28.08f)
    }

    @Test
    fun trackIsSubtleAtRestAndClearerWhileDragging() {
        assertTrue(SAMSUNG_RADIAL_IDLE_BORDER_ALPHA < SAMSUNG_RADIAL_DRAG_BORDER_ALPHA)
        assertTrue(SAMSUNG_RADIAL_IDLE_FILL_ALPHA < SAMSUNG_RADIAL_DRAG_FILL_ALPHA)
        assertTrue(SAMSUNG_RADIAL_IDLE_BORDER_ALPHA <= 0.10f)
        assertTrue(SAMSUNG_RADIAL_IDLE_FILL_ALPHA <= 0.015f)
        assertTrue(SAMSUNG_RADIAL_DRAG_BORDER_ALPHA <= 0.30f)
        assertTrue(SAMSUNG_RADIAL_DRAG_FILL_ALPHA <= 0.065f)
    }

    @Test
    fun actionsUseMoreOfTheAvailableRowWidth() {
        assertEquals(0, SAMSUNG_RADIAL_ROW_HORIZONTAL_PADDING_DP)
        assertEquals(SAMSUNG_RADIAL_TRACK_DIAMETER_DP, SAMSUNG_RADIAL_CONTAINER_DIAMETER_DP)
        assertTrue(SAMSUNG_RADIAL_ROW_HORIZONTAL_PADDING_DP < 34)
    }

    @Test
    fun controlsScaleDownOnlyWhenTheAvailableWidthRequiresIt() {
        assertEquals(136f, samsungRadialControlDiameterDp(272f), 0.001f)
        assertEquals(144f, samsungRadialControlDiameterDp(320f), 0.001f)
        assertEquals(144f, samsungRadialControlDiameterDp(480f), 0.001f)
    }
}
