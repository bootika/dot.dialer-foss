package dev.goodwy.rphone.controller.util

import dev.goodwy.rphone.view.screen.ambientDropBrightnessFactor
import dev.goodwy.rphone.view.screen.ambientDropVisualSpec
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class AmbientDropletCalibrationTest {
    @Test fun `legacy units round trip without migration and current effect is fifty percent`() {
        assertEquals(50f, AmbientDropletPreference.opacityPercent(100))
        assertEquals(100, AmbientDropletPreference.storedBrightness(50f))
        assertEquals(200, AmbientDropletPreference.storedBrightness(100f))
        assertEquals(5f, AmbientDropletPreference.opacityPercent(10))
        for (stored in 10..200) {
            assertEquals(stored, AmbientDropletPreference.storedBrightness(AmbientDropletPreference.opacityPercent(stored)))
        }
        for (avatar in listOf(false, true)) for (dark in listOf(false, true)) {
            val baseline = ambientDropVisualSpec(avatar, dark, Color.Black)
            val fifty = ambientDropVisualSpec(avatar, dark, Color.Black, AmbientDropletPreference.storedBrightness(50f))
            val full = ambientDropVisualSpec(avatar, dark, Color.Black, AmbientDropletPreference.storedBrightness(100f))
            assertEquals(baseline, fifty)
            assertEquals(baseline.maximumDropAlpha * 2f, full.maximumDropAlpha)
            assertTrue(full.maximumDropAlpha <= 1f)
            assertEquals(baseline.blendMode, full.blendMode)
            assertEquals(baseline.dropColor, full.dropColor)
        }
    }

    @Test fun `out of range and non finite inputs use the approved default with legacy bounds`() {
        assertEquals(5f, AmbientDropletPreference.opacityPercent(Int.MIN_VALUE))
        assertEquals(100f, AmbientDropletPreference.opacityPercent(Int.MAX_VALUE))
        assertEquals(10, AmbientDropletPreference.storedBrightness(-5f))
        assertEquals(200, AmbientDropletPreference.storedBrightness(150f))
        assertEquals(140, AmbientDropletPreference.storedBrightness(Float.NaN))
        assertEquals(140, AmbientDropletPreference.storedBrightness(Float.POSITIVE_INFINITY))
        assertEquals(1.4f, ambientDropBrightnessFactor(AmbientDropletPreference.DEFAULT_BRIGHTNESS))
    }
}
