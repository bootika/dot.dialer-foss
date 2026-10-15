package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.components.CallControlIcons
import dev.goodwy.rphone.view.components.CallPressFillAlphaKey
import dev.goodwy.rphone.view.components.CallPressScaleKey
import dev.goodwy.rphone.view.components.LocalCallTapHapticsEnabled
import dev.goodwy.rphone.view.components.DotDialerMergeIcon
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallTapFeedbackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun transparentControlsPreserveBoundsShowPressAndNeverDispatchCancelOrDisabled() {
        var secondary by mutableStateOf(false)
        var dark by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var calls = 0
        val states = mutableListOf<String>()
        val events = mutableListOf<Int>()
        val view = object : View(rule.activity) {
            override fun performHapticFeedback(feedbackConstant: Int): Boolean { events += feedbackConstant; return true }
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalView provides view) {
                DotDialerTheme(systemDark = dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        if (secondary) SecondaryCallAction(DotDialerMergeIcon, "Merge",
                            Modifier.width(140.dp), enabled = enabled, testTag = "merge") { states += "$enabled@${rule.mainClock.currentTime}"; calls++ }
                        else CallScreenActionButton(CallControlIcons.MicOff, "Mute", enabled = enabled) { states += "$enabled@${rule.mainClock.currentTime}"; calls++ }
                    }
                }
            }
        }
        rule.runOnIdle { (rule.activity.window.decorView as android.view.ViewGroup).addView(view, android.view.ViewGroup.LayoutParams(0, 0)) }
        for (theme in listOf(false, true)) for (merge in listOf(false, true)) {
            rule.runOnIdle { dark = theme; secondary = merge; enabled = true; calls = 0; events.clear(); Snapshot.sendApplyNotifications() }
            rule.mainClock.advanceTimeBy(300)
            val node = rule.onNodeWithTag(if (merge) "merge" else "callRoundActionSurface", true)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val idle = node.fetchSemanticsNode().config[CallPressFillAlphaKey]
            node.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(200)
            assertEquals(.95f, node.fetchSemanticsNode().config[CallPressScaleKey], .001f)
            assertEquals(idle + .14f, node.fetchSemanticsNode().config[CallPressFillAlphaKey], 1f / 255f)
            assertEquals(bounds, node.fetchSemanticsNode().boundsInRoot)
            capture("${if (theme) "dark" else "light"}-${if (merge) "merge" else "neutral"}-pressed")
            node.performTouchInput { cancel() }
            rule.mainClock.advanceTimeBy(300)
            assertEquals(1f, node.fetchSemanticsNode().config[CallPressScaleKey], .001f)
            assertEquals(idle, node.fetchSemanticsNode().config[CallPressFillAlphaKey], 1f / 255f)
            assertEquals("callbacks=$states", 0, calls); assertTrue(events.isEmpty())
            node.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(200)
            assertEquals("no action before disabling", 0, calls)
            rule.runOnIdle { enabled = false; Snapshot.sendApplyNotifications() }
            assertEquals("no action while updating enabled", 0, calls)
            rule.mainClock.advanceTimeBy(200)
            assertEquals("no action before pointer up", 0, calls)
            rule.waitForIdle()
            node.assertIsNotEnabled()
            node.performTouchInput { up() }
            rule.mainClock.advanceTimeBy(300)
            node.assertIsNotEnabled()
            assertEquals(1f, node.fetchSemanticsNode().config[CallPressScaleKey], .001f)
            assertEquals(idle, node.fetchSemanticsNode().config[CallPressFillAlphaKey], 1f / 255f)
            assertEquals("callbacks=$states", 0, calls); assertTrue(events.isEmpty())
        }
    }

    @Test fun quickTapAcknowledgesWithoutDelayingActionAndHapticRespectsAppAndViewGates() {
        var appHaptics by mutableStateOf(true)
        var calls = 0
        val events = mutableListOf<Int>()
        val view = object : View(rule.activity) {
            override fun performHapticFeedback(feedbackConstant: Int): Boolean { events += feedbackConstant; return true }
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalView provides view, LocalCallTapHapticsEnabled provides appHaptics) {
                DotDialerTheme(systemDark = true) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SecondaryCallAction(DotDialerMergeIcon, "Merge", Modifier.width(140.dp), testTag = "merge") { calls++ }
                    }
                }
            }
        }
        rule.runOnIdle { (rule.activity.window.decorView as android.view.ViewGroup).addView(view, android.view.ViewGroup.LayoutParams(0, 0)) }
        rule.mainClock.advanceTimeBy(300)
        val node = rule.onNodeWithTag("merge", true)
        node.performClick()
        assertEquals(1, calls)
        assertEquals(listOf(HapticFeedbackConstants.KEYBOARD_TAP), events)
        rule.mainClock.advanceTimeBy(100)
        assertEquals(.95f, node.fetchSemanticsNode().config[CallPressScaleKey], .001f)
        rule.mainClock.advanceTimeBy(300)
        assertEquals(1f, node.fetchSemanticsNode().config[CallPressScaleKey], .001f)
        rule.runOnIdle { appHaptics = false; events.clear() }
        rule.mainClock.advanceTimeByFrame()
        node.performClick()
        assertEquals(2, calls); assertTrue(events.isEmpty())
        rule.runOnIdle { appHaptics = true; view.isHapticFeedbackEnabled = false }
        rule.mainClock.advanceTimeByFrame()
        node.performClick()
        assertEquals(3, calls); assertTrue(events.isEmpty())
    }

    private fun capture(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val folder = File("build/reports/visual/call-tap-d80")
        folder.mkdirs()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
