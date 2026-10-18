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

    @Test fun `quick actions can be selected only in the tap selector`() {
        render("Tap a contact or recent",includeQuickActions=true)
        openAndVerifyChoices()
        rule.onNodeWithText("Quick actions").performClick()
        rule.runOnIdle{assertEquals(9,selected)}
    }

    @Test fun `tap selector shows approved choices in order and Contact selects details action`() {
        render("Tap a recent")
        openAndVerifyChoices()
        capture("home-gesture-selector.png")
        rule.onNodeWithText("Contact").performClick()
        rule.runOnIdle { assertEquals(7, selected) }
    }

    @Test fun `right swipe selector allows Call and no action is first`() {
        render("Swipe right", includeDelete = true)
        openAndVerifyChoices()
        rule.onAllNodesWithText("Call")[1].performClick()
        rule.runOnIdle { assertEquals(1, selected) }
    }

    @Test fun `left swipe selector allows Copy and no action selection`() {
        render("Swipe left", includeDelete = true)
        openAndVerifyChoices()
        rule.onNodeWithText("Copy").performClick()
        rule.runOnIdle { assertEquals(5, selected) }
        rule.onNodeWithText("Call").performClick()
        rule.onNodeWithText("No action").performClick()
        rule.runOnIdle { assertEquals(0, selected) }
    }

    @Test fun `delete call appears only in swipe choices and can be selected`() {
        render("Swipe left", includeDelete = true)
        openAndVerifyChoices()
        capture("home-gesture-delete-selector.png")
        rule.onNodeWithText("Delete").performClick()
        rule.runOnIdle { assertEquals(8, selected) }
    }

    private var includesDelete = false
    private var includesQuickActions = false

    private fun render(title: String, includeDelete: Boolean = false, includeQuickActions: Boolean = false) {
        includesDelete = includeDelete
        includesQuickActions = includeQuickActions
        rule.setContent {
            DotDialerTheme(systemDark = false) {
                val choices = homeRowActionChoices(includeDelete, includeQuickActions)
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
        val expected = listOf("No action", "Call", "SMS", "WhatsApp", "Contact", "Copy") + if (includesDelete) listOf("Delete") else if(includesQuickActions) listOf("Quick actions") else emptyList()
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
            val canvas = Canvas(bitmap)
            val manager = org.robolectric.util.ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.view.WindowManagerGlobal"), "getInstance")
            val windows = org.robolectric.util.ReflectionHelpers.getField<List<android.view.View>>(manager, "mViews")
            windows.forEach { window ->
                val location = IntArray(2); window.getLocationOnScreen(location)
                canvas.save(); canvas.translate(location[0].toFloat(), location[1].toFloat()); window.draw(canvas); canvas.restore()
            }
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }
}
