package dev.goodwy.rphone.view.screen

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36], application=Application::class)
class DialpadIncomingNumberTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()

    @Test fun incomingInternationalNumberReplacesMountedDraftButRecompositionKeepsEdits() {
        val incoming=mutableStateOf<String?>("03614283555")
        val redraw=mutableStateOf(0)
        lateinit var editor: MutableState<String>
        rule.setContent {
            editor=rememberDialpadNumber(incoming.value) { "0722123456" }
            Text(editor.value)
            Text(redraw.value.toString())
        }
        rule.onNodeWithText("03614283555").assertExists()
        rule.runOnIdle { incoming.value="+493614283555" }
        rule.onNodeWithText("+493614283555").assertExists()
        rule.runOnIdle { editor.value="+493614283556"; redraw.value++ }
        rule.onNodeWithText("+493614283556").assertExists()
        rule.runOnIdle { incoming.value="+390612345678" }
        rule.onNodeWithText("+390612345678").assertExists()
    }

    @Test fun nullLaunchKeepsDraftAndExplicitEmptyClearsIt() {
        val incoming=mutableStateOf<String?>(null)
        lateinit var editor: MutableState<String>
        rule.setContent { editor=rememberDialpadNumber(incoming.value) { "+493614283555" }; Text(editor.value) }
        rule.onNodeWithText("+493614283555").assertExists()
        rule.runOnIdle { incoming.value="" }
        rule.runOnIdle { assertEquals("",editor.value) }
        rule.runOnIdle { incoming.value="112" }
        rule.onNodeWithText("112").assertExists()
        rule.runOnIdle { assertEquals("112",editor.value) }
    }
}
