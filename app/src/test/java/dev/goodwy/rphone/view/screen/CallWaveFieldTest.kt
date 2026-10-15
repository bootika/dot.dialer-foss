package dev.goodwy.rphone.view.screen

import android.telecom.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class CallWaveFieldTest {

    @Test
    fun `field uses a sparse layered set of contours instead of parallel hair lines`() {
        assertEquals(9, CALL_WAVE_LAYER_COUNT)
        assertTrue(CALL_WAVE_SAMPLE_COUNT >= 180)
        assertEquals(CALL_WAVE_LAYER_COUNT, CALL_WAVE_RADIAL_FRACTIONS.size)
        assertEquals(CALL_WAVE_LAYER_COUNT, CALL_WAVE_FREQUENCIES.size)
        assertEquals(CALL_WAVE_LAYER_COUNT, CALL_WAVE_PHASE_SPEEDS.size)
        assertTrue(CALL_WAVE_FREQUENCIES.min() >= 12f)
        assertTrue(CALL_WAVE_FREQUENCIES.max() >= 22f)
        val radialGaps = CALL_WAVE_RADIAL_FRACTIONS
            .toList()
            .zipWithNext { a, b -> b - a }
        assertTrue(radialGaps.distinct().size > 2)
    }

    @Test
    fun `call avatar automatically uses the largest comfortable vertical size`() {
        assertEquals(296, CALL_SCREEN_WAVE_FIELD_DP)
        assertEquals(164, CALL_SCREEN_WAVE_AVATAR_DP)
        assertEquals(68, CALL_SCREEN_ACTION_DIAMETER_DP)
        assertTrue(CALL_SCREEN_WAVE_AVATAR_DP.toFloat() / CALL_SCREEN_WAVE_FIELD_DP < 0.56f)
        assertTrue(CALL_SCREEN_ACTION_DIAMETER_DP < CALL_SCREEN_WAVE_AVATAR_DP / 2)

        val compact = callAvatarGeometry(availableHeightDp = 600, screenWidthDp = 360).avatarSizeDp
        val standard = callAvatarGeometry(availableHeightDp = 780, screenWidthDp = 360).avatarSizeDp
        val tall = callAvatarGeometry(availableHeightDp = 920, screenWidthDp = 360).avatarSizeDp

        assertEquals(164, compact)
        assertEquals(200, standard)
        assertEquals(220, tall)
        assertTrue(compact < standard)
        assertTrue(standard < tall)
    }

    @Test
    fun `avatar respects width and secondary call vertical budget`() {
        val narrow = callAvatarGeometry(availableHeightDp = 920, screenWidthDp = 240).avatarSizeDp
        val regular = callAvatarGeometry(availableHeightDp = 780, screenWidthDp = 412).avatarSizeDp
        val withOtherCall = callAvatarGeometry(
            availableHeightDp = 780,
            screenWidthDp = 412,
            hasOtherCall = true,
        ).avatarSizeDp

        assertEquals(192, narrow)
        assertEquals(200, regular)
        assertEquals(182, withOtherCall)
        assertTrue(withOtherCall < regular)
    }

    @Test
    fun `avatar mask is a high frequency animated wave rather than a static flower`() {
        val samples = (0..360).map { sample ->
            val angle = sample / 360f * (PI * 2.0).toFloat()
            callAvatarRadiusFactor(angle = angle, phase = 0.4f, activity = 0.78f)
        }
        val signChanges = samples
            .map { it - 1f }
            .zipWithNext()
            .count { (a, b) -> (a < 0f && b >= 0f) || (a >= 0f && b < 0f) }
        assertTrue("mask needs visibly higher spatial frequency", signChanges >= 24)

        val changedByPhase = (0..24).count { sample ->
            val angle = sample / 24f * (PI * 2.0).toFloat()
            val first = callAvatarRadiusFactor(angle, phase = 0.2f, activity = 0.78f)
            val second = callAvatarRadiusFactor(angle, phase = 1.1f, activity = 0.78f)
            kotlin.math.abs(first - second) > 0.003f
        }
        assertTrue("the mask must morph as phase changes", changedByPhase >= 18)
        assertTrue(samples.all { it in 0.89f..1.11f })
    }

    @Test
    fun `call state controls wave energy`() {
        assertTrue(callWaveActivity(Call.STATE_RINGING) > callWaveActivity(Call.STATE_ACTIVE))
        assertTrue(callWaveActivity(Call.STATE_ACTIVE) > callWaveActivity(Call.STATE_HOLDING))
        assertTrue(callWaveActivity(Call.STATE_HOLDING) > callWaveActivity(Call.STATE_DISCONNECTED))
    }

    @Test
    fun `accelerometer motion increases contour amplitude and spatial frequency`() {
        val still = (0..720).map { sample ->
            val angle = sample / 720f * (PI * 2.0).toFloat()
            callWaveRadiusFactor(angle, phase = 0.72f, layer = 4, activity = 0.62f, motion = 0f)
        }
        val moving = (0..720).map { sample ->
            val angle = sample / 720f * (PI * 2.0).toFloat()
            callWaveRadiusFactor(angle, phase = 0.72f, layer = 4, activity = 0.62f, motion = 1f)
        }
        val stillDeviation = still.maxOf { kotlin.math.abs(it - 1f) }
        val movingDeviation = moving.maxOf { kotlin.math.abs(it - 1f) }
        val stillCrossings = still.map { it - 1f }.zipWithNext().count { (a, b) -> a * b < 0f }
        val movingCrossings = moving.map { it - 1f }.zipWithNext().count { (a, b) -> a * b < 0f }

        assertTrue(movingDeviation > stillDeviation * 1.2f)
        assertTrue(movingCrossings > stillCrossings)
    }

    @Test
    fun `every contour stays visually stable across a full rotation`() {
        repeat(CALL_WAVE_LAYER_COUNT) { layer ->
            repeat(CALL_WAVE_SAMPLE_COUNT + 1) { sample ->
                val angle = sample.toFloat() / CALL_WAVE_SAMPLE_COUNT * (PI * 2.0).toFloat()
                val radiusFactor = callWaveRadiusFactor(
                    angle = angle,
                    phase = 1.37f,
                    layer = layer,
                    activity = 1f,
                )
                assertTrue("layer=$layer sample=$sample factor=$radiusFactor", radiusFactor in 0.87f..1.13f)
            }
        }
    }
}
