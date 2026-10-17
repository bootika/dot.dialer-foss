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
class CallConferenceMistRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val sources = mutableStateOf(listOf(CallMistSource(1, null, Color.Red)))
    private val dark = mutableStateOf(false)

    @Test fun `adding removing changing and failing participant photos updates the rendered field`() {
        val blue = fixture("blue", AndroidColor.rgb(40, 90, 170))
        // The old muted green settled exactly at G-B=12, passing >12 only during fallback.
        val green = fixture("green", AndroidColor.rgb(120, 210, 40))
        rule.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.fillMaxSize().background(callMistSurface(MaterialTheme.colorScheme.surface))) {
                    CallConferenceMist(sources.value)
                }
            }
        }
        waitFor("01-one-circle", .50f, .41f) { AndroidColor.red(it) > AndroidColor.blue(it) + 15 }
        capture("01-one-circle")
        rule.runOnIdle { sources.value = listOf(CallMistSource(1, null, Color.Red), CallMistSource(2, blue, Color.Green)) }
        waitFor("02-photo-added", .80f, .41f) { AndroidColor.blue(it) > AndroidColor.red(it) + 12 }
        capture("02-photo-added")
        // A blue fallback cannot satisfy the green predicate: the photo must actually load.
        rule.runOnIdle { sources.value = listOf(CallMistSource(2, green, Color.Blue)) }
        waitFor("03-removed-and-photo-changed", .50f, .41f) { AndroidColor.green(it) > AndroidColor.blue(it) + 12 }
        capture("03-removed-and-photo-changed")
        rule.runOnIdle { sources.value = listOf(CallMistSource(2, "file:///missing-participant-photo.jpg", Color.Magenta)) }
        waitFor("04-failed-photo-circle", .50f, .41f) { AndroidColor.red(it) > AndroidColor.green(it) + 12 }
        capture("04-failed-photo-circle")
        rule.runOnIdle { dark.value = true }
        waitFor("05-dark", .50f, .41f) { AndroidColor.red(it) > AndroidColor.green(it) + 12 && AndroidColor.red(it) < 180 }
        capture("05-dark")
        rule.runOnIdle { sources.value = emptyList() }
        waitFor("06-empty", .50f, .41f) { kotlin.math.abs(AndroidColor.red(it) - 20) <= 1 && kotlin.math.abs(AndroidColor.green(it) - 24) <= 1 }
        capture("06-empty")
    }

    private fun waitFor(stage: String, x: Float, y: Float, predicate: (Int) -> Boolean) {
        try {
            awaitColour(x, y, predicate)
        } catch (failure: Throwable) {
            runCatching { capture("$stage-failed") }.exceptionOrNull()?.let(failure::addSuppressed)
            failure.addSuppressed(AssertionError("Conference transition: $stage at ($x, $y)"))
            throw failure
        }
    }

    private fun awaitColour(x: Float, y: Float, predicate: (Int) -> Boolean) {
        rule.waitUntil(5_000) {
            rule.mainClock.advanceTimeBy(200)
            var matches = false
            rule.runOnIdle {
                val bitmap = screenshot()
                matches = predicate(bitmap.getPixel((bitmap.width * x).toInt(), (bitmap.height * y).toInt()))
                bitmap.recycle()
            }
            matches
        }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.runOnIdle {
            val bitmap = screenshot()
            val settled = bitmap.getPixel((bitmap.width * x).toInt(), (bitmap.height * y).toInt())
            assertTrue("Settled colour must satisfy the same predicate: RGB=" +
                "${AndroidColor.red(settled)},${AndroidColor.green(settled)},${AndroidColor.blue(settled)}", predicate(settled))
            val a = bitmap.getPixel(bitmap.width - 1, 0)
            val b = bitmap.getPixel(bitmap.width / 2, (bitmap.height * .96f).toInt())
            for (channel in listOf(AndroidColor::red, AndroidColor::green, AndroidColor::blue)) {
                assertTrue("Navigation stays quiet", kotlin.math.abs(channel(a) - channel(b)) <= 2)
            }
            bitmap.recycle()
        }
    }

    private fun fixture(name: String, color: Int): String {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File(rule.activity.cacheDir, "participant-$name.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun screenshot(): Bitmap {
        val view = rule.activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun capture(name: String) {
        val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/conference-avatar-mist").apply { mkdirs() }
        rule.runOnIdle {
            val bitmap = screenshot()
            File(output, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
