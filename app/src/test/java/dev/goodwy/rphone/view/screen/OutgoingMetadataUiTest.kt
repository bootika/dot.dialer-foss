package dev.goodwy.rphone.view.screen

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.telecom.Call
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h360dp-mdpi")
class OutgoingMetadataUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `outgoing and established calls share shield bounds without premature quality or timer`() {
        val state = mutableStateOf(Call.STATE_DIALING)
        val dark = mutableStateOf(false)
        val font = mutableStateOf(1f)
        val wifi = mutableStateOf(false)
        val hasAccount = mutableStateOf(true)
        var details = 0
        val account = PhoneAccount.builder(PhoneAccountHandle(ComponentName("dot.dialer.test", "TestPhoneService"), "sim-1"), "Vodafone").build()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, font.value)) {
                DotDialerTheme(systemDark = dark.value) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.width(312.dp)) {
                        CallIdentityHeader("Test Alexandru Butica", null, state.value, "00:05",
                            if (hasAccount.value) account else null,
                            CallConnectionInfo(if (wifi.value) CallTransportKind.WifiCalling else CallTransportKind.Cellular, VoiceQualityTier.HdPlus, "EVS-SWB", null, null),
                            callScreenSpacing(740, false), 0, { details++ })
                    }
                    }
                }
            }
        }
        fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        for (mode in listOf(false, true)) for (scale in listOf(1f, 1.5f, 2f)) {
            rule.runOnIdle { dark.value = mode; font.value = scale; state.value = Call.STATE_DIALING }
            rule.waitForIdle()
            val shield = bounds("callConnectionShield")
            val header = bounds("callIdentityHeader")
            for (phase in listOf(Call.STATE_DIALING, Call.STATE_CONNECTING)) {
                rule.runOnIdle { state.value = phase }; rule.waitForIdle()
                rule.onNodeWithText("Connecting", useUnmergedTree = true).assertIsDisplayed()
                rule.onNodeWithText("HD+ Voice").assertDoesNotExist()
                rule.onNodeWithTag("callPrimaryTimer", useUnmergedTree = true).assertDoesNotExist()
                val layout = mutableListOf<TextLayoutResult>()
                rule.onNodeWithTag("callConnectingLabel", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
                assertFalse("Connecting remains readable", layout.single().hasVisualOverflow)
                assertEquals(shield, bounds("callConnectionShield"))
            }
            val beforeClick = details
            rule.onNodeWithText("Connecting").performClick(); assertEquals(beforeClick + 1, details)
            capture("outgoing-$mode-$scale.png")
            rule.runOnIdle { state.value = Call.STATE_ACTIVE }; rule.waitForIdle()
            rule.onNodeWithText("HD+ Voice", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("callPrimaryTimer", useUnmergedTree = true).assertIsDisplayed()
            assertEquals(shield, bounds("callConnectionShield"))
            assertEquals(header, bounds("callIdentityHeader"))
        }
        rule.runOnIdle { state.value = Call.STATE_CONNECTING; wifi.value = true; hasAccount.value = false }
        rule.waitForIdle()
        rule.onNodeWithText("Wi-Fi Call", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Vodafone").assertDoesNotExist()
        rule.onNodeWithText("Connecting", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun capture(name: String) {
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File(System.getProperty("user.dir"), "build/reports/visual/outgoing-metadata/$name")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
