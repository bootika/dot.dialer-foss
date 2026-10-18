package dev.goodwy.rphone.view.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import dev.goodwy.rphone.view.screen.performHomeAction
import dev.goodwy.rphone.view.theme.DotDialerTheme
import androidx.compose.ui.unit.dp
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
class HistorySwipeDeleteTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val deleted = mutableListOf<Long>()
    private var calls = 0
    private var contacts = 0

    @Test fun `right swipe deletes one displayed history ID immediately`() {
        render()
        rule.onNodeWithTag("row").performTouchInput { swipeRight() }
        rule.runOnIdle { assertEquals(listOf(42L), deleted); assertEquals(0, calls); assertEquals(0, contacts) }
    }
    @Test fun `left swipe deletes one displayed history ID immediately`() {
        render()
        rule.onNodeWithTag("row").performTouchInput { swipeLeft() }
        rule.runOnIdle { assertEquals(listOf(42L), deleted) }
    }
    @Test fun `delete dispatch without an explicit target never falls through to contact or call`() {
        performHomeAction(8, rule.activity, "", { calls++ }, { contacts++ })
        assertEquals(emptyList<Long>(), deleted); assertEquals(0, contacts); assertEquals(0, calls)
    }
    private fun render() {
        rule.setContent {
            DotDialerTheme(systemDark = false) {
                SwipeableHomeContainer(true, 8, 8,
                    false, "light", 0.5f, onAction = { action ->
                        performHomeAction(action, rule.activity, "", { calls++ }, { contacts++ },
                            onDeleteEntry = { deleted += 42L })
                    }, modifier = Modifier.testTag("row")) {
                    Box(Modifier.fillMaxWidth().height(80.dp)) { Text("Test Alexandru Butica") }
                }
            }
        }
    }
}
