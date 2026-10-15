package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class HomeGestureSelectorTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var selected = -1
    private var observedChoices = emptyList<Pair<String, Int>>()

    @Test fun `tap selector shows approved choices in order and Contact selects details action`() {
        render("Tap a recent")
        openAndVerifyChoices()
        capture("home-gesture-selector.png")
        rule.onNodeWithText("Contact").performClick()
        rule.runOnIdle { assertEquals(7, selected) }
    }

    @Test fun `right swipe selector allows Call and no action is first`() {
        render("Swipe right")
        openAndVerifyChoices()
        rule.onAllNodesWithText("Call")[1].performClick()
        rule.runOnIdle { assertEquals(1, selected) }
    }

    @Test fun `left swipe selector allows Copy and no action selection`() {
        render("Swipe left")
        openAndVerifyChoices()
        rule.onNodeWithText("Copy").performClick()
        rule.runOnIdle { assertEquals(5, selected) }
        rule.onNodeWithText("Call").performClick()
        rule.onNodeWithText("No action").performClick()
        rule.runOnIdle { assertEquals(0, selected) }
    }

    private fun render(title: String) {
        rule.setContent {
            DotDialerTheme(systemDark = false) {
                val choices = homeRowActionChoices()
                observedChoices = choices
                DotSettingsCard {
                    DotSettingsChoiceRow(title, "Call", choices, 1, { selected = it })
                }
            }
        }
    }

    private fun openAndVerifyChoices() {
        rule.onNodeWithText("Call").performClick()
        rule.waitForIdle()
        val expected = listOf("No action", "Call", "SMS", "WhatsApp", "Contact", "Copy")
        rule.runOnIdle { assertEquals(expected, observedChoices.map { it.first }) }
        val tops = expected.map { text ->
            rule.onAllNodesWithText(text).fetchSemanticsNodes().last().boundsInRoot.top
        }
        assertTrue("Dropdown follows the approved order", tops.zipWithNext().all { (a, b) -> a < b })
        rule.onNodeWithText("Video call").assertDoesNotExist()
        rule.onNodeWithText("Open details").assertDoesNotExist()
    }

    private fun capture(name: String) {
        val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/$name")
        output.parentFile!!.mkdirs()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }
}
