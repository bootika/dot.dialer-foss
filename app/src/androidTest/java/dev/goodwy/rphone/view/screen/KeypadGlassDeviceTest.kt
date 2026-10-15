package dev.goodwy.rphone.view.screen

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.view.screen.settings.InterfaceScreen
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class KeypadGlassDeviceTest {
    private fun find(node: SemanticsNode, predicate: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (predicate(node)) node else node.children.firstNotNullOfOrNull { find(it, predicate) }
    private fun composeView(view: View): View? = when {
        view.javaClass.name.endsWith("AndroidComposeView") -> view
        view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { composeView(view.getChildAt(it)) }
        else -> null
    }
    private fun root(view: View): SemanticsNode =
        (view.javaClass.methods.first { it.name.startsWith("getSemanticsOwner") }.invoke(view) as SemanticsOwner).rootSemanticsNode

    @Test fun noPreviewDiscreteSliderAndGpuChangesOnlyTintAlpha() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "dot.dialer.glassqa.test") { "Never alter user's app" }
        val prefs = PreferenceManager(context)
        runBlocking { prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_LG_DIALPAD to false,
            PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH to 30, PreferenceManager.KEY_BLUR_INTENSITY to 20)) }
        startKoin { modules(module { single { prefs } }) }
        val dark = mutableStateOf(false); val cardScene = mutableStateOf(false)
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, method, _ ->
            if (method.returnType == Boolean::class.javaPrimitiveType) false else null
        } as DestinationsNavigator
        try { ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                DotDialerTheme(systemDark = dark.value) {
                    if (!cardScene.value) InterfaceScreen(navigator) else {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                            Box(Modifier.fillMaxSize().padding(24.dp), androidx.compose.ui.Alignment.Center) {
                                DialpadGlassTestCard(rememberDialpadEffectState(prefs))
                            }
                        }
                    }
                }
            } }
            fun <T> withRoot(block: (View, SemanticsNode) -> T): T {
                var result: T? = null
                scenario.onActivity { activity -> val view = checkNotNull(composeView(activity.window.decorView)); result = block(view, root(view)) }
                @Suppress("UNCHECKED_CAST") return result as T
            }
            fun waitFor(condition: () -> Boolean) {
                val deadline = SystemClock.uptimeMillis() + 8_000
                while (!condition()) { check(SystemClock.uptimeMillis() < deadline); SystemClock.sleep(50) }
                SystemClock.sleep(300)
            }
            waitFor { withRoot { _, tree -> find(tree) { it.config.contains(SemanticsProperties.Text) } != null } }
            assertTrue(withRoot { view, _ -> view.isHardwareAccelerated })
            withRoot { _, tree ->
                val glass = checkNotNull(find(tree) { it.config.contains(SemanticsProperties.Text) &&
                    it.config[SemanticsProperties.Text].any { text -> text.text == context.getString(R.string.liquid_glass_dialpad) } &&
                    it.config.contains(SemanticsActions.OnClick) })
                checkNotNull(glass.config[SemanticsActions.OnClick].action).invoke()
            }
            waitFor { withRoot { _, tree -> find(tree) { it.config.contains(SemanticsActions.SetProgress) } != null } }
            assertNull(withRoot { _, tree -> find(tree) { it.config.contains(SemanticsProperties.Text) &&
                it.config[SemanticsProperties.Text].any { text -> text.text == context.getString(R.string.keypad_effect_preview) } } })
            val dir = File(context.cacheDir, "dialpad-alpha-qa").apply { mkdirs() }
            for (mode in listOf(false, true)) {
                scenario.onActivity { dark.value = mode; cardScene.value = false }
                waitFor { withRoot { _, tree -> find(tree) { it.config.contains(SemanticsActions.SetProgress) } != null } }
                for (percent in 0..100 step 10) {
                    withRoot { _, tree ->
                        val slider = checkNotNull(find(tree) { it.config.contains(SemanticsActions.SetProgress) })
                        assertEquals(9, slider.config[SemanticsProperties.ProgressBarRangeInfo].steps)
                        checkNotNull(slider.config[SemanticsActions.SetProgress].action).invoke(percent.toFloat())
                    }
                    waitFor { prefs.dialpadGlassStrengthPercent() == percent }
                }
                withRoot { _, tree -> checkNotNull(find(tree) { it.config.contains(SemanticsActions.SetProgress) })
                    .config[SemanticsActions.SetProgress].action?.invoke(30f) }
                waitFor { prefs.dialpadGlassStrengthPercent() == 30 }
                checkNotNull(instrumentation.uiAutomation.takeScreenshot()).save(File(dir, "settings-${if (mode) "dark" else "light"}.png"))
                scenario.onActivity { cardScene.value = true }
                waitFor { withRoot { _, tree -> find(tree) { it.config.getOrElse(SemanticsProperties.TestTag) { "" } == "dialpad-qa-card" } != null } }
                val samples = mutableListOf<Bitmap>()
                for (percent in 0..100 step 10) {
                    runBlocking { prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH to percent)) }
                    waitFor { prefs.dialpadGlassStrengthPercent() == percent }
                    val full = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                    val bounds = withRoot { view, tree ->
                        val card = checkNotNull(find(tree) { it.config.getOrElse(SemanticsProperties.TestTag) { "" } == "dialpad-qa-card" })
                        val origin = IntArray(2).also { view.getLocationOnScreen(it) }
                        card.boundsInRoot.translate(origin[0].toFloat(), origin[1].toFloat())
                    }
                    val bitmap = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
                    samples += bitmap; bitmap.save(File(dir, "card-${if (mode) "dark" else "light"}-$percent.png"))
                }
                assertFalse(samples.first().sameAs(samples.last()))
                var informative = 0; var bad = 0
                val first = samples.first(); val last = samples.last()
                for (y in 12 until first.height - 12 step 19) for (x in 12 until first.width - 12 step 19) {
                    val start = first.getPixel(x, y); val end = last.getPixel(x, y)
                    if (maxOf(abs(Color.red(start) - Color.red(end)), abs(Color.green(start) - Color.green(end)), abs(Color.blue(start) - Color.blue(end))) < 12) continue
                    informative++
                    for (i in 1..9) {
                        val value = samples[i].getPixel(x, y)
                        val error = listOf(Color.red(start) to Color.red(end), Color.green(start) to Color.green(end), Color.blue(start) to Color.blue(end))
                            .zip(listOf(Color.red(value), Color.green(value), Color.blue(value)))
                            .maxOf { (pair, actual) -> abs(actual - (pair.first + (pair.second - pair.first) * i / 10f)) }
                        if (error > 5f) bad++
                    }
                }
                assertTrue("Enough backdrop pixels", informative > 20)
                assertTrue("Alpha-only GPU interpolation: $bad/$informative", bad <= informative / 10)
            }
            runBlocking { prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH to 30)) }
            assertEquals(30, PreferenceManager(context).dialpadGlassStrengthPercent())
        } } finally { stopKoin() }
    }
    private fun Bitmap.save(file: File) = file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
}
