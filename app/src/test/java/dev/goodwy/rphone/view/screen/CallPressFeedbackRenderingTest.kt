package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.view.components.CallControlIcons
import dev.goodwy.rphone.view.components.CallPressScaleKey
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class CallPressFeedbackRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `fixed answer decline resume and neutral controls compress five percent without changing touch area`() {
        var mode by mutableIntStateOf(0)
        var dark by mutableStateOf(false)
        var disabled by mutableStateOf(false)
        var answers = 0
        var rejects = 0
        rule.setContent {
            DotDialerTheme(systemDark = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    key(mode, dark, disabled) {
                        when(mode) {
                            0 -> IncomingCallButtons(false, false, 20f, true, { answers++ }, { rejects++ })
                            1 -> DefaultSwipeToAnswer(false, false, 20f, true, 20.dp, 0.dp, { answers++ }, { rejects++ })
                            2 -> IPhoneSwipeToAnswer(false, false, 20f, { answers++ }, { rejects++ }, {})
                            3 -> CallScreenActionButton(CallControlIcons.MicOff, "Mute", enabled = !disabled) { answers++ }
                            else -> CallScreenActionButton(CallControlIcons.Resume, "Resume", isResumeAction = true) { answers++ }
                        }
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (variant in 0..4) {
            rule.runOnIdle { dark = theme; mode = variant; answers = 0; rejects = 0 }
            rule.mainClock.advanceTimeBy(300)
            val labels = if (variant == 0) listOf("Answer", "Decline") else listOf(when(variant) { 3 -> "Mute"; 4 -> "Resume"; else -> "Decline" })
            for (label in labels) {
                val node = rule.onNode(hasTestTag("callRoundActionSurface") and hasAnyDescendant(hasContentDescription(label)), true)
                val before = node.fetchSemanticsNode().boundsInRoot
                assertEquals(1f, node.fetchSemanticsNode().config[CallPressScaleKey], 0.001f)
                node.performTouchInput { down(center) }
                rule.mainClock.advanceTimeBy(200)
                assertEquals(0.95f, node.fetchSemanticsNode().config[CallPressScaleKey], 0.001f)
                assertEquals(before, node.fetchSemanticsNode().boundsInRoot)
                capture("${if(theme) "dark" else "light"}-$variant-$label-pressed")
                node.performTouchInput { cancel() }
                rule.mainClock.advanceTimeBy(200)
                assertEquals(1f, node.fetchSemanticsNode().config[CallPressScaleKey], 0.001f)
                assertEquals(0, answers); assertEquals(0, rejects)
                // The unscaled edge stays clickable even though the painted circle contracts.
                node.performTouchInput { down(Offset(width / 2f, 1f)) }
                rule.mainClock.advanceTimeBy(200)
                node.performTouchInput { up() }
                rule.mainClock.advanceTimeBy(200)
                if (label == "Decline") { assertEquals(1, rejects); rejects = 0 }
                else { assertEquals(1, answers); answers = 0 }
            }
        }
        rule.runOnIdle { mode = 3; disabled = true }
        rule.mainClock.advanceTimeBy(300)
        val node = rule.onNodeWithTag("callRoundActionSurface", true)
        node.assertIsNotEnabled()
        node.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(200)
        assertEquals(1f, node.fetchSemanticsNode().config[CallPressScaleKey], 0.001f)
        node.performTouchInput { cancel() }
        assertEquals(0, answers)
    }

    private fun capture(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val folder = File(System.getProperty("user.dir"), "build/reports/visual/call-press/" + BuildConfig.FLAVOR)
        folder.mkdirs()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
