package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.R
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.dialpadGlassStrengthPercent
import dev.goodwy.rphone.view.screen.dialpadEffectFill
import dev.goodwy.rphone.view.screen.rememberDialpadEffectState
import dev.goodwy.rphone.view.screen.settings.components.settingsOptions
import dev.goodwy.rphone.view.screen.settings.components.SettingsEditor
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.core.preferences.DialpadGlassPolicy
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class KeypadGlassSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var prefs: PreferenceManager
    @Before fun setup() {
        prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        runBlocking { prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_LG_DIALPAD to false,
            PreferenceManager.KEY_BLUR_DIALPAD to false, PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH to 30)) }
        startKoin { modules(module { single { prefs } }) }
    }
    @After fun cleanup() { stopKoin() }

    private fun slider() = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
    private fun setPercent(value: Float) {
        slider().performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(value)) }
        rule.waitUntil(5_000) { prefs.dialpadGlassStrengthPercent() == DialpadGlassPolicy.normalizeStrength(value.toInt()) }
    }

    @Test fun `discrete slider same family as droplets no preview persists hides and reopens`() {
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var session by mutableIntStateOf(0)
        var observedStrength = -1
        var observedGlass = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark) {
                    val realConsumer = rememberDialpadEffectState(prefs)
                    observedStrength = realConsumer.strength; observedGlass = realConsumer.glass
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            key(session) { KeypadGlassSettings(prefs) }
                        }
                    }
                }
            }
        }
        val glass = rule.activity.getString(R.string.liquid_glass_dialpad)
        val blur = rule.activity.getString(R.string.blur_effect_dialpad)
        val label = rule.activity.getString(R.string.keypad_effect_strength)
        rule.onNodeWithText(label).assertDoesNotExist()
        rule.onNodeWithText(glass).performClick()
        rule.waitUntil(5_000) { observedGlass }
        rule.onNodeWithText(label).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.keypad_effect_preview)).assertDoesNotExist()
        assertEquals(9, slider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].steps)
        assertEquals(30, observedStrength)
        for (value in (0..100 step 10).map(Int::toFloat) + 47f) {
            slider().performScrollTo(); setPercent(value)
            rule.waitUntil(5_000) { observedStrength == DialpadGlassPolicy.normalizeStrength(value.toInt()) }
        }
        rule.runOnIdle { session++ }
        rule.waitForIdle(); assertEquals(50, PreferenceManager(rule.activity).dialpadGlassStrengthPercent())
        rule.onNodeWithText(glass).performScrollTo().performClick()
        rule.waitUntil(5_000) { !observedGlass }
        rule.onNodeWithText(label).assertDoesNotExist()
        rule.onNodeWithText(blur).assertDoesNotExist()
        prefs.setBoolean(PreferenceManager.KEY_BLUR_DIALPAD, true)
        rule.waitUntil(5_000) { prefs.getBoolean(PreferenceManager.KEY_BLUR_DIALPAD, false) }
        rule.onNodeWithText(label).assertDoesNotExist()
        rule.onNodeWithText(glass).performClick()
        rule.waitUntil(5_000) { observedGlass }
        rule.onNodeWithText(label).assertExists()
        assertEquals(50, prefs.dialpadGlassStrengthPercent())
        for (mode in listOf(false, true)) for (scale in listOf(1f, 1.5f, 2.5f)) {
            rule.runOnIdle { dark = mode; font = scale }
            slider().performScrollTo(); rule.waitForIdle()
            val dir = File(System.getProperty("user.dir"), "build/reports/visual/keypad-glass-settings/${BuildConfig.FLAVOR}").apply { mkdirs() }
            rule.runOnIdle {
                val view = rule.activity.window.decorView
                val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
                File(dir, "${if (mode) "dark" else "light"}-font${(scale * 100).toInt()}.png")
                    .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun `restore malformed values clamped portable and global switches untouched`() = runBlocking {
        for ((raw, normalized) in listOf(-50 to 0, 160 to 100, "invalid" to 30, true to 30)) {
            prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH to raw,
                PreferenceManager.KEY_LIQUID_GLASS to false, PreferenceManager.KEY_BLUR_EFFECTS to false))
            rule.waitUntil(5_000) { prefs.getAllPreferences()[PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH] == raw }
            assertEquals(normalized, PreferenceManager(rule.activity).dialpadGlassStrengthPercent())
            assertFalse(prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, true))
            assertFalse(prefs.getBoolean(PreferenceManager.KEY_BLUR_EFFECTS, true))
        }
        assertEquals(SettingsEditor.INTERFACE, settingsOptions.single { it.id == "A09" }.editor)
        assertFalse(settingsOptions.single { it.id == "A09" }.advanced)
        assertEquals(SettingsEditor.LEGACY, settingsOptions.single { it.id == "A08" }.editor)
        assertFalse(settingsOptions.single { it.id == "A08" }.isAvailable("gplay", true))
    }

    @Test fun `glass tint RGB is constant and only alpha changes including opaque zero`() {
        val normal = Color(0xFFE2E3DC)
        val target = Color.White.copy(alpha = 0.35f)
        assertEquals(target.copy(alpha = 1f), dialpadEffectFill(normal, target, DialpadGlassPolicy.effect(true, 0, true, true)))
        assertEquals(target, dialpadEffectFill(normal, target, DialpadGlassPolicy.effect(true, 100, true, true)))
        assertEquals(0.805f, dialpadEffectFill(normal, target, DialpadGlassPolicy.effect(true, 30, true, true)).alpha, 0.002f)
        for (percent in 0..100 step 10) {
            val fill = dialpadEffectFill(normal, target, DialpadGlassPolicy.effect(true, percent, true, true))
            assertEquals(target.red, fill.red, 0f); assertEquals(target.green, fill.green, 0f); assertEquals(target.blue, fill.blue, 0f)
        }
        assertEquals(normal, dialpadEffectFill(normal, target, DialpadGlassPolicy.effect(false, 100, true, true)))
    }
}
