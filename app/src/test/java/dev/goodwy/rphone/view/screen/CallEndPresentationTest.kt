package dev.goodwy.rphone.view.screen

import android.app.Application
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import java.io.File
import dev.goodwy.rphone.view.theme.DotDialerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallEndPresentationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun endingDoesNotPresentConnectingOrResetTheLastTimer() {
        verifyEnding(false)
    }

    @Test fun darkEndingDoesNotPresentConnectingOrResetTheLastTimer() {
        verifyEnding(true)
    }

    private fun verifyEnding(dark: Boolean) {
        val state = mutableStateOf(Call.STATE_ACTIVE)
        val duration = mutableStateOf("1:23")
        rule.setContent {
            DotDialerTheme(systemDark = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    CallIdentityHeader("Test Alexandru Butica", null, state.value, duration.value,
                        null, null, callScreenSpacing(800, false), 0, {})
                }
            }
        }
        rule.onNodeWithText("1:23").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { state.value = Call.STATE_DISCONNECTING; duration.value = "—" }
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithText("Connecting").assertDoesNotExist()
        rule.onNodeWithText("1:23").assertIsDisplayed()
        capture("${if (dark) "dark" else "light"}-disconnecting")
        rule.runOnIdle { state.value = Call.STATE_DISCONNECTED }
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithText("Call Ended").assertIsDisplayed()
        rule.onNodeWithText("1:23").assertDoesNotExist()
        capture("${if (dark) "dark" else "light"}-ended")
    }

    private fun capture(name: String) {
        val file = File("build/reports/visual/call-end-d78/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height,
                android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun terminalStateNeverRevertsToConnectingAndNewCallStateIsNotFrozen() {
        for (previous in listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING, Call.STATE_DIALING, Call.STATE_RINGING)) {
            assertEquals(previous, callMetadataDisplayState(Call.STATE_DISCONNECTING, previous))
            assertEquals(Call.STATE_DISCONNECTED, callMetadataDisplayState(Call.STATE_DISCONNECTED, previous))
            assertEquals(Call.STATE_RINGING, callMetadataDisplayState(Call.STATE_RINGING, previous))
        }
        assertEquals(Call.STATE_DISCONNECTED,
            callMetadataDisplayState(Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED))
    }
}
