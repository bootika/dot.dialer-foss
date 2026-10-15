package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.theme.DotDialerTheme
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class DialpadKeyFontFallbackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun dependencies() {
        val prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        prefs.setBoolean(PreferenceManager.KEY_APP_HAPTICS, false)
        prefs.setBoolean(PreferenceManager.KEY_DTMF_TONE, false)
        startKoin { modules(module { single { prefs } }) }
    }
    @After fun cleanup() { stopKoin() }

    private val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI",
        "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
        "*" to "", "0" to "+", "#" to "")
    private var dark by mutableStateOf(false)
    private var font by mutableFloatStateOf(1f)
    private var compact by mutableStateOf(false)
    private var narrow by mutableStateOf(false)
    private var old by mutableStateOf(false)
    private var interfaceScale by mutableFloatStateOf(1f)
    private val events = mutableListOf<String>()
    private val factor get() = if (narrow) 0.86f else 1f
    private val width get() = (if (compact) 82f else 108f) * factor
    private val height get() = (if (compact) 52f else 56f) * factor

    private fun host() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark, interfaceScale = interfaceScale) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                keys.chunked(3).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { (digit, letters) ->
                                            if (old) Legacy(digit, letters) else DialPadKey(digit, letters,
                                                { events += "tap:$it" }, { events += "long:$it" },
                                                Modifier.testTag("key-$digit"), compact, width.dp, height.dp, factor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Frozen pre-D64 visual reference, deliberately independent of the new label component.
    @Composable private fun Legacy(digit: String, letters: String) {
        Surface(shape = RoundedCornerShape(42.dp), color = dialpadKeyColor,
            modifier = Modifier.size(width.dp, height.dp)) {
            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Text(digit, style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = ((if (compact) 18f else 28f) * factor).sp), fontWeight = FontWeight.Medium)
                if (letters.isNotBlank()) Text(letters,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = (10f * factor).sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            }
        }
    }

    private fun bitmap(): Bitmap {
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        lateinit var result: Bitmap
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            result = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(result))
        }
        return result
    }
    private fun save(name: String, image: Bitmap) {
        val dir = File(System.getProperty("user.dir"), "build/reports/visual/dialpad-font-fallback/${BuildConfig.FLAVOR}")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `small font enlargement is pixel identical to pre fallback layout`() {
        host()
        for (theme in listOf(false, true)) for (small in listOf(1f, 1.15f, 1.2f)) for (landscape in listOf(false, true)) for (zoom in listOf(0.9f, 1f, 1.2f)) {
            rule.runOnIdle { dark = theme; font = small; compact = landscape; interfaceScale = zoom; old = true }
            val before = bitmap()
            rule.runOnIdle { old = false }
            val after = bitmap()
            assertTrue("Pixel parity font=$small compact=$landscape dark=$theme", before.sameAs(after))
            if (zoom == 1f) save("${if (theme) "dark" else "light"}-font${(small * 100).toInt()}-${if (landscape) "compact" else "portrait"}", after)
        }
    }

    @Test fun `extreme font keeps every label inside actual unchanged key bounds and actions intact`() {
        host()
        for (theme in listOf(false, true)) for (large in listOf(1.5f, 2f, 2.5f)) for (mode in 0..2) for (zoom in listOf(0.9f, 1f, 1.2f)) {
            rule.runOnIdle { dark = theme; font = large; compact = mode == 1; narrow = mode == 2; interfaceScale = zoom }
            val image = bitmap()
            if (zoom == 1f) save("${if (theme) "dark" else "light"}-font${(large * 100).toInt()}-mode$mode", image)
            for ((digit, letters) in keys) {
                val key = rule.onNodeWithTag("key-$digit").fetchSemanticsNode().boundsInRoot
                assertEquals(width, key.width, 0.51f); assertEquals(height, key.height, 0.51f)
                for (text in listOf(digit, letters).filter { it.isNotBlank() }) {
                    val node = rule.onNodeWithText(text, useUnmergedTree = true)
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    assertTrue("$text horizontal $large mode$mode", bounds.left >= key.left && bounds.right <= key.right)
                    assertTrue("$text vertical $large mode$mode", bounds.top >= key.top && bounds.bottom <= key.bottom)
                    val results = mutableListOf<TextLayoutResult>()
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                    val layout = results.single()
                    assertFalse("$text overflow font=$large mode=$mode zoom=$zoom size=${layout.size} paragraph=${layout.multiParagraph.height} widthOverflow=${layout.didOverflowWidth} heightOverflow=${layout.didOverflowHeight} bounds=$bounds key=$key", layout.hasVisualOverflow)
                    assertEquals(1, results.single().lineCount)
                }
            }
            val baseline = keys.map { (digit, _) -> rule.onNodeWithText(digit, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top }
            baseline.chunked(3).forEach { row -> assertEquals(row.first(), row.last(), 0.51f) }
        }
        keys.forEach { (digit, _) -> rule.onNodeWithTag("key-$digit").performClick() }
        assertEquals(keys.map { "tap:${it.first}" }, events)
        events.clear()
        for (digit in listOf("0", "*", "#", "2")) {
            rule.onNodeWithTag("key-$digit").performTouchInput { longClick() }
        }
        assertEquals(listOf("long:+", "long:,", "long:;", "long:2"), events)
    }

    @Test fun `fallback is conditional on fit and effective not raw system scale`() {
        var fontScale by mutableFloatStateOf(2.5f)
        var ignore by mutableStateOf(false)
        var result: DialpadKeyTextLayout? = null
        var room by mutableFloatStateOf(200f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                DotDialerTheme(ignoreSystemFontScale = ignore) {
                    val legacy = DialpadKeyTextLayout(MaterialTheme.typography.headlineMedium,
                        MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp))
                    result = fitDialpadKeyText(legacy, LocalDensity.current, rememberTextMeasurer(), 200.dp, room.dp, false, 1f)
                }
            }
        }
        rule.runOnIdle { assertFalse(result!!.fitted); room = 56f }
        rule.runOnIdle { assertTrue(result!!.fitted); ignore = true }
        rule.runOnIdle { assertFalse(result!!.fitted); ignore = false; fontScale = 1.2f }
        rule.runOnIdle { assertFalse(result!!.fitted) }
    }
}
