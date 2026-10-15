package dev.goodwy.rphone.view.screen

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAmbientDropletsTest {

    @Test
    fun firstDropKeepsTheOriginalRealDialerCadence() {
        assertEquals(
            AmbientDropAnimationTiming(
                horizontalDurationMillis = 10_000,
                verticalDurationMillis = 12_000,
                alphaDurationMillis = 5_000,
            ),
            ambientDropAnimationTiming(0),
        )
        assertEquals(100f, CALL_AMBIENT_HORIZONTAL_TRAVEL_DP)
        assertEquals(-150f, CALL_AMBIENT_VERTICAL_TRAVEL_DP)
        assertEquals(10, ambientDropDiameterDp(0))
        assertEquals(19, ambientDropDiameterDp(9))
    }

    @Test
    fun everyDropGetsIndependentAxisAndOpacityDurations() {
        val first = ambientDropAnimationTiming(0)
        val tenth = ambientDropAnimationTiming(9)

        assertNotEquals(first.horizontalDurationMillis, tenth.horizontalDurationMillis)
        assertNotEquals(first.verticalDurationMillis, tenth.verticalDurationMillis)
        assertNotEquals(first.alphaDurationMillis, tenth.alphaDurationMillis)
        assertEquals(19_000, tenth.horizontalDurationMillis)
        assertEquals(22_800, tenth.verticalDurationMillis)
        assertEquals(9_500, tenth.alphaDurationMillis)
        assertEquals(first, ambientDropAnimationTiming(10))
        assertEquals(tenth, ambientDropAnimationTiming(19))
    }

    @Test
    fun dropletsUseAvatarAndThemeAwareColorAlphaAndBlendMode() {
        val lightOnSurface = Color(0xFF10231C)
        val withAvatar = ambientDropVisualSpec(
            hasAvatar = true,
            isDarkTheme = false,
            onSurfaceColor = lightOnSurface,
        )
        val withoutAvatarDark = ambientDropVisualSpec(
            hasAvatar = false,
            isDarkTheme = true,
            onSurfaceColor = Color.Black,
        )
        val withoutAvatarLight = ambientDropVisualSpec(
            hasAvatar = false,
            isDarkTheme = false,
            onSurfaceColor = lightOnSurface,
        )

        assertEquals(0.40f, withAvatar.minimumDropAlpha)
        assertEquals(0.40f, withAvatar.maximumDropAlpha)
        assertEquals(BlendMode.Overlay, withAvatar.blendMode)
        assertEquals(Color.White, withAvatar.dropColor)
        assertEquals(0.18f, withoutAvatarDark.minimumDropAlpha)
        assertEquals(0.18f, withoutAvatarDark.maximumDropAlpha)
        assertEquals(BlendMode.SrcOver, withoutAvatarDark.blendMode)
        assertEquals(Color.White, withoutAvatarDark.dropColor)
        assertEquals(0.11f, withoutAvatarLight.minimumDropAlpha)
        assertEquals(0.11f, withoutAvatarLight.maximumDropAlpha)
        assertEquals(BlendMode.SrcOver, withoutAvatarLight.blendMode)
        assertEquals(lightOnSurface, withoutAvatarLight.dropColor)
        assertEquals(CALL_AMBIENT_ALPHA_WITH_AVATAR, withAvatar.minimumDropAlpha)
        assertEquals(
            CALL_AMBIENT_ALPHA_WITHOUT_AVATAR_DARK,
            withoutAvatarDark.minimumDropAlpha,
        )
        assertEquals(
            CALL_AMBIENT_ALPHA_WITHOUT_AVATAR_LIGHT,
            withoutAvatarLight.minimumDropAlpha,
        )
        assertEquals(BlendMode.Overlay, CALL_AMBIENT_BLEND_MODE_WITH_AVATAR)
        assertEquals(BlendMode.SrcOver, CALL_AMBIENT_BLEND_MODE_WITHOUT_AVATAR)
        assertEquals(CompositingStrategy.Offscreen, CALL_AMBIENT_COMPOSITING_STRATEGY)
        assertTrue(CALL_FROSTED_BACKGROUND_ALPHA in 0.92f..0.96f)
        assertEquals(1f, callBackgroundLayerAlpha(frostedWindow = false))
        assertEquals(CALL_FROSTED_BACKGROUND_ALPHA, callBackgroundLayerAlpha(frostedWindow = true))
    }

    @Test
    fun brightnessScalesEachApprovedVisualTreatmentWithoutChangingItsRelativeBalance() {
        val withAvatar = ambientDropVisualSpec(
            hasAvatar = true,
            isDarkTheme = false,
            onSurfaceColor = Color.Black,
            brightnessPercent = 40,
        )
        val withoutAvatar = ambientDropVisualSpec(
            hasAvatar = false,
            isDarkTheme = true,
            onSurfaceColor = Color.Black,
            brightnessPercent = 40,
        )

        assertEquals(CALL_AMBIENT_ALPHA_WITH_AVATAR * 0.4f, withAvatar.maximumDropAlpha)
        assertEquals(
            CALL_AMBIENT_ALPHA_WITHOUT_AVATAR_DARK * 0.4f,
            withoutAvatar.maximumDropAlpha,
        )
        assertEquals(0.1f, ambientDropBrightnessFactor(0))
        assertEquals(0.1f, ambientDropBrightnessFactor(10))
        assertEquals(1f, ambientDropBrightnessFactor(100))
        assertEquals(1.4f, ambientDropBrightnessFactor(140))
        assertEquals(2f, ambientDropBrightnessFactor(200))
        assertEquals(2f, ambientDropBrightnessFactor(300))
    }

    @Test
    fun dropletsBeginAtDifferentPointsInsideEveryAnimationCycle() {
        val first = ambientDropStartOffsets(0)
        val second = ambientDropStartOffsets(1)

        assertNotEquals(first.horizontalMillis, second.horizontalMillis)
        assertNotEquals(first.verticalMillis, second.verticalMillis)
        assertNotEquals(first.alphaMillis, second.alphaMillis)

        (0 until 20).forEach { index ->
            val timing = ambientDropAnimationTiming(index)
            val offsets = ambientDropStartOffsets(index, timing)
            assertTrue(offsets.horizontalMillis in 0 until timing.horizontalDurationMillis * 2)
            assertTrue(offsets.verticalMillis in 0 until timing.verticalDurationMillis * 2)
            assertTrue(offsets.alphaMillis in 0 until timing.alphaDurationMillis * 2)
        }
    }

    @Test
    fun dropletsUseStableVariedDepthProfiles() {
        val specs = (0 until 20).map(::ambientDropDepthSpec)
        val repeatedSpecs = (0 until 20).map(::ambientDropDepthSpec)
        val farthest = specs.minBy { it.depth }
        val nearest = specs.maxBy { it.depth }

        assertEquals(specs, repeatedSpecs)
        assertTrue(specs.map { it.depth }.distinct().size >= 15)
        assertTrue(farthest.diameterScale < nearest.diameterScale)
        assertTrue(specs.all { it.alphaScale == 1f })
        assertTrue(specs.all { it.edgeClarity < .80f })
        specs.forEach { spec ->
            assertTrue(spec.depth in 0f..1f)
            assertTrue(spec.edgeClarity in 0.35f..0.80f)
            assertTrue(spec.solidCoreStop <= .44f)
            assertTrue(spec.solidCoreStop < spec.featherStop)
            assertTrue(spec.featherStop < 1f)
            assertTrue(spec.gradientRadiusScale in 1.12f..1.18f)
        }
    }
}
