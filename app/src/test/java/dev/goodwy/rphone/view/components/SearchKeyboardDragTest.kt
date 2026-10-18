package dev.goodwy.rphone.view.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.theme.DotDialerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
class SearchKeyboardDragTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var hides = 0
    private var deletes = 0
    private lateinit var state: LazyListState
    private lateinit var scope: CoroutineScope
    private val keyboard = object : SoftwareKeyboardController {
        override fun show() = Unit
        override fun hide() { hides++ }
    }
    @Test fun `up and down user drags dismiss keyboard without changing list data`() {
        render()
        rule.onNodeWithTag("results").performTouchInput { swipeUp() }
        rule.runOnIdle { assertTrue(hides > 0) }
        rule.runOnIdle { hides = 0 }
        rule.onNodeWithTag("results").performTouchInput { swipeDown() }
        rule.runOnIdle { assertTrue(hides > 0); assertEquals(40, state.layoutInfo.totalItemsCount); assertEquals(0, deletes) }
    }
    @Test fun `programmatic scroll and horizontal gesture do not dismiss keyboard`() {
        render()
        rule.runOnIdle { scope.launch { state.scrollToItem(10) } }
        rule.waitForIdle()
        rule.onNodeWithTag("results").performTouchInput { swipeLeft() }
        rule.runOnIdle { assertEquals(0, hides); assertEquals(1, deletes) }
    }
    @Test fun `downward drag at top still dismisses keyboard`() {
        render()
        rule.onNodeWithTag("results").performTouchInput { swipeDown() }
        rule.runOnIdle { assertTrue(hides > 0) }
    }
    private fun render() {
        rule.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                DotDialerTheme(systemDark = false) {
                    state = rememberLazyListState(); scope = rememberCoroutineScope()
                    HideKeyboardOnListDrag(state)
                    LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(300.dp).testTag("results")) {
                        items(40) { index ->
                            SwipeableHomeContainer(true, 8, 8, false, "light", 0.5f,
                                onAction = { deletes++ }) {
                                Text("Test Alexandru Butica $index", Modifier.fillMaxWidth().height(60.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
