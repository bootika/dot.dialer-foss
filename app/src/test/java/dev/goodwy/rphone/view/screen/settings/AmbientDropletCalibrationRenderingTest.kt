package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.util.AmbientDropletPreference
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.CallAmbientDroplets
import dev.goodwy.rphone.view.screen.ambientDropletRenderConfig
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class AmbientDropletCalibrationRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `real opacity control preserves old settings persists full opacity and keeps off state`() {
        val context = RuntimeEnvironment.getApplication()
        var prefs by mutableStateOf(PreferenceManager(context))
        var version by mutableIntStateOf(0)
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        fun restore(stored: Int) {
            runBlocking { prefs.restoreAllPreferences(mapOf(AmbientDropletPreference.BRIGHTNESS_KEY to stored,
                AmbientDropletPreference.ENABLED_KEY to true)) }
            rule.waitUntil(5000) { prefs.getInt(AmbientDropletPreference.BRIGHTNESS_KEY, -1) == stored &&
                prefs.getBoolean(AmbientDropletPreference.ENABLED_KEY, false) }
            rule.runOnIdle { version++ }
            rule.mainClock.advanceTimeBy(300)
        }
        restore(100)
        // Infinite animations are suspended by the test clock's auto-advance policy.
        // Drive real animation frames so opacity changes are actually rendered.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark) {
                    Surface(Modifier.fillMaxSize()) {
                        Column {
                            AmbientDropletSettingsControls(prefs, version)
                            val config = remember(version) { prefs.ambientDropletRenderConfig() }
                            Box(Modifier.fillMaxWidth().weight(1f)
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .background(MaterialTheme.colorScheme.primaryContainer)) {
                                if (config.enabled) CallAmbientDroplets(hasAvatar = false, brightnessPercent = config.brightnessPercent)
                            }
                        }
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            restore(100)
            rule.runOnIdle { dark = theme; font = scale }
            rule.mainClock.advanceTimeBy(300)
            rule.onNodeWithText("50%").assertExists()
            val slider = rule.onNodeWithTag("ambient-opacity-slider")
            val info = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(50f, info.current, 0.001f)
            assertEquals(5f..100f, info.range)
            assertEquals(100, prefs.ambientDropletRenderConfig().brightnessPercent)
            capture("${if(theme) "dark" else "light"}-font${(scale*100).toInt()}-50")
            slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(100f)) }
            rule.waitUntil(5000) { prefs.getInt(AmbientDropletPreference.BRIGHTNESS_KEY, -1) == 200 }
            rule.runOnIdle { prefs = PreferenceManager(context); version++ }
            rule.mainClock.advanceTimeBy(12_000)
            assertEquals(100f, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f)
            assertEquals(200, prefs.ambientDropletRenderConfig().brightnessPercent)
            capture("${if(theme) "dark" else "light"}-font${(scale*100).toInt()}-100")
            rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).performClick()
            rule.waitUntil(5000) { !prefs.getBoolean(AmbientDropletPreference.ENABLED_KEY, true) }
            rule.runOnIdle { version++ }
            rule.mainClock.advanceTimeBy(300)
            slider.assertIsNotEnabled()
            assertEquals(200, prefs.getInt(AmbientDropletPreference.BRIGHTNESS_KEY, -1))
            assertFalse(prefs.ambientDropletRenderConfig().enabled)
            capture("${if(theme) "dark" else "light"}-font${(scale*100).toInt()}-off")
            restore(80)
            restore(80) // Repeated old backup/read must not halve the value again.
            rule.onNodeWithText("40%").assertExists()
            assertEquals(80, prefs.ambientDropletRenderConfig().brightnessPercent)
            capture("${if(theme) "dark" else "light"}-font${(scale*100).toInt()}-restore40")
        }
    }

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File(System.getProperty("user.dir"), "build/reports/visual/droplet-opacity/" + BuildConfig.FLAVOR)
            folder.mkdirs()
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
