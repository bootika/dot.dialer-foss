package dev.goodwy.rphone.view.screen

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class CallConferenceMistTest {
    @Test fun `every circle colour participates without averaging or inventing another hue`() {
        val colors = listOf(Color.Red, Color.Green, Color.Magenta, Color.Blue)
        val zones = conferenceMistZones(colors.map { listOf(it) }, dark = false)
        assertEquals(colors, zones.map { it.tint })
        assertEquals(listOf(.20f, .80f, .20f, .80f), zones.map { it.x })
        assertEquals(4, zones.size)
    }

    @Test fun `all extracted photo colours participate alongside circles`() {
        val palettes = listOf(listOf(Color.Red, Color.Yellow, Color.Blue), listOf(Color.Green))
        val zones = conferenceMistZones(palettes, dark = true)
        assertEquals(palettes.flatten(), zones.map { it.tint })
        assertEquals(.58f, zones.take(3).sumOf { it.alpha.toDouble() }.toFloat(), .0001f)
        assertEquals(.58f, zones.last().alpha, .0001f)
        assertTrue(zones.take(3).map { it.x }.distinct().size == 3)
    }

    @Test fun `offscreen participants remain included and strength is bounded for large groups`() {
        for (count in listOf(1, 2, 3, 4, 6, 12, 24)) {
            val colors = List(count) { Color(it / count.toFloat(), .4f, .6f) }
            for (dark in listOf(false, true)) {
                val zones = conferenceMistZones(colors.map { listOf(it) }, dark)
                assertEquals(colors, zones.map { it.tint })
                assertTrue(zones.all { it.alpha > 0f && it.alpha <= .58f && it.y in .30f.. .52f })
                assertTrue(zones.sumOf { it.alpha.toDouble() } <= 4 * .58 + .0001)
            }
        }
    }

    @Test fun `removal and empty conferences cannot retain stale participant colours`() {
        val palettes = listOf(listOf(Color.Red), listOf(Color.Blue), listOf(Color.Green))
        assertEquals(listOf(Color.Red, Color.Blue, Color.Green), conferenceMistZones(palettes, false).map { it.tint })
        assertEquals(listOf(Color.Blue, Color.Green), conferenceMistZones(palettes.drop(1), false).map { it.tint })
        assertTrue(conferenceMistZones(emptyList(), false).isEmpty())
    }
}
