package dev.goodwy.rphone.view.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.settings.SystemFontScaleSetting
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
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
@Config(sdk = [36], application = Application::class)
class SystemFontScalePreferenceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun stopDependencies() { stopKoin() }

    @Test fun `density policy preserves original Android converter by default and never alters display density`() {
        listOf(0.85f, 1f, 1.15f, 1.5f, 2f).forEach { scale ->
            val system = Density(3f, scale)
            assertSame(system, appTextDensity(system, false))
            assertEquals(3f, appTextDensity(system, true).density, 0.001f)
            assertEquals(1f, appTextDensity(system, true).fontScale, 0.001f)
        }
    }

    @Test fun `saved off preference changes live text restores Android scale and persists`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        kotlinx.coroutines.runBlocking {
            prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_IGNORE_SYSTEM_FONT_SCALE to false), replacePortable = true)
        }
        assertEquals(false, prefs.getBoolean(PreferenceManager.KEY_IGNORE_SYSTEM_FONT_SCALE, true))
        assertEquals(true, PreferenceManager.DEFAULT_IGNORE_SYSTEM_FONT_SCALE)
        startKoin { modules(module { single { prefs } }) }
        var androidScale by mutableFloatStateOf(2f)
        var effectiveScale = 0f
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, androidScale)) {
                DotDialerAppTheme {
                    effectiveScale = LocalDensity.current.fontScale
                    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
                    val checked = remember(version) {
                        prefs.getBoolean(PreferenceManager.KEY_IGNORE_SYSTEM_FONT_SCALE, false)
                    }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Box(Modifier.size(48.dp).testTag("control"))
                        Text("Test Alexandru Butica", fontSize = 20.sp, modifier = Modifier.testTag("contact"))
                        Text("HD+ Voice", style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("quality"))
                        SystemFontScaleSetting(
                            checked = checked,
                            onCheckedChange = {
                                prefs.setBoolean(PreferenceManager.KEY_IGNORE_SYSTEM_FONT_SCALE, it)
                            },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNode(isToggleable()).assertIsOff()
        val scaledText = rule.onNodeWithTag("contact").fetchSemanticsNode().boundsInRoot
        val control = rule.onNodeWithTag("control").fetchSemanticsNode().boundsInRoot.size
        capture("system-font-200-off.png")
        rule.onNode(isToggleable()).performClick()
        rule.waitUntil(5_000) { effectiveScale == 1f }
        rule.onNode(isToggleable()).assertIsOn()
        val standardText = rule.onNodeWithTag("contact").fetchSemanticsNode().boundsInRoot
        assertTrue(standardText.height < scaledText.height)
        assertEquals(control, rule.onNodeWithTag("control").fetchSemanticsNode().boundsInRoot.size)
        assertEquals(true, PreferenceManager(context).getBoolean(PreferenceManager.KEY_IGNORE_SYSTEM_FONT_SCALE, false))
        capture("system-font-200-on.png")
        rule.runOnUiThread { androidScale = 1.5f }
        rule.runOnIdle { assertEquals(1f, effectiveScale, 0.001f) }
        assertEquals(standardText.size, rule.onNodeWithTag("contact").fetchSemanticsNode().boundsInRoot.size)
        rule.onNode(isToggleable()).performClick()
        rule.waitUntil(5_000) { effectiveScale == 1.5f }
        rule.onNode(isToggleable()).assertIsOff()
        assertEquals(false, PreferenceManager(context).getBoolean(PreferenceManager.KEY_IGNORE_SYSTEM_FONT_SCALE, true))
        capture("system-font-150-restored.png")
    }

    @Test fun `ignoring Android font scale leaves app interface scale and explicit sp text available`() {
        var ignore by androidx.compose.runtime.mutableStateOf(false)
        var observedScale = 0f
        var bodySize = 0f
        var density = 0f
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 2f)) {
                DotDialerTheme(systemDark = false, interfaceScale = 1.2f, ignoreSystemFontScale = ignore) {
                    observedScale = LocalDensity.current.fontScale
                    density = LocalDensity.current.density
                    bodySize = MaterialTheme.typography.bodyLarge.fontSize.value
                    Text("Test Alexandru Butica")
                }
            }
        }
        rule.runOnIdle {
            assertEquals(2f, observedScale, 0.001f)
            assertEquals(19.2f, bodySize, 0.001f)
        }
        rule.runOnUiThread { ignore = true }
        rule.runOnIdle {
            assertEquals(1f, observedScale, 0.001f)
            assertEquals(2f, density, 0.001f)
            assertEquals(19.2f, bodySize, 0.001f)
        }
    }

    private fun capture(name: String) {
        val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/$name")
        output.parentFile!!.mkdirs()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }
}
