package dev.goodwy.rphone.controller

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.view.theme.DotDialerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallSurfaceFirstFrameTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightInitialFrameIsImmediateAndSwitchStillAnimates() = verifyFrames(false)
    @Test fun darkInitialFrameIsImmediateAndSwitchStillAnimates() = verifyFrames(true)

    private fun verifyFrames(isDark: Boolean) {
        val displayed = mutableStateOf<Call?>(null)
        val first = Shadow.newInstanceOf(Call::class.java)
        val second = Shadow.newInstanceOf(Call::class.java)
        var surface = Color.Unspecified
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DotDialerTheme(systemDark = isDark) {
                surface = MaterialTheme.colorScheme.surface
                Box(Modifier.fillMaxSize().background(Color.Green)) {
                    CallSurfaceTransition(displayed.value) { call ->
                        if (call != null) Box(Modifier.fillMaxSize().background(if (call === first) Color.Blue else Color.Red))
                    }
                }
            }
        }
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            assertEquals(surface.toArgb(), capture("${if (isDark) "dark" else "light"}-waiting"))
            rule.runOnIdle { displayed.value = first }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            assertNotEquals(Color.Green.toArgb(), capture("${if (isDark) "dark" else "light"}-update-frame"))
            // One frame applies composition state, the next presents its resulting layout.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            assertEquals(Color.Blue.toArgb(), capture("${if (isDark) "dark" else "light"}-first-call-frame"))
            rule.runOnIdle { displayed.value = second }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            assertNotEquals(Color.Red.toArgb(), capture("${if (isDark) "dark" else "light"}-switch-first-frame"))
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
            assertEquals(Color.Red.toArgb(), capture("${if (isDark) "dark" else "light"}-switch-complete"))
    }

    private fun capture(name: String): Int {
        var center = 0
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/call-handoff/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return center
    }
}
