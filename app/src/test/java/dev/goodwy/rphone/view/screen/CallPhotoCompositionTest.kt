package dev.goodwy.rphone.view.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallPhotoCompositionTest {

    @Test
    fun photoBlockMovesDownByThreePercentOfTheActualFreeZone() {
        assertEquals(12f, callFullPhotoVerticalOffset(320f, 720f), .0001f)
        assertEquals(6f, callFullPhotoVerticalOffset(520f, 720f), .0001f)
        assertEquals(24f, callFullPhotoVerticalOffset(640f, 1440f), .0001f)
    }

    @Test
    fun missingOrInvalidLandmarksNeverProduceAnInvalidPhotoOffset() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            assertEquals(0f, callFullPhotoVerticalOffset(invalid, 720f), 0f)
            assertEquals(0f, callFullPhotoVerticalOffset(320f, invalid), 0f)
        }
        assertEquals(0f, callFullPhotoVerticalOffset(720f, 320f), 0f)
        assertEquals(0f, callFullPhotoVerticalOffset(320f, 320f), 0f)
        assertEquals(0f, callFullPhotoVerticalOffset(-Float.MAX_VALUE, Float.MAX_VALUE), 0f)
    }

    @Test
    fun squareAndLandscapeImagesUseAFullWidthPanel() {
        assertTrue(usesFullWidthPhotoPanel(1000, 1000))
        assertTrue(usesFullWidthPhotoPanel(1600, 900))
    }

    @Test
    fun portraitImagesRemainFullBleed() {
        assertFalse(usesFullWidthPhotoPanel(900, 1600))
    }

    @Test
    fun panelTopInsetIsResponsiveAndBounded() {
        assertEquals(128, callPhotoPanelTopInsetDp(560))
        assertEquals(156, callPhotoPanelTopInsetDp(780))
        assertEquals(172, callPhotoPanelTopInsetDp(1200))
    }

    @Test
    fun panelCentreTracksTheMeasuredGapBetweenHeaderAndControls() {
        assertEquals(
            129,
            callPhotoPanelTopInsetDp(
                screenWidthDp = 360,
                screenHeightDp = 780,
                panelAspectRatio = 1f,
                clearZoneCenterDp = 309f,
            ),
        )
        assertEquals(
            152,
            callPhotoPanelTopInsetDp(
                screenWidthDp = 360,
                screenHeightDp = 780,
                panelAspectRatio = 1.15f,
                clearZoneCenterDp = 309f,
            ),
        )
    }

    @Test
    fun measuredPanelPositionFallsBackUntilLandmarksAreAvailable() {
        assertEquals(
            156,
            callPhotoPanelTopInsetDp(
                screenWidthDp = 360,
                screenHeightDp = 780,
                panelAspectRatio = 1f,
                clearZoneCenterDp = null,
            ),
        )
    }

    @Test
    fun squareKeepsItsGeometryAndLandscapeIsCroppedToAUsefulHeight() {
        assertEquals(1f, callPhotoPanelAspectRatio(1f))
        assertEquals(CALL_PHOTO_MAX_PANEL_ASPECT_RATIO, callPhotoPanelAspectRatio(16f / 9f))
    }

    @Test
    fun bottomFadeSettlesAroundTheSecondControlRow() {
        val screenFraction = 1f - CALL_PHOTO_BOTTOM_SCRIM_FRACTION +
            CALL_PHOTO_BOTTOM_SCRIM_FRACTION * CALL_PHOTO_BOTTOM_FADE_SETTLED_STOP

        assertEquals(0.7272f, screenFraction, 0.0001f)
    }

    @Test
    fun photoEdgeFeatherIsVerticallySymmetric() {
        val upperSharpStop = CALL_PHOTO_EDGE_FEATHER_FRACTION
        val lowerSharpStop = 1f - CALL_PHOTO_EDGE_FEATHER_FRACTION

        assertEquals(upperSharpStop, 1f - lowerSharpStop, 0.0001f)
        assertEquals(0.38f, upperSharpStop, 0.0001f)
    }
}
