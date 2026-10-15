package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.util.AmbientDropletPreference
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
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
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class EndCallWidthPaletteRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() { stopKoin() }

    @Test fun `End width is Berry not primary while values reset and droplets remain intact`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        val off = MutableStateFlow(false)
        val helper = Proxy.newProxyInstance(PurchaseHelper::class.java.classLoader,
            arrayOf(PurchaseHelper::class.java)) { _, method, _ ->
            when (method.name) {
                "getIsPro", "isPro" -> off
                "hashCode" -> 1
                "toString" -> "Test purchase helper"
                else -> null
            }
        } as PurchaseHelper
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, method, _ ->
            if (method.returnType == Boolean::class.javaPrimitiveType) false else null
        } as DestinationsNavigator
        startKoin { modules(module { single { prefs }; single<PurchaseHelper> { helper } }) }
        prefs.setInt(AmbientDropletPreference.BRIGHTNESS_KEY, 140)
        prefs.setBoolean(AmbientDropletPreference.ENABLED_KEY, true)
        prefs.setFloat(PreferenceManager.KEY_HANGUP_WIDTH, 0.4f)
        prefs.setString(PreferenceManager.KEY_THEME_MODE, "auto")
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var generation by mutableIntStateOf(0)
        var advanced by mutableStateOf(true)
        val visualContext = object : ContextWrapper(rule.activity) {
            override fun getDisplay(): Display = rule.activity.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)
        }
        rule.setContent {
            CompositionLocalProvider(LocalContext provides visualContext, LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark) { key(generation) { CallerUIScreen(navigator, advanced = advanced) } }
            }
        }
        fun scrollTo(tag: String) {
            rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
            rule.mainClock.advanceTimeBy(1200)
            rule.waitForIdle()
        }
        for (theme in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            prefs.setFloat(PreferenceManager.KEY_HANGUP_WIDTH, 0.4f)
            rule.waitUntil(5000) { prefs.getFloat(PreferenceManager.KEY_HANGUP_WIDTH, -1f) == 0.4f }
            rule.runOnIdle { dark = theme; font = scale; generation++ }
            scrollTo("end-call-width-section")
            val label = rule.onNodeWithTag("end-call-width-value")
            label.assertTextEquals("40%")
            val name = "${if (theme) "dark" else "light"}-font${(scale * 100).toInt()}"
            val end = capture("end-call-width-section", "$name-end")
            assertEquals("Primary green leaked into End card: $name", 0, greenPixels(end))
            val slider = rule.onNodeWithTag("end-call-width-slider")
            val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(0.1f..1f, range.range)
            assertEquals(8, range.steps)
            slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.8f)) }
            rule.waitUntil(5000) { prefs.getFloat(PreferenceManager.KEY_HANGUP_WIDTH, -1f) == 0.8f }
            assertEquals(0.8f, PreferenceManager(context).getFloat(PreferenceManager.KEY_HANGUP_WIDTH, -1f), 0.001f)
            rule.runOnIdle { generation++ }
            scrollTo("end-call-width-section")
            label.assertTextEquals("80%").performClick()
            rule.waitUntil(5000) { prefs.getFloat(PreferenceManager.KEY_HANGUP_WIDTH, -1f) == 0.4f }
            label.assertTextEquals("40%")
            assertEquals(140, prefs.getInt(AmbientDropletPreference.BRIGHTNESS_KEY, -1))
            rule.runOnIdle { advanced = false; generation++ }
            scrollTo("ambient-opacity-slider")
            rule.onNodeWithText("70%").assertExists()
            val droplets = capture("ambient-opacity-slider", "$name-droplets")
            assertTrue("Droplets lost primary color: $name", greenPixels(droplets) > 10)
            rule.runOnIdle { advanced = true; generation++ }
        }
    }

    private fun capture(tag: String, name: String): Bitmap {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        var cropped: Bitmap? = null
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val folder = File(System.getProperty("user.dir"), "build/reports/visual/end-width-berry/${BuildConfig.FLAVOR}")
            folder.mkdirs()
            File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cropped = Bitmap.createBitmap(image, bounds.left.toInt(), bounds.top.toInt(),
                bounds.width.toInt(), bounds.height.toInt())
        }
        return requireNotNull(cropped)
    }

    private fun greenPixels(image: Bitmap): Int {
        var count = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val pixel = image.getPixel(x, y)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            if (g > r + 20 && g > b + 10) count++
        }
        return count
    }
}
