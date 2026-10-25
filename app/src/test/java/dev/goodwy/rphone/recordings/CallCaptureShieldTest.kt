package dev.goodwy.rphone.recordings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.screen.recordings.RecordingCaptureShield
import dev.goodwy.rphone.view.theme.DotDialerTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class CallCaptureShieldTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun bothThemesRenderElapsedStopAndSavingWithoutEndingCall() {
        var dark by mutableStateOf(false);var phase by mutableStateOf("recording");var stops=0
        rule.setContent {DotDialerTheme(systemDark=dark){Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){
            Box(Modifier.width(312.dp)){RecordingCaptureShield(CallCaptureState("test",phase,18000)){stops++}}
        }}}
        for(theme in listOf(false,true)) {
            rule.runOnIdle {dark=theme};rule.onNodeWithText("Recording · 0:18").assertIsDisplayed()
            val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText("Recording · 0:18").performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertEquals(theme,layouts.single().layoutInput.style.color.luminance()>.5f)
            rule.onNodeWithTag("capture-stop").assertIsDisplayed().performClick();assertEquals(if(theme)2 else 1,stops)
        }
        rule.runOnIdle {phase="saving"}
        rule.onNodeWithTag("capture-stop").assertDoesNotExist();rule.onNodeWithText("Saving recording…").assertIsDisplayed()
        rule.runOnIdle {phase="waiting"}
        rule.onNodeWithText("Recording will start when connected").assertIsDisplayed()
        rule.onNodeWithTag("capture-stop").assertIsDisplayed().performClick()
        assertEquals(3,stops)
    }
}
