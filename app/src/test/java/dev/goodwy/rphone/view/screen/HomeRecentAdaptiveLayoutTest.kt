package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.util.forceLtr
import dev.goodwy.rphone.view.components.CallLogVisual
import dev.goodwy.rphone.view.components.CallLogVisualStatus
import dev.goodwy.rphone.view.components.LocalRillAvatarAppearance
import dev.goodwy.rphone.view.components.RillAvatarAppearance
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.assertFalse
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
@Config(sdk = [36], application = Application::class, qualifiers = "w420dp-h900dp-mdpi")
class HomeRecentAdaptiveLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val settings = mutableStateOf(Scenario())

    @Test fun `150 percent font keeps full name and number and moves yesterday below them`() {
        render()
        capture("recent-font-150.png")
        assertReadable(settings.value.title)
        assertReadable(PHONE.forceLtr())
        val number = rule.onNodeWithText(PHONE.forceLtr(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = rule.onNodeWithText("Yesterday", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Date must no longer consume number width", time.top >= number.bottom)
    }

    @Test fun `long names and international numbers remain readable across widths fonts and dates`() {
        render()
        listOf(320, 360, 420).forEach { width ->
            listOf(1f, 1.15f, 1.5f, 2f).forEach { scale ->
                rule.runOnUiThread { settings.value = Scenario(width = width, fontScale = scale, time = "Yesterday, 23:59") }
                rule.waitForIdle()
                capture("recent-width-$width-font-${(scale * 100).toInt()}.png")
                assertReadable(settings.value.title)
                assertReadable(PHONE.forceLtr())
                assertReadable(settings.value.time)
            }
        }
    }

    @Test fun `short content stays compact at normal font size`() {
        settings.value = Scenario(title = "Ana", fontScale = 1f, time = "09:42", phone = "123")
        render()
        val title = rule.onNodeWithText("Ana", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = rule.onNodeWithText("09:42", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(time.left > title.right)
        assertReadable("123".forceLtr())
        capture("recent-compact.png")
    }

    @Test fun `unknown caller number and long localized date stay complete at 200 percent`() {
        settings.value = Scenario(
            width = 320, fontScale = 2f, title = "+44 7700 900000", phone = "+44 7700 900000",
            time = "Yesterday, 23:59", showNumber = false,
        )
        render()
        assertReadable(settings.value.title)
        assertReadable(settings.value.time)
        capture("recent-unknown-font-200.png")
    }

    @Test fun `app text size large avatar and RTL still reserve the full width for important data`() {
        settings.value = Scenario(
            width = 320, fontScale = 1.5f, interfaceScale = 1.2f, avatarSize = 52,
            nameSize = 22, direction = LayoutDirection.Rtl, time = "Ieri, 23:59",
        )
        render()
        assertReadable(settings.value.title)
        assertReadable(PHONE.forceLtr())
        assertReadable(settings.value.time)
        capture("recent-rtl-font-150-app-120.png")
    }

    @Test fun `contact rows without time also wrap long names and full numbers`() {
        settings.value = Scenario(width = 320, fontScale = 2f, contactRow = true)
        render()
        assertReadable(settings.value.title)
        assertReadable(PHONE)
        rule.onNodeWithText("Yesterday", useUnmergedTree = true).assertDoesNotExist()
        capture("contact-font-200-wrapped.png")
    }

    @Test fun `timestamp switches layout live when font size grows and shrinks`() {
        settings.value = Scenario(width = 320, fontScale = 1f, title = "Ana", time = "09:42")
        render()
        val initialTime = rule.onNodeWithText("09:42", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val initialName = rule.onNodeWithText("Ana", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(initialTime.left > initialName.right)
        rule.runOnUiThread { settings.value = settings.value.copy(fontScale = 2f) }
        rule.waitForIdle()
        assertReadable(PHONE.forceLtr())
        val number = rule.onNodeWithText(PHONE.forceLtr(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val expandedTime = rule.onNodeWithText("09:42", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(expandedTime.top >= number.bottom)
        rule.runOnUiThread { settings.value = settings.value.copy(fontScale = 1f) }
        rule.waitForIdle()
        val restoredTime = rule.onNodeWithText("09:42", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(restoredTime.left > initialName.right)
    }

    @Test fun `disabled expanded details keeps name number and time in the compact arrangement`() {
        settings.value = Scenario(width = 320, fontScale = 1.5f, time = "09:42", expandedDetails = false)
        render()
        assertEquals(1, textLayout(settings.value.title).lineCount)
        assertEquals(1, textLayout(PHONE.forceLtr()).lineCount)
        val number = rule.onNodeWithText(PHONE.forceLtr(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = rule.onNodeWithText("09:42", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(time.top < number.bottom)
        capture("recent-expanded-details-off.png")
        rule.runOnUiThread { settings.value = settings.value.copy(expandedDetails = true) }
        rule.waitForIdle()
        assertTrue(textLayout(settings.value.title).lineCount > 1)
        assertReadable(PHONE.forceLtr())
        capture("recent-expanded-details-on.png")
    }

    private fun render() {
        rule.setContent {
            val scenario = settings.value
            CompositionLocalProvider(
                LocalDensity provides Density(1f, scenario.fontScale),
                LocalLayoutDirection provides scenario.direction,
                LocalRillAvatarAppearance provides RillAvatarAppearance(false, true, false, true, false, false, false),
            ) {
                DotDialerTheme(systemDark = false, interfaceScale = scenario.interfaceScale) {
                    Box(Modifier.width(scenario.width.dp)) {
                        HomeResultRow(
                            title = scenario.title, subtitle = scenario.phone.takeIf { scenario.showNumber }, photoUri = null, phoneNumber = scenario.phone,
                            callVisual = CallLogVisual(CallLogVisualStatus.OUTGOING, false, false).takeUnless { scenario.contactRow },
                            callTime = scenario.time.takeUnless { scenario.contactRow },
                            swipeEnabled = false, swipeRightAction = 0, swipeLeftAction = 0,
                            haptics = false, hapticsStrength = "light", hapticsIntensity = 0.5f,
                            onAction = {}, onAvatarClick = {}, onClick = {}, isLastInSection = true,
                            contactNameSize = scenario.nameSize, avatarSize = scenario.avatarSize,
                            expandedListDetails = scenario.expandedDetails,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun assertReadable(text: String) {
        val result = textLayout(text)
        val diagnostic = "$text in ${settings.value}; size=${result.size}; paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}"
        assertFalse("Text ellipsized: $diagnostic", (0 until result.lineCount).any(result::isLineEllipsized))
        // Native text metrics can differ from integer layout bounds by a fractional pixel.
        val lastGlyph = result.getBoundingBox(text.lastIndex)
        assertTrue("Final character clipped horizontally: $diagnostic", lastGlyph.right <= result.size.width + 1f)
        assertTrue("Final character clipped vertically: $diagnostic", lastGlyph.bottom <= result.size.height + 1f)
    }

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(results) }
        return results.single()
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

    private data class Scenario(
        val width: Int = 360, val fontScale: Float = 1.5f, val title: String = "Test Alexandru Butica",
        val time: String = "Yesterday", val phone: String = PHONE,
        val interfaceScale: Float = 1f, val avatarSize: Int = 46, val nameSize: Int = 18,
        val showNumber: Boolean = true, val direction: LayoutDirection = LayoutDirection.Ltr,
        val contactRow: Boolean = false,
        val expandedDetails: Boolean = true,
    )
    private companion object { const val PHONE = "+40 700 000 000" }
}
