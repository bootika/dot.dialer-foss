package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.settings.components.SettingsHomeContent
import dev.goodwy.rphone.view.screen.settings.components.SettingsImmediateSection
import dev.goodwy.rphone.view.theme.DotDialerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsImmediateScrollTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() = stopKoin()

    @Test fun sectionRemainsOpaqueOnFirstFrameAndReentry() {
        val generation = mutableIntStateOf(0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DotDialerTheme {
                Box(Modifier.fillMaxSize().background(Color.Green)) {
                    key(generation.intValue) { SettingsImmediateSection(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.Blue))
                    } }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        assertEquals(Color.Blue.toArgb(), capture("immediate-initial").getPixel(180, 300))
        for (index in 1..4) {
            rule.runOnIdle { generation.intValue = index }
            rule.mainClock.advanceTimeByFrame(); rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
            assertEquals(Color.Blue.toArgb(), capture("immediate-reentry-$index").getPixel(180, 300))
        }
    }

    @Test fun realSettingsFastJumpsHaveSamePixelsImmediatelyAndSettledWithAnimationOn() = verifyHome(false, 1f)
    @Test fun darkLargeFontSettingsFastJumpsHaveNoDelayedReveal() = verifyHome(true, 1.5f)

    private fun verifyHome(dark: Boolean, font: Float) {
        val prefs = PreferenceManager(rule.activity.applicationContext)
        runBlocking { prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_SCROLL_ANIMATION to true)) }
        startKoin { modules(module { single { prefs } }) }
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, method, _ ->
            if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        } as DestinationsNavigator
        lateinit var list: LazyListState
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        list = rememberLazyListState()
                        SettingsHomeContent(list, PaddingValues(), "", {}, emptyList(), navigator, "2.0.3-test", "Local", {}, {}, {})
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        for (index in listOf(7, 2, 8, 3, 7)) {
            rule.runOnIdle { runBlocking { list.scrollToItem(index) } }
            rule.mainClock.advanceTimeByFrame(); rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
            val first = capture("${if(dark) "dark" else "light"}-jump-$index-first")
            rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
            val settled = capture("${if(dark) "dark" else "light"}-jump-$index-settled")
            assertTrue("Delayed item reveal at index$index", first.sameAs(settled))
            first.recycle(); settled.recycle()
        }
        assertTrue(prefs.getBoolean(PreferenceManager.KEY_SCROLL_ANIMATION, false))
    }

    private fun capture(name: String): Bitmap {
        lateinit var bitmap: Bitmap
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/settings-immediate/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return bitmap
    }
}
