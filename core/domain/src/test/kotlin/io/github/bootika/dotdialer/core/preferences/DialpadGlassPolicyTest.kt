package io.github.bootika.dotdialer.core.preferences

import org.junit.Assert.*
import org.junit.Test

class DialpadGlassPolicyTest {
    @Test fun `default bounded and malformed backup values`() {
        assertEquals(30, DotDialerPreferenceDefaults.intFor("dialpad_glass_strength_percent"))
        for (invalid in listOf(null, true, "100", 100f, Long.MAX_VALUE)) assertEquals(30, DialpadGlassPolicy.normalizeStrength(invalid))
        assertEquals(0, DialpadGlassPolicy.normalizeStrength(-1))
        assertEquals(100, DialpadGlassPolicy.normalizeStrength(101))
        assertEquals(50, DialpadGlassPolicy.normalizeStrength(47))
        assertEquals(40, DialpadGlassPolicy.normalizeStrength(44))
        assertEquals(50, DialpadGlassPolicy.normalizeStrength(45))
        assertTrue(PreferenceBackupPolicy.isPortable("dialpad_glass_strength_percent"))
    }
    @Test fun `off unsupported or absent backdrop remains normal but zero keeps glass`() {
        for (glass in listOf(false, true)) {
            for (percent in listOf(0, 30, 100)) {
                assertEquals(DialpadGlassPolicy.Mode.NORMAL, DialpadGlassPolicy.effect(glass, percent, false, true).mode)
                assertEquals(DialpadGlassPolicy.Mode.NORMAL, DialpadGlassPolicy.effect(glass, percent, true, false).mode)
            }
        }
        assertEquals(DialpadGlassPolicy.Mode.GLASS, DialpadGlassPolicy.effect(true, 0, true, true).mode)
        assertEquals(DialpadGlassPolicy.Mode.NORMAL, DialpadGlassPolicy.effect(false, 100, true, true).mode)
    }
    @Test fun `only alpha changes across all eleven positions and full optical preset is invariant`() {
        val full = DialpadGlassPolicy.effect(true, 100, true, true)
        var previousAlpha = 1f
        for (percent in 0..100 step 10) {
            val glass = DialpadGlassPolicy.effect(true, percent, true, true)
            assertEquals(DialpadGlassPolicy.Mode.GLASS, glass.mode)
            assertEquals(full.optics, glass.optics)
            assertEquals(1.4f, glass.optics.saturation, 0f)
            assertEquals(1f, glass.optics.blurScale, 0f)
            assertEquals(18f, glass.optics.refractionHeightDp, 0f)
            assertEquals(52f, glass.optics.refractionAmountDp, 0f)
            assertEquals(1f, glass.optics.highlightAlpha, 0f)
            assertEquals(1f, glass.optics.shadowAlpha, 0f)
            assertTrue(glass.fillAlpha <= previousAlpha)
            previousAlpha = glass.fillAlpha
        }
        assertEquals(1f, DialpadGlassPolicy.effect(true, 0, true, true).fillAlpha, 0f)
        assertEquals(0.35f, full.fillAlpha, 0f)
        assertEquals(0.805f, DialpadGlassPolicy.effect(true, 30, true, true).fillAlpha, 0.000001f)
    }
}
