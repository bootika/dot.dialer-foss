package io.github.bootika.dotdialer.core.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPolicyTest {
    @Test
    fun `unknown stored strength safely falls back to light`() {
        assertEquals(HapticStrength.LIGHT, HapticStrength.fromStorage("unknown"))
    }

    @Test
    fun `custom intensity is clamped`() {
        val belowRange = HapticPolicy.pulse(HapticIntent.TAP, HapticStrength.CUSTOM, -4f)
        val zero = HapticPolicy.pulse(HapticIntent.TAP, HapticStrength.CUSTOM, 0f)
        val aboveRange = HapticPolicy.pulse(HapticIntent.TAP, HapticStrength.CUSTOM, 8f)
        val one = HapticPolicy.pulse(HapticIntent.TAP, HapticStrength.CUSTOM, 1f)

        assertEquals(zero, belowRange)
        assertEquals(one, aboveRange)
    }

    @Test
    fun `frequent keyboard and scroll feedback stays short and subtle`() {
        val keyboard = HapticPolicy.pulse(HapticIntent.KEYBOARD, HapticStrength.STRONG, 1f)
        val scroll = HapticPolicy.pulse(HapticIntent.SCROLL_TICK, HapticStrength.STRONG, 1f)

        assertTrue(keyboard.durationMs <= 12L)
        assertTrue(keyboard.amplitude <= 110)
        assertEquals(keyboard, scroll)
    }

    @Test
    fun `reject is more pronounced than tap without becoming a long buzz`() {
        val tap = HapticPolicy.pulse(HapticIntent.TAP, HapticStrength.LIGHT, 0.5f)
        val reject = HapticPolicy.pulse(HapticIntent.REJECT, HapticStrength.LIGHT, 0.5f)

        assertTrue(reject.amplitude > tap.amplitude)
        assertTrue(reject.durationMs > tap.durationMs)
        assertTrue(reject.durationMs <= 60L)
    }
}
