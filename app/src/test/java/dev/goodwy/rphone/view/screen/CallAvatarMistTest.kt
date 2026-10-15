package dev.goodwy.rphone.view.screen

import android.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import dev.goodwy.rphone.view.components.RillAvatarAppearance
import dev.goodwy.rphone.view.components.modernPastelAvatarColor
import dev.goodwy.rphone.view.components.rillAvatarColors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class CallAvatarMistTest {
    @Test fun `photographed hue families remain stable across order and small resampling changes`() {
        val teal = Color.rgb(40, 140, 160)
        val blue = Color.rgb(45, 130, 175)
        val gold = Color.rgb(180, 140, 70)
        val pixels = IntArray(1000) { when { it < 350 -> teal; it < 700 -> blue; else -> gold } }
        val palette = extractCallAvatarPalette(pixels)!!
        assertEquals(palette, extractCallAvatarPalette(pixels.reversedArray()))
        val altered = pixels.clone().also { it[350] = teal }
        assertEquals(hsv(palette.left)[0], hsv(extractCallAvatarPalette(altered)!!.left)[0], .5f)
        assertTrue(hsv(palette.left)[0] in hsv(teal)[0]..hsv(blue)[0])
        val noisy = IntArray(1000) { if (it == 0) Color.BLUE else Color.GRAY }
        assertEquals(CallAvatarPalette(Color.GRAY, Color.GRAY, Color.GRAY), extractCallAvatarPalette(noisy))
    }

    @Test fun `photo colours are extracted rather than borrowed from the approved example`() {
        val green = Color.rgb(95, 130, 65)
        val blue = Color.rgb(40, 120, 145)
        val gold = Color.rgb(170, 115, 60)
        val pixels = IntArray(100) { when { it < 40 -> green; it < 70 -> blue; else -> gold } }
        assertEquals(CallAvatarPalette(green, blue, gold), extractCallAvatarPalette(pixels))
        val purple = Color.rgb(150, 75, 180)
        assertEquals(CallAvatarPalette(purple, purple, purple), extractCallAvatarPalette(IntArray(50) { purple }))
    }

    @Test fun `black and overexposed pixels do not hide the coloured background`() {
        val teal = Color.rgb(45, 135, 150)
        val pixels = IntArray(100) { when { it < 40 -> Color.BLACK; it < 80 -> Color.WHITE; else -> teal } }
        assertEquals(CallAvatarPalette(teal, teal, teal), extractCallAvatarPalette(pixels))
    }

    @Test fun `transparent empty or unusable photos fall back to the identity colour`() {
        assertNull(extractCallAvatarPalette(intArrayOf()))
        assertNull(extractCallAvatarPalette(intArrayOf(Color.TRANSPARENT)))
        val grey = Color.rgb(128, 128, 128)
        assertEquals(CallAvatarPalette(grey, grey, grey), extractCallAvatarPalette(intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.WHITE)))
    }

    @Test fun `fallback retains the actual circle colour and uses adjacent hues`() {
        val base = Color.rgb(210, 90, 100)
        val result = analogousCallAvatarPalette(base)
        assertEquals(base, result.upper)
        val original = hsv(base)
        assertEquals((original[0] - 28f + 360f) % 360f, hsv(result.left)[0], .5f)
        assertEquals((original[0] + 28f) % 360f, hsv(result.right)[0], .5f)
        assertEquals(result, analogousCallAvatarPalette(base))
    }

    @Test fun `neutral fallback stays neutral instead of inventing a saturated hue`() {
        val result = analogousCallAvatarPalette(Color.GRAY)
        assertEquals(Color.GRAY, result.left)
        assertEquals(Color.GRAY, result.right)
    }

    @Test fun `both themes preserve photo hue and light tint compensates for alpha blending`() {
        val color = Color.rgb(30, 120, 155)
        for (dark in listOf(false, true)) {
            val actual = hsv(callMistColor(color, dark).toArgb())
            assertEquals(hsv(color)[0], actual[0], 2f)
            assertTrue(actual[1] <= if (dark) .405f else .605f)
            assertTrue(actual[2] in if (dark) .595f.. .665f else .945f.. .985f)
        }
    }

    @Test fun `light palette keeps quiet colours visible without tinting neutral photos`() {
        val quietTeal = Color.rgb(196, 231, 234)
        val result = hsv(callMistColor(quietTeal, false).toArgb())
        assertEquals(hsv(quietTeal)[0], result[0], 2f)
        assertEquals(.40f, result[1], .01f)
        assertEquals(0f, hsv(callMistColor(Color.GRAY, false).toArgb())[1], 0f)
        assertEquals(0f, hsv(callMistColor(Color.WHITE, false).toArgb())[1], 0f)
    }

    @Test fun `warm field prefers photographed amber and does not increase warm saturation`() {
        val hair = Color.rgb(130, 65, 50)
        val amber = Color.rgb(200, 150, 80)
        val palette = extractCallAvatarPalette(IntArray(100) { if (it < 80) hair else amber })!!
        assertEquals(amber, palette.right)
        assertTrue(hsv(callMistColor(amber, false).toArgb())[1] <= .325f)
        assertEquals(hair, extractCallAvatarPalette(IntArray(100) { hair })!!.right)
    }

    @Test fun `background and circle use the same resolver for every avatar scheme`() {
        val base = RillAvatarAppearance(true, true, false, true, false, false, false)
        for (scheme in listOf(lightColorScheme(), darkColorScheme())) {
            assertEquals(modernPastelAvatarColor("Mara Ionescu"), rillAvatarColors("Mara Ionescu", base, scheme))
            assertEquals(modernPastelAvatarColor("unknown_caller"), rillAvatarColors("", base, scheme))
            assertEquals(modernPastelAvatarColor("Mara"), rillAvatarColors("Mara", base.copy(modernPastelAvatars = false), scheme))
            assertEquals(modernPastelAvatarColor("Mara"), rillAvatarColors("Mara", base.copy(modernPastelAvatars = false, secondaryColorAvatars = true), scheme))
        }
    }

    private fun hsv(color: Int): FloatArray = FloatArray(3).also { Color.colorToHSV(color, it) }
}
