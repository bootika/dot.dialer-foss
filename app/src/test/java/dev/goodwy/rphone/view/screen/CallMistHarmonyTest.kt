package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.goodwy.rphone.view.components.modernPastelAvatarColors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CallMistHarmonyTest {
    @Test fun `grey dominant mixed photos and almost black colours never lose their chromatic anchor`() {
        for (accent in listOf(Color.BLUE, Color.RED, Color.rgb(229, 115, 115))) {
            val source = CallAvatarPalette(Color.GRAY, accent, Color.GRAY)
            val result = harmonizedCallMistPalette(source)
            assertEquals(Color.GRAY, result.upper)
            assertTrue(result.colors.any { hsv(it)[1] >= .10f })
            assertEquals(result, harmonizedCallMistPalette(source))
        }
        for (r in 0..8) for (g in 0..8) for (b in 0..8) {
            val base = Color.rgb(r, g, b)
            val palette = harmonizedCallMistPalette(analogousCallAvatarPalette(base))
            if (!palette.usesNeutralPalette) assertEquals(base, palette.upper)
        }
    }

    @Test fun `pink circle produces four hues including its cool complement`() {
        val base = Color.rgb(229, 115, 115)
        val palette = harmonizedCallMistPalette(analogousCallAvatarPalette(base))
        assertEquals(base, palette.upper)
        assertEquals(4, palette.colors.distinct().size)
        assertEquals((hsv(base)[0] + 180f) % 360f, hsv(palette.left)[0], 1f)
        assertTrue(hsv(palette.left)[0] in 155f..280f)
    }

    @Test fun `all coloured circles retain their identity and have a deterministic cool counterpoint`() {
        for (hue in 0 until 360 step 5) {
            val base = Color.HSVToColor(floatArrayOf(hue.toFloat(), .55f, .85f))
            val input = analogousCallAvatarPalette(base)
            val palette = harmonizedCallMistPalette(input)
            assertEquals(base, palette.upper)
            assertEquals(palette, harmonizedCallMistPalette(input))
            assertEquals("Four distinct hues required for $hue", 4, palette.colors.distinct().size)
            assertTrue("Missing cool counterpoint for $hue", hsv(palette.left)[0] in 154f..281f)
            assertTrue(palette.colors.all { hsv(it)[1] in .275f.. .56f && hsv(it)[2] in .84f.. .86f })
        }
    }

    @Test fun `different identities do not receive a fixed cool accent`() {
        val red = harmonizedCallMistPalette(analogousCallAvatarPalette(Color.RED))
        val orange = harmonizedCallMistPalette(analogousCallAvatarPalette(Color.rgb(255, 128, 0)))
        assertNotEquals(red.left, orange.left)
        assertTrue(kotlin.math.abs(hsv(red.left)[0] - hsv(orange.left)[0]) > 20f)
    }

    @Test fun `photographic colours are retained when they already form a balanced palette`() {
        val source = CallAvatarPalette(Color.rgb(95, 130, 65), Color.rgb(40, 120, 145), Color.rgb(170, 115, 60))
        val result = harmonizedCallMistPalette(source)
        assertEquals(source.upper, result.upper)
        assertEquals(source.left, result.left)
        assertEquals(source.right, result.right)
        assertEquals(4, result.colors.distinct().size)
        val allCold = CallAvatarPalette(Color.rgb(70, 170, 150), Color.rgb(60, 130, 170), Color.rgb(70, 90, 170))
        val extended = harmonizedCallMistPalette(allCold)
        assertEquals(allCold.upper, extended.upper)
        assertEquals(allCold.left, extended.left)
        assertEquals(allCold.right, extended.right)
        assertEquals(4, extended.colors.distinct().size)
    }

    @Test fun `a warm only photo gets a cool colour while a cold only photo gets warm contrast`() {
        val pink = Color.rgb(230, 120, 160)
        val warm = harmonizedCallMistPalette(CallAvatarPalette(pink, pink, pink))
        assertTrue(hsv(warm.left)[0] in 155f..280f)
        val blue = Color.rgb(40, 90, 170)
        val cold = harmonizedCallMistPalette(CallAvatarPalette(blue, blue, blue))
        assertTrue(hsv(cold.lower)[0] !in 155f..280f)
        assertEquals(4, cold.colors.distinct().size)
    }

    @Test fun `neutral photographs borrow four stable vivid Thunderbird hues only for artwork`() {
        for (base in listOf(Color.GRAY, Color.LTGRAY, Color.DKGRAY)) {
            val palette = harmonizedCallMistPalette(analogousCallAvatarPalette(base))
            assertTrue(palette.usesNeutralPalette)
            assertEquals(4, palette.colors.distinct().size)
            assertTrue(palette.colors.all { it in modernPastelAvatarColors.map { pair -> pair.first.toArgb() } })
            assertEquals(Color.GRAY, analogousCallAvatarPalette(Color.GRAY).upper)
            val zones = conferenceMistZones(listOf(palette.colors.map(::androidxColor)), true, listOf(CALL_NEUTRAL_MIST_STRENGTH))
            assertEquals(.58f * CALL_NEUTRAL_MIST_STRENGTH, zones.sumOf { it.alpha.toDouble() }.toFloat(), .0001f)
        }
    }

    @Test fun `neutral colour choices are seeded stable varied and balanced for every caller colour`() {
        val palettes = modernPastelAvatarColors.map { neutralAvatarMistPalette(it.first.toArgb()) }
        assertTrue(palettes.distinct().size > 8)
        for (seed in -100..100) {
            val palette = neutralAvatarMistPalette(seed)
            assertEquals(palette, neutralAvatarMistPalette(seed))
            assertEquals(4, palette.colors.distinct().size)
            assertTrue(palette.colors.all { hsv(it)[1] >= .28f })
            assertTrue(hsv(palette.left)[0] in 155f..280f)
            assertTrue(hsv(palette.right)[0] !in 90f..280f)
        }
    }

    @Test fun `conference harmonies include every identity and retain the existing opacity budget`() {
        val bases = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA)
        val palettes = bases.map { harmonizedCallMistPalette(analogousCallAvatarPalette(it)).colors.map(::androidxColor) }
        for (dark in listOf(false, true)) {
            val zones = conferenceMistZones(palettes, dark)
            assertTrue(bases.all { androidxColor(it) in zones.map { zone -> zone.tint } })
            assertTrue(zones.sumOf { it.alpha.toDouble() } <= 4 * .58 + .0001)
            assertTrue(zones.take(4).first().alpha > zones.take(4)[1].alpha)
            assertEquals(palettes.last(), conferenceMistZones(palettes.takeLast(1), dark).map { it.tint })
        }
    }

    private fun hsv(color: Int): FloatArray = FloatArray(3).also { Color.colorToHSV(color, it) }
    private fun androidxColor(color: Int) = androidx.compose.ui.graphics.Color(color)
}
