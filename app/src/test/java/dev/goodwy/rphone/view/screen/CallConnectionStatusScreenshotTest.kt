package dev.goodwy.rphone.view.screen

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.telecom.PhoneAccount
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [36],
    qualifiers = "w360dp-h260dp",
    application = Application::class,
)
class CallConnectionStatusScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val vodafoneAccount: PhoneAccount = PhoneAccount.builder(
        PhoneAccountHandle(ComponentName("dot.dialer.test", "TestPhoneAccountService"), "sim-1"),
        "Vodafone",
    ).build()

    @Test
    fun `metadata shares readable theme aware text colors in compact and accessible layouts`() {
        val dark = mutableStateOf(false)
        val scale = mutableStateOf(1f)
        val quality = mutableStateOf<VoiceQualityTier?>(VoiceQualityTier.HdPlus)
        val transport = mutableStateOf(CallTransportKind.Cellular)
        val hasAccount = mutableStateOf(true)
        var expectedColor = Color.Unspecified
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                DotDialerTheme(systemDark = dark.value) {
                    expectedColor = MaterialTheme.colorScheme.onSurface
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        CallConnectionStatusRow(
                            CallConnectionInfo(transport.value, quality.value, null, null, null),
                            "01:34", {}, Modifier.width(312.dp),
                            account = if (hasAccount.value) vodafoneAccount else null,
                        )
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) for (fontScale in listOf(1f, 1.5f)) {
            for (tier in listOf(VoiceQualityTier.HdPlus, VoiceQualityTier.Hd, null)) {
                for (connection in CallTransportKind.entries) for (accountPresent in listOf(true, false)) {
                    composeRule.runOnIdle {
                        dark.value = isDark; scale.value = fontScale; quality.value = tier
                        transport.value = connection; hasAccount.value = accountPresent
                    }
                    composeRule.waitForIdle()
                    val labels = listOfNotNull(
                        if (accountPresent) "Vodafone" else null,
                        when (tier) { VoiceQualityTier.HdPlus -> "HD+ Voice"; VoiceQualityTier.Hd -> "HD Voice"; null -> "Voice call" },
                        if (connection == CallTransportKind.Cellular) "Cellular" else "Wi-Fi Call",
                    )
                    labels.forEach { label ->
                        val results = mutableListOf<TextLayoutResult>()
                        composeRule.onNodeWithText(label, useUnmergedTree = true)
                            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                        val color = results.single().layoutInput.style.color
                        val creamBadge = label == "HD Voice"
                        val expected = if (creamBadge) dev.goodwy.rphone.view.theme.DotInk else expectedColor
                        org.junit.Assert.assertEquals("$label dark=$isDark scale=$fontScale", expected, color)
                        org.junit.Assert.assertTrue(if (isDark && !creamBadge) color.luminance() > 0.75f else color.luminance() < 0.10f)
                    }
                    if (accountPresent && tier == VoiceQualityTier.HdPlus && connection == CallTransportKind.Cellular) {
                        capture("call-metadata-${if (isDark) "dark" else "light"}-scale-$fontScale.png")
                    }
                }
            }
        }
    }

    @Test
    fun `actual font scaling preserves a compact centered pill and wraps only when necessary`() {
        val scale = mutableStateOf(1f)
        val interfaceScale = mutableStateOf(1f)
        // CallScreen has 24dp horizontal padding on a 360dp phone.
        val availableWidth = mutableStateOf(312)
        var measuredDensity = 1f
        val transport = mutableStateOf(CallTransportKind.Cellular)
        val quality = mutableStateOf<VoiceQualityTier?>(VoiceQualityTier.HdPlus)
        val hasAccount = mutableStateOf(true)
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, scale.value)) {
                DotDialerTheme(systemDark = false, interfaceScale = interfaceScale.value) {
                    measuredDensity = LocalDensity.current.density
                    Column(Modifier.fillMaxSize().background(Color(0xFF7D9390)), horizontalAlignment = Alignment.CenterHorizontally) {
                        CallConnectionStatusRow(
                            CallConnectionInfo(transport.value, quality.value, null, null, null),
                            "1:00:00", {}, Modifier.width(availableWidth.value.dp).testTag("metadataAvailable"),
                            account = if (hasAccount.value) vodafoneAccount else null,
                        )
                    }
                }
            }
        }
        fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        fun assertSharedTypography(voiceLabel: String) {
            fun style(label: String): TextStyle {
                val results = mutableListOf<TextLayoutResult>()
                composeRule.onNodeWithText(label, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(results) }
                return results.single().layoutInput.style
            }
            val reference = style("Cellular")
            listOf("Vodafone", voiceLabel).forEach { label ->
                val actual = style(label)
                org.junit.Assert.assertEquals(reference.fontFamily, actual.fontFamily)
                org.junit.Assert.assertEquals(reference.fontSize, actual.fontSize)
                org.junit.Assert.assertEquals(reference.fontWeight, actual.fontWeight)
                org.junit.Assert.assertEquals(reference.lineHeight, actual.lineHeight)
                org.junit.Assert.assertEquals(reference.letterSpacing, actual.letterSpacing)
            }
        }
        val cases = listOf(1f to 1f, 1.15f to 1f, 1.15f to 1.2f, 1.5f to 1f, 2f to 1f)
        cases.forEachIndexed { index, (androidScale, appScale) ->
            composeRule.runOnIdle { scale.value = androidScale; interfaceScale.value = appScale; quality.value = VoiceQualityTier.HdPlus }
            composeRule.waitForIdle()
            assertSharedTypography("HD+ Voice")
            val pill = bounds(CALL_CONNECTION_QUALITY_TEST_TAG)
            val account = bounds("callAccountCell")
            val output = bounds("callTransportCell")
            val shield = bounds("callConnectionShield")
            org.junit.Assert.assertTrue(pill.width < shield.width * 0.6f)
            if (index == 0) {
                org.junit.Assert.assertEquals("Normal font remains compact", account.center.y, pill.center.y, 0.5f)
            }
            if (kotlin.math.abs(account.center.y - pill.center.y) <= .5f) {
                org.junit.Assert.assertEquals(account.center.y, pill.center.y, 0.5f)
                org.junit.Assert.assertEquals(pill.center.y, output.center.y, 0.5f)
                org.junit.Assert.assertEquals(shield.center.x, pill.center.x, 0.5f)
                org.junit.Assert.assertEquals(pill.left - account.right, output.left - pill.right, 0.5f)
            } else {
                // The larger real padding can require wrapping sooner. Prove it is necessary
                // from measured cell widths, rather than exempting a fixed font-scale case.
                val available = bounds("metadataAvailable")
                val minimumSingleRow = 2 * maxOf(account.width, output.width) + pill.width +
                    (2 * CALL_CONNECTION_STATUS_INSET_DP + 4) * measuredDensity
                org.junit.Assert.assertTrue("Wrapping only when measured cells cannot fit", minimumSingleRow > available.width)
                org.junit.Assert.assertTrue("SIM occupies the first accessible row", account.center.y < pill.center.y)
            }
            capture("call-metadata-scale-$index.png")
            listOf(VoiceQualityTier.Hd, null).forEach { tier ->
                composeRule.runOnIdle { quality.value = tier }
                composeRule.waitForIdle()
                assertSharedTypography(if (tier == VoiceQualityTier.Hd) "HD Voice" else "Voice call")
                org.junit.Assert.assertEquals(pill, bounds(CALL_CONNECTION_QUALITY_TEST_TAG))
            }
        }
        composeRule.runOnIdle { scale.value = 1.15f; interfaceScale.value = 1f; transport.value = CallTransportKind.WifiCalling; availableWidth.value = 280; hasAccount.value = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Wi-Fi Call").assertIsDisplayed()
        org.junit.Assert.assertTrue(bounds("callConnectionShield").width <= 280 * composeRule.activity.resources.displayMetrics.density + 1)
        capture("call-metadata-wifi-no-sim.png")
    }

    private fun capture(name: String) {
        val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/$name")
        requireNotNull(output.parentFile).mkdirs()
        composeRule.runOnIdle {
            val view = composeRule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            FileOutputStream(output).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }

    @Test
    fun `shared header reserves the same height in all call states at enlarged font sizes`() {
        val state = mutableStateOf(Call.STATE_DIALING)
        val scale = mutableStateOf(1.15f)
        val info = CallConnectionInfo(CallTransportKind.Cellular, VoiceQualityTier.HdPlus, null, null, null)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                DotDialerTheme(systemDark = false) {
                    Column(Modifier.width(312.dp).testTag("identityHeader")) {
                        CallIdentityHeader(
                            contactName = "Test Alexandru Butica", companyName = null,
                            callState = state.value, duration = "00:00", phoneAccount = vodafoneAccount,
                            connectionInfo = info, spacing = callScreenSpacing(740, false),
                            identityVerticalCompensationDp = 0, onConnectionDetailsClick = {},
                        )
                    }
                }
            }
        }
        listOf(1.15f, 1.5f, 2f).forEach { fontScale ->
            composeRule.runOnIdle { scale.value = fontScale; state.value = Call.STATE_DIALING }
            composeRule.waitForIdle()
            val height = composeRule.onNodeWithTag("identityHeader").fetchSemanticsNode().boundsInRoot.height
            listOf(Call.STATE_RINGING, Call.STATE_ACTIVE, Call.STATE_HOLDING, Call.STATE_DISCONNECTED).forEach { callState ->
                composeRule.runOnIdle { state.value = callState }
                composeRule.waitForIdle()
                org.junit.Assert.assertEquals(height, composeRule.onNodeWithTag("identityHeader").fetchSemanticsNode().boundsInRoot.height, 0.5f)
            }
        }
    }

    @Test
    fun `render stable call duration slots`() {
        composeRule.setContent {
            DotDialerTheme(systemDark = false) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF7D9390)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    listOf("00:05", "59:59", "1:00:00").forEach { duration ->
                        CallConnectionStatusRow(
                            info = CallConnectionInfo(
                                transport = CallTransportKind.Cellular,
                                quality = VoiceQualityTier.HdPlus,
                                codecLabel = "EVS-SWB",
                                bandwidthKhz = null,
                                bitrateKbps = null,
                            ),
                            duration = duration,
                            onClick = {},
                            account = vodafoneAccount,
                        )
                    }
                }
            }
        }

        val output = File(
            requireNotNull(System.getProperty("user.dir")),
            "build/reports/visual/call-connection-status-light.png",
        )
        requireNotNull(output.parentFile).mkdirs()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val view = composeRule.activity.window.decorView
            check(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            FileOutputStream(output).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
    }

    @Test
    fun `render a stable neutral voice segment when codec quality is not reported`() {
        composeRule.setContent {
            DotDialerTheme(systemDark = false) {
                CallConnectionStatusRow(
                    info = CallConnectionInfo(
                        transport = CallTransportKind.Cellular,
                        quality = null,
                        codecLabel = null,
                        bandwidthKhz = null,
                        bitrateKbps = null,
                    ),
                    duration = "00:05",
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Voice call").assertIsDisplayed()
    }

    @Test
    fun `quality pill keeps the same bounds for every voice quality label`() {
        lateinit var quality: androidx.compose.runtime.MutableState<VoiceQualityTier?>
        composeRule.setContent {
            quality = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(VoiceQualityTier.HdPlus)
            }
            DotDialerTheme(systemDark = false) {
                CallConnectionStatusRow(
                    info = CallConnectionInfo(
                        transport = CallTransportKind.Cellular,
                        quality = quality.value,
                        codecLabel = null,
                        bandwidthKhz = null,
                        bitrateKbps = null,
                    ),
                    duration = "00:05",
                    onClick = {},
                )
            }
        }

        fun qualityBounds() = composeRule
            .onNodeWithTag(CALL_CONNECTION_QUALITY_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        val hdPlusBounds = qualityBounds()
        composeRule.runOnIdle { quality.value = VoiceQualityTier.Hd }
        composeRule.waitForIdle()
        val hdBounds = qualityBounds()
        composeRule.runOnIdle { quality.value = null }
        composeRule.waitForIdle()
        val neutralBounds = qualityBounds()

        org.junit.Assert.assertEquals(hdPlusBounds.width, hdBounds.width, 0.01f)
        org.junit.Assert.assertEquals(hdPlusBounds.height, hdBounds.height, 0.01f)
        org.junit.Assert.assertEquals(hdPlusBounds.width, neutralBounds.width, 0.01f)
        org.junit.Assert.assertEquals(hdPlusBounds.height, neutralBounds.height, 0.01f)
    }
}
