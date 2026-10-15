package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.screen.onboarding.MorphingOnboardingScreen
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
class EssentialOnboardingRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun restrictedSettingsButtonRemainsReachableWithLargeFonts() {
        var dark by mutableStateOf(false)
        var clicks = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                DotDialerTheme(systemDark = dark) { RestrictedSetupHelp { clicks++ } }
            }
        }
        for (mode in listOf(false, true)) {
            rule.runOnIdle { dark = mode }
            rule.onNodeWithText(rule.activity.getString(R.string.open_app_settings))
                .assertIsDisplayed().performClick()
            capture("restricted-${if (mode) "dark" else "light"}-150")
        }
        assertEquals(2, clicks)
    }
    @Test fun essentialPageDoesNotMoveAsTimePasses() {
        rule.mainClock.autoAdvance = false
        rule.setContent { DotDialerTheme(systemDark = false) { MorphingOnboardingScreen {} } }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        fun pixels(): IntArray {
            var result = intArrayOf()
            rule.runOnIdle {
                val view = rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                result = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(result, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }
            return result
        }
        val initial = pixels()
        rule.mainClock.advanceTimeBy(3000)
        assertArrayEquals(initial, pixels())
    }
    @Test fun singleEssentialPageKeepsStartReachableInAllStyles() {
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var completed = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                DotDialerTheme(systemDark = dark) { MorphingOnboardingScreen { completed++ } }
            }
        }
        for (mode in listOf(false, true)) for (font in listOf(1f, 1.5f)) {
            rule.runOnIdle { dark = mode; scale = font }
            rule.mainClock.advanceTimeBy(1000)
            rule.onNodeWithText(rule.activity.getString(R.string.onboarding_start)).assertIsDisplayed().performClick()
            rule.onNodeWithText(rule.activity.getString(R.string.onboarding_essential_title)).assertExists()
            capture("intro-${if (mode) "dark" else "light"}-${(font * 100).toInt()}")
        }
        assertEquals(4, completed)
    }
    @Test
    @Config(qualifiers = "w800dp-h360dp-land-mdpi")
    fun landscapeLargeFontKeepsStartReachable() {
        var completed = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                DotDialerTheme(systemDark = false) { MorphingOnboardingScreen { completed = true } }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText(rule.activity.getString(R.string.onboarding_start)).assertIsDisplayed().performClick()
        assertTrue(completed)
        capture("intro-landscape-150")
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File(System.getProperty("user.dir"), "build/reports/visual/essential-setup")
            directory.mkdirs()
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
