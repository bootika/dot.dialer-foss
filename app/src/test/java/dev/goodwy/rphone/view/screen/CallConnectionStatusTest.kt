package dev.goodwy.rphone.view.screen

import android.telecom.Connection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [36], application = android.app.Application::class)
class CallConnectionStatusTest {
    @Test fun `HD plus uses app Jade with alpha rather than theme container color`() {
        val jade = dev.goodwy.rphone.view.theme.DotJade
        val color = requireNotNull(callVoiceQualityHighlightColor(VoiceQualityTier.HdPlus))
        assertEquals(jade.red, color.red, 0f)
        assertEquals(jade.green, color.green, 0f)
        assertEquals(jade.blue, color.blue, 0f)
        assertEquals(.23f, color.alpha, 1f / 255f)
        assertEquals(8, CALL_CONNECTION_QUALITY_HORIZONTAL_PADDING_DP)
    }
    @Test fun `HD keeps saturation plus four and lowers D36 lightness by eight points`() {
        val cream = dev.goodwy.rphone.view.theme.DotCallQualityCream
        val base = dev.goodwy.rphone.view.theme.DotIvory
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(0xFFF2E5CA.toInt(), hsl)
        assertEquals(40.5f, hsl[0], 0.001f)
        hsl[1] += 0.04f
        hsl[2] += 0.04f - 0.08f
        assertEquals(androidx.compose.ui.graphics.Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl)), cream)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF0DEB8), cream)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF7F5EF), base)
        val color = requireNotNull(callVoiceQualityHighlightColor(VoiceQualityTier.Hd))
        assertEquals(cream.red, color.red, 0f)
        assertEquals(cream.green, color.green, 0f)
        assertEquals(cream.blue, color.blue, 0f)
        assertEquals(.50f, CALL_CONNECTION_HD_ALPHA, 0f)
        assertEquals(.50f, color.alpha, 1f / 255f)
        assertNull(callVoiceQualityHighlightColor(null))
    }
    @Test
    fun `connection shield uses selected opacity in each theme`() {
        assertEquals(0.50f, CALL_CONNECTION_SHIELD_LIGHT_ALPHA, 0f)
        assertEquals(0.20f, CALL_CONNECTION_SHIELD_DARK_ALPHA, 0f)
        // sRGB Color stores alpha in eight bits; allow one quantization step.
        assertEquals(0.50f, callConnectionShieldColor(darkTheme = false).alpha, 1f / 255f)
        assertEquals(0.20f, callConnectionShieldColor(darkTheme = true).alpha, 1f / 255f)
        for (darkTheme in listOf(false, true)) {
            val color = callConnectionShieldColor(darkTheme)
            assertEquals(1f, color.red, 0.001f)
            assertEquals(1f, color.green, 0.001f)
            assertEquals(1f, color.blue, 0.001f)
        }
    }

    @Test
    fun `duration slot stays fixed while seconds and minutes change`() {
        val expectedWidth = callDurationSlotWidthDp("00:00")

        assertEquals(expectedWidth, callDurationSlotWidthDp("00:09"))
        assertEquals(expectedWidth, callDurationSlotWidthDp("09:59"))
        assertEquals(expectedWidth, callDurationSlotWidthDp("59:59"))
    }

    @Test
    fun `duration slot expands only when hours appear`() {
        assertEquals(76, callDurationSlotWidthDp("59:59"))
        assertEquals(100, callDurationSlotWidthDp("1:00:00"))
        assertEquals(100, callDurationSlotWidthDp("9:59:59"))
    }

    @Test
    fun `large Android fonts expand stable timer and metadata slots`() {
        assertEquals(114, callDurationSlotWidthDp("00:02", fontScale = 1.5f))
        assertEquals(150, callDurationSlotWidthDp("1:00:00", fontScale = 1.5f))
        assertEquals(54, callTimerSlotHeightDp(baseHeightDp = 36, fontScale = 1.5f))
        assertEquals(1.38f, effectiveCallFontScale(1.15f, labelSizeSp = 16.8f), 0.001f)
    }

    @Test
    fun `measured content keeps one row and adapts spacing before wrapping`() {
        val standard = callConnectionStatusGeometry(328, 82, 86, 94, 20)
        assertEquals(1, standard.rows)
        assertEquals(306, standard.widthDp)
        val enlarged = callConnectionStatusGeometry(328, 92, 103, 105, 24)
        assertEquals(1, enlarged.rows)
        assertEquals(4, enlarged.gapDp)
        assertEquals(48, enlarged.touchHeightDp)
    }

    @Test
    fun `fallback uses compact content widths and handles missing SIM`() {
        val large = callConnectionStatusGeometry(328, 120, 128, 142, 40)
        assertEquals(2, large.rows)
        assertEquals(142, large.qualityWidthDp)
        assertEquals(286, large.widthDp)
        val noAccount = callConnectionStatusGeometry(328, null, 128, 142, 40)
        assertEquals(1, noAccount.rows)
        val narrow = callConnectionStatusGeometry(160, 120, 128, 142, 40)
        assertEquals(3, narrow.rows)
        org.junit.Assert.assertTrue(narrow.widthDp <= 160)
    }

    @Test
    fun `quality pill keeps a fixed geometry with equal outer insets`() {
        assertEquals(48, CALL_CONNECTION_TOUCH_HEIGHT_DP)
        assertEquals(38, CALL_CONNECTION_STATUS_HEIGHT_DP)
        assertEquals(30, CALL_CONNECTION_QUALITY_PILL_HEIGHT_DP)
        assertEquals(
            CALL_CONNECTION_STATUS_INSET_DP,
            (CALL_CONNECTION_STATUS_HEIGHT_DP - CALL_CONNECTION_QUALITY_PILL_HEIGHT_DP) / 2,
        )
        assertEquals(94, CALL_CONNECTION_QUALITY_PILL_WIDTH_DP)
    }

    @Test
    fun `all three status cells use the same stable width`() {
        assertEquals(CALL_CONNECTION_CELL_WIDTH_DP, CALL_CONNECTION_ACCOUNT_CELL_WIDTH_DP)
        assertEquals(CALL_CONNECTION_CELL_WIDTH_DP, CALL_CONNECTION_TRANSPORT_CELL_WIDTH_DP)
        assertEquals(CALL_CONNECTION_CELL_WIDTH_DP, CALL_CONNECTION_QUALITY_PILL_WIDTH_DP)
    }

    @Test
    fun `status shield width changes only when the account cell exists`() {
        assertEquals(204, callConnectionStatusWidthDp(hasAccount = false))
        assertEquals(306, callConnectionStatusWidthDp(hasAccount = true))
    }

    @Test
    fun `super wideband and fullband EVS use HD plus Voice`() {
        assertEquals(
            VoiceQualityTier.HdPlus,
            classifyVoiceQuality(Connection.AUDIO_CODEC_EVS_SWB, platformReportsHighDefinition = false),
        )
        assertEquals(
            VoiceQualityTier.HdPlus,
            classifyVoiceQuality(Connection.AUDIO_CODEC_EVS_FB, platformReportsHighDefinition = false),
        )
    }

    @Test
    fun `wideband codecs use HD Voice`() {
        listOf(
            Connection.AUDIO_CODEC_AMR_WB,
            Connection.AUDIO_CODEC_EVS_WB,
            Connection.AUDIO_CODEC_EVRC_WB,
            Connection.AUDIO_CODEC_EVRC_NW,
            Connection.AUDIO_CODEC_G722,
        ).forEach { codec ->
            assertEquals(
                VoiceQualityTier.Hd,
                classifyVoiceQuality(codec, platformReportsHighDefinition = false),
            )
        }
    }

    @Test
    fun `platform HD property is the fallback when codec is unavailable`() {
        assertEquals(
            VoiceQualityTier.Hd,
            classifyVoiceQuality(codec = null, platformReportsHighDefinition = true),
        )
        assertNull(classifyVoiceQuality(codec = null, platformReportsHighDefinition = false))
    }

    @Test
    fun `narrowband codec does not receive an HD badge`() {
        assertNull(
            classifyVoiceQuality(
                codec = Connection.AUDIO_CODEC_EVS_NB,
                platformReportsHighDefinition = false,
            ),
        )
    }
}
