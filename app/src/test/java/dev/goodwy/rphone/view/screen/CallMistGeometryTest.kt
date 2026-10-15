package dev.goodwy.rphone.view.screen

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class CallMistGeometryTest {
    private val zone = CallMistZone(Color.Blue, .16f, .46f, .64f, .34f, .58f)

    @Test fun `field follows measured middle zone rather than full screen percentages`() {
        val normal = callMistZoneInMiddle(zone, AmbientDropletMiddleZone(179f, 487f), 0f, 800f)
        val largeFont = callMistZoneInMiddle(zone, AmbientDropletMiddleZone(237f, 473f), 0f, 800f)
        assertEquals(333f / 800f, normal.y, .0001f)
        assertEquals(355f / 800f, largeFont.y, .0001f)
        assertTrue(largeFont.height < normal.height)
        assertEquals(zone.x, largeFont.x, 0f)
        assertEquals(zone.alpha, largeFont.alpha, 0f)
        assertEquals(zone.width, largeFont.width, 0f)
    }

    @Test fun `insets are subtracted once and incomplete measurements retain the safe fallback`() {
        assertEquals(
            callMistZoneInMiddle(zone, AmbientDropletMiddleZone(179f, 487f), 0f, 800f),
            callMistZoneInMiddle(zone, AmbientDropletMiddleZone(207f, 515f), 28f, 800f),
        )
        for (middle in listOf(null, AmbientDropletMiddleZone(Float.NaN, 20f), AmbientDropletMiddleZone(30f, 20f))) {
            assertEquals(zone, callMistZoneInMiddle(zone, middle, 0f, 800f))
        }
        assertEquals(zone, callMistZoneInMiddle(zone, AmbientDropletMiddleZone(30f, 80f), 0f, 0f))
    }
}
