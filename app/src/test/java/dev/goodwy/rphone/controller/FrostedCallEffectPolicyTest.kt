package dev.goodwy.rphone.controller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrostedCallEffectPolicyTest {
    @Test
    fun enabledEffectUsesOpaqueFallbackWhenSystemBlurIsUnavailable() {
        val effect = resolveFrostedCallEffect(
            requested = true,
            crossWindowBlurAvailable = false,
        )

        assertFalse(effect.translucentBackground)
        assertFalse(effect.backgroundBlur)
    }

    @Test
    fun enabledEffectUsesBlurWhenAndroidAllowsIt() {
        val effect = resolveFrostedCallEffect(
            requested = true,
            crossWindowBlurAvailable = true,
        )

        assertTrue(effect.translucentBackground)
        assertTrue(effect.backgroundBlur)
    }

    @Test
    fun disabledEffectIsFullyOpaqueAndDoesNotBlur() {
        val effect = resolveFrostedCallEffect(
            requested = false,
            crossWindowBlurAvailable = true,
        )

        assertFalse(effect.translucentBackground)
        assertFalse(effect.backgroundBlur)
    }
}
