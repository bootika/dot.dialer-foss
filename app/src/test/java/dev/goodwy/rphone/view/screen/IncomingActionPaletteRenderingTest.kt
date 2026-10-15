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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.theme.CallActionColors
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
class IncomingActionPaletteRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val scene = mutableStateOf(0 to 0)
    private var answers = 0
    private var rejects = 0

    @Test fun `all production answer modes share idle and committed pixels and retain their callbacks`() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    key(scene.value) {
                        val answer = { answers++; Unit }
                        val reject = { rejects++; Unit }
                        when (scene.value.first) {
                            0 -> IncomingCallButtons(false, false, 20f, true, answer, reject)
                            1 -> DualActionHorizontalSlider(false, false, 20f, true, answer, reject)
                            2 -> SamsungRadialAnswerButtons(true, answer, reject)
                            3 -> DefaultSwipeToAnswer(false, false, 20f, true, 20.dp, 0.dp, answer, reject)
                            4 -> IPhoneSwipeToAnswer(false, false, 20f, answer, reject, {})
                            5 -> HorizontalSwipeToAnswer(false, false, 20f, true, answer, reject)
                            6 -> VerticalSwipeToAnswer(false, false, 20f, true, answer, reject)
                        }
                    }
                }
            }
        }
        for (mode in 0..6) for (answer in listOf(true, false)) {
            rule.runOnIdle { scene.value = mode to if (answer) 1 else 2; answers = 0; rejects = 0 }
            rule.mainClock.advanceTimeBy(500)
            val action = if (answer) CallActionColors.answerCommitted else CallActionColors.rejectCommitted
            val idle = if (answer) CallActionColors.answerIdle else CallActionColors.rejectIdle
            val central = mode >= 5
            if (!central) assertPixels(idle, "mode-$mode-${if (answer) "answer" else "reject"}-idle")
            val gesture = central || (answer && mode in 3..4) || mode in 1..2
            val node = if (central || (answer && mode in 3..4)) rule.onNodeWithTag("callAnswerHandle")
                else rule.onNodeWithContentDescription(if (answer) "Answer" else "Decline", useUnmergedTree = true)
            node.performTouchInput { down(center) }
            if (gesture) {
                node.performTouchInput {
                    val delta = when {
                        mode == 6 -> Offset(0f, if (answer) -160f else 160f)
                        mode == 5 -> Offset(if (answer) -240f else 240f, 0f)
                        mode == 1 -> Offset(if (answer) 240f else -240f, 0f)
                        else -> Offset(260f, 0f)
                    }
                    moveBy(delta / 2f, delayMillis = 100)
                    moveBy(delta / 2f, delayMillis = 100)
                }
            }
            rule.mainClock.advanceTimeBy(600)
            assertPixels(action, "mode-$mode-${if (answer) "answer" else "reject"}-committed")
            node.performTouchInput { up() }
            rule.mainClock.advanceTimeBy(600)
            rule.runOnIdle {
                assertEquals(if (answer) 1 else 0, answers)
                assertEquals(if (answer) 0 else 1, rejects)
            }
        }
    }

    private fun assertPixels(color: Color, name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val expected = color.toArgb()
            val count = pixels.count { pixel ->
                abs(AndroidColor.red(pixel) - AndroidColor.red(expected)) <= 2 &&
                    abs(AndroidColor.green(pixel) - AndroidColor.green(expected)) <= 2 &&
                    abs(AndroidColor.blue(pixel) - AndroidColor.blue(expected)) <= 2
            }
            val file = File(System.getProperty("user.dir"), "build/reports/visual/incoming-action-palette/$name.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            assertTrue("$name: expected opaque shared palette, found $count pixels", count > 300)
        }
    }
}
