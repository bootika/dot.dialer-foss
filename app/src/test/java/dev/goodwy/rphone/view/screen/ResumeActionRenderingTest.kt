package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.goodwy.rphone.view.components.CallControlIcons
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import dev.goodwy.rphone.view.theme.CallActionColors
import dev.goodwy.rphone.view.theme.DotAccent
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import kotlin.math.abs
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
class ResumeActionRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val scene = mutableStateOf(false to DotAccent.Jade)
    private var commits = 0

    @Test fun `resume keeps shared jade and white idle pressed cancelled and committed in every theme accent`() {
        rule.setContent {
            DotDialerTheme(systemDark = scene.value.first, accent = scene.value.second) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    key(scene.value) {
                        CallScreenActionButton(CallControlIcons.Resume, "Resume", isResumeAction = true) { commits++ }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (accent in DotAccent.entries) {
            rule.runOnIdle { scene.value = dark to accent; commits = 0 }
            rule.mainClock.advanceTimeBy(500)
            val prefix = "${if (dark) "dark" else "light"}-${accent.key}"
            assertPixels(CallActionColors.answerIdle, "$prefix-idle")
            val node = rule.onNodeWithTag("callRoundActionSurface", useUnmergedTree = true)
            node.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(500)
            assertPixels(CallActionColors.answerCommitted, "$prefix-pressed")
            node.performTouchInput { cancel() }
            rule.mainClock.advanceTimeBy(500)
            assertPixels(CallActionColors.answerIdle, "$prefix-cancelled")
            rule.runOnIdle { assertEquals(0, commits) }
            node.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(500)
            node.performTouchInput { up() }
            rule.mainClock.advanceTimeBy(500)
            rule.runOnIdle { assertEquals(1, commits) }
        }
    }

    @Test fun `resume is the same brand handset and optical profile not a playback triangle`() {
        assertSame(DotDialerHandsetIcon, CallControlIcons.Resume)
        assertEquals(callActionIconSize(DotDialerHandsetIcon), callActionIconSize(CallControlIcons.Resume))
    }

    private fun assertPixels(background: Color, name: String) {
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("callRoundActionSurface", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            fun matches(actual: Int, expected: Int) =
                abs(AndroidColor.red(actual) - AndroidColor.red(expected)) <= 2 &&
                abs(AndroidColor.green(actual) - AndroidColor.green(expected)) <= 2 &&
                abs(AndroidColor.blue(actual) - AndroidColor.blue(expected)) <= 2
            var white = 0
            var fill = 0
            for (y in bounds.top.toInt() until bounds.bottom.toInt()) for (x in bounds.left.toInt() until bounds.right.toInt()) {
                val pixel = bitmap.getPixel(x, y)
                if (matches(pixel, CallActionColors.onAction.toArgb())) white++
                if (matches(pixel, background.toArgb())) fill++
            }
            val output = File(System.getProperty("user.dir"), "build/reports/visual/resume-action/$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            assertTrue("$name must paint a white handset, actual white=$white", white > 60)
            assertTrue("$name must paint the shared action fill, actual fill=$fill", fill > 1000)
        }
    }
}
