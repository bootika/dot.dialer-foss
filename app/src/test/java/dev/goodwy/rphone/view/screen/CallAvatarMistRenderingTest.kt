package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class CallAvatarMistRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val uri = mutableStateOf<String?>(null)
    private val dark = mutableStateOf(true)
    private val fallback = Color(0xFFE57373)

    @Test fun `field follows the current photo and failed or missing photos return to the circle colour`() {
        val blue = fixture("blue", AndroidColor.rgb(40, 90, 170))
        val green = fixture("green", AndroidColor.rgb(60, 155, 75))
        rule.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.fillMaxSize().background(callMistSurface(MaterialTheme.colorScheme.surface))) {
                    CallAvatarMist(uri.value, fallback)
                }
            }
        }
        waitFor { pixel -> AndroidColor.red(pixel) > AndroidColor.green(pixel) + 10 }
        assertCoolCounterpoint()
        capture("01-initials-dark")
        rule.runOnIdle { uri.value = blue }
        waitFor { pixel -> AndroidColor.blue(pixel) > AndroidColor.red(pixel) + 10 }
        capture("02-blue-photo-dark")
        rule.runOnIdle { uri.value = green }
        waitFor { pixel -> AndroidColor.green(pixel) > AndroidColor.blue(pixel) + 10 }
        capture("03-green-photo-dark")
        rule.runOnIdle { uri.value = "file:///missing-contact-photo.jpg" }
        waitFor { pixel -> AndroidColor.red(pixel) > AndroidColor.green(pixel) + 10 }
        assertCoolCounterpoint()
        capture("04-failed-photo-fallback")
        rule.runOnIdle { uri.value = null; dark.value = false }
        waitFor { pixel -> AndroidColor.red(pixel) > AndroidColor.green(pixel) + 10 }
        assertCoolCounterpoint()
        capture("05-initials-light")
        val grey = fixture("grey", AndroidColor.GRAY)
        rule.runOnIdle { uri.value = grey }
        waitFor { pixel -> AndroidColor.green(pixel) > AndroidColor.red(pixel) + 10 }
        capture("06-grey-photo-light")
        rule.runOnIdle { dark.value = true }
        waitFor { pixel -> AndroidColor.green(pixel) > AndroidColor.red(pixel) + 10 }
        capture("07-grey-photo-dark")
    }

    private fun waitFor(predicate: (Int) -> Boolean) {
        rule.waitUntil(5_000) {
            rule.mainClock.advanceTimeBy(200)
            var matches = false
            rule.runOnIdle {
                val bitmap = screenshot()
                // Identity-colour region; the left region is deliberately a counterpoint now.
                matches = predicate(bitmap.getPixel((bitmap.width * .36f).toInt(), (bitmap.height * .20f).toInt()))
                bitmap.recycle()
            }
            matches
        }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        // All coloured ellipses end before the navigation region, in both themes.
        rule.runOnIdle {
            val bitmap = screenshot()
            val topCorner = bitmap.getPixel(bitmap.width - 1, 0)
            val bottom = bitmap.getPixel(bitmap.width / 2, (bitmap.height * .96f).toInt())
            for (channel in listOf(AndroidColor::red, AndroidColor::green, AndroidColor::blue)) {
                assertTrue("Navigation area must stay quiet", kotlin.math.abs(channel(topCorner) - channel(bottom)) <= 2)
            }
            bitmap.recycle()
        }
    }

    private fun assertCoolCounterpoint() {
        rule.runOnIdle {
            val bitmap = screenshot()
            val pixel = bitmap.getPixel((bitmap.width * .06f).toInt(), (bitmap.height * .46f).toInt())
            assertTrue("Pink identity must have a visible cool counterpoint", AndroidColor.green(pixel) > AndroidColor.red(pixel) + 8 && AndroidColor.blue(pixel) > AndroidColor.red(pixel) + 8)
            bitmap.recycle()
        }
    }

    private fun fixture(name: String, color: Int): String {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File(rule.activity.cacheDir, "$name-portrait.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun screenshot(): Bitmap {
        val view = rule.activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun capture(name: String) {
        val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/call-avatar-mist").apply { mkdirs() }
        rule.runOnIdle {
            val bitmap = screenshot()
            File(output, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
