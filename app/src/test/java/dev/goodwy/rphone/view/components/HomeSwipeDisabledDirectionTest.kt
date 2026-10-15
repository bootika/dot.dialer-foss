package dev.goodwy.rphone.view.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.theme.DotDialerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class HomeSwipeDisabledDirectionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val actions = mutableListOf<Int>()

    @Test fun `LTR no action stays still during rightward drag while left swipe remains functional`() {
        render(right = 0, left = 1)
        dragAndAssertStill(160f)
        swipe(-160f)
        assertEquals(listOf(1), actions)
    }

    @Test fun `LTR no action stays still during leftward drag while right swipe remains functional`() {
        render(right = 1, left = 0)
        dragAndAssertStill(-160f)
        swipe(160f)
        assertEquals(listOf(1), actions)
    }

    @Test fun `RTL disabled logical right stays still and logical left remains functional`() {
        render(right = 0, left = 1, rtl = true)
        dragAndAssertStill(-160f)
        swipe(160f)
        assertEquals(listOf(1), actions)
    }

    @Test fun `both directions disabled never move or invoke callbacks`() {
        render(right = 0, left = 0)
        dragAndAssertStill(160f)
        dragAndAssertStill(-160f)
    }

    @Test fun `legacy video action cannot move or call even if passed directly to the component`() {
        render(right = PreferenceManager.SWIPE_ACTION_VIDEO_CALL, left = 0)
        dragAndAssertStill(160f)
    }

    @Test fun `offset policy clamps allowed movements and blocks disabled directions including crossing zero`() {
        assertEquals(85f, homeSwipeOffset(0f, 500f, 85f, true, false, false))
        assertEquals(0f, homeSwipeOffset(30f, -100f, 85f, true, false, false))
        assertEquals(-85f, homeSwipeOffset(0f, -500f, 85f, true, false, true))
        assertEquals(0f, homeSwipeOffset(-30f, 100f, 85f, true, false, true))
    }

    private fun render(right: Int, left: Int, rtl: Boolean = false) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                DotDialerTheme(systemDark = false) {
                    SwipeableHomeContainer(
                        enabled = true, swipeRightAction = right, swipeLeftAction = left,
                        haptics = false, hapticsStrength = "light", hapticsIntensity = 0.5f,
                        onAction = { actions += it }, modifier = Modifier.testTag("gesture"),
                    ) {
                        Box(Modifier.fillMaxWidth().height(72.dp).testTag("foreground")) {
                            Text("Test Alexandru Butica")
                        }
                    }
                }
            }
        }
    }

    private fun dragAndAssertStill(dx: Float) {
        val before = rule.onNodeWithTag("foreground").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("gesture").performTouchInput {
            down(center)
            moveBy(Offset(dx, 0f))
        }
        rule.waitForIdle()
        assertEquals(before, rule.onNodeWithTag("foreground").fetchSemanticsNode().boundsInRoot)
        assertEquals(emptyList<Int>(), actions)
        rule.onNodeWithTag("gesture").performTouchInput { up() }
        rule.waitForIdle()
    }

    private fun swipe(dx: Float) {
        rule.onNodeWithTag("gesture").performTouchInput {
            down(center)
            moveBy(Offset(dx, 0f))
            up()
        }
        rule.waitForIdle()
    }
}
