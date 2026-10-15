package dev.goodwy.rphone.view.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModePolicyTest {

    @Test
    fun `approved jade accent is the default`() {
        assertEquals(DotAccent.Jade, resolveAccent(null))
        assertEquals(DotAccent.Jade, resolveAccent("unknown"))
        assertEquals(DotAccent.Jade, resolveAccent("jade"))
    }

    @Test
    fun `approved dark palette keeps surfaces restrained`() {
        assertEquals(Color(0xFF08100D), DotDarkBackground)
        assertEquals(Color(0xFF15201C), DotDarkCard)
        assertEquals(Color(0xFF1B2822), DotDarkHeader)
    }

    @Test
    fun `configured accent choices resolve deterministically`() {
        assertEquals(DotAccent.Blue, resolveAccent("blue"))
        assertEquals(DotAccent.Violet, resolveAccent("violet"))
        assertEquals(DotAccent.Coral, resolveAccent("coral"))
    }

    @Test
    fun `legacy violet preference renders exact electric iris`() {
        assertEquals(Color(0xFF6857E5), DotElectricIris)
        assertEquals(DotAccent.Violet, resolveAccent("violet"))
    }

    @Test
    fun `blue uses the Dark Edge Radio brand without changing keys or other accents`() {
        assertEquals(Color(0xFF4285F4), DotBlue)
        assertEquals(DotAccent.Blue, resolveAccent("blue"))
        assertEquals(Color(0xFF00BB77), DotJade)
        assertEquals(Color(0xFF6857E5), DotElectricIris)
        assertEquals(Color(0xFFE5485B), DotCoral)
        assertEquals(DotRuby, DotCoral)
        assertEquals(Color(0xFFC83E4D), DotError)
    }
    @Test
    fun `auto follows the system theme`() {
        assertTrue(resolveDarkTheme("auto", systemDark = true))
        assertFalse(resolveDarkTheme("auto", systemDark = false))
        assertTrue(resolveDarkTheme("auto_bw", systemDark = true))
        assertFalse(resolveDarkTheme("auto_bw", systemDark = false))
    }

    @Test
    fun `light always uses the light palette`() {
        assertFalse(resolveDarkTheme("light", systemDark = true))
        assertFalse(resolveDarkTheme("light", systemDark = false))
    }

    @Test
    fun `dark always uses the dark palette`() {
        assertTrue(resolveDarkTheme("dark", systemDark = false))
        assertTrue(resolveDarkTheme("dark", systemDark = true))
    }

    @Test
    fun `unknown mode safely follows the system`() {
        assertTrue(resolveDarkTheme("future-mode", systemDark = true))
        assertFalse(resolveDarkTheme("future-mode", systemDark = false))
    }
}
