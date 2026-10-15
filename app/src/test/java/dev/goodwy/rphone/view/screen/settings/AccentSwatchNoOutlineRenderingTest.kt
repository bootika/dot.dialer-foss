package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.view.theme.DotDialerTheme
import dev.goodwy.rphone.view.theme.DotAccent
import dev.goodwy.rphone.view.theme.DotInk
import dev.goodwy.rphone.view.theme.resolveAccent
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
class AccentSwatchNoOutlineRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `circles keep full color at edge and selector and popup keep selection behavior`() {
        val choices = listOf("Jade" to "jade", "Blue" to "blue", "Electric Iris" to "violet", "Ruby" to "coral")
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var selected by mutableStateOf("jade")
        var commits = 0
        var renderedPrimary: androidx.compose.ui.graphics.Color? = null
        var renderedOnPrimary: androidx.compose.ui.graphics.Color? = null
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark, accent = resolveAccent(selected)) {
                    val colors = MaterialTheme.colorScheme
                    SideEffect { renderedPrimary = colors.primary; renderedOnPrimary = colors.onPrimary }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Column {
                            DotAccentColorChoiceRow("Accent color", choices, selected, { selected = it; commits++ })
                            DotVisualChoiceMenuRows(choices, selected, { selected = it; commits++ },
                                { key, checked -> AccentSwatch(accentPreviewColor(key), checked) },
                                Modifier.testTag("native-menu"))
                            for (checked in listOf(false, true)) Row(
                                Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                for ((_, key) in choices) Box(Modifier.testTag("circle-$key-$checked")) {
                                    AccentSwatch(accentPreviewColor(key), checked)
                                }
                            }
                        }
                    }
                }
            }
        }
        for (mode in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            rule.runOnIdle { dark = mode; font = scale }
            for ((label, key) in choices) {
                rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag("native-menu"))).performClick()
                rule.onNodeWithTag("accent-color-selector").assert(hasText(label))
                rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag("native-menu"))).assertIsSelected()
                if (resolveAccent(key) == DotAccent.Blue) rule.runOnIdle {
                    assertEquals(if (mode) androidx.compose.ui.graphics.Color(0xFFAFC6FF)
                        else androidx.compose.ui.graphics.Color(0xFF4285F4), renderedPrimary)
                    assertEquals(if (mode) androidx.compose.ui.graphics.Color(0xFF0A241B)
                        else DotInk, renderedOnPrimary)
                }
                val bitmap = capture("${if(mode) "dark" else "light"}-font${(scale*100).toInt()}-$key")
                for (checked in listOf(false, true)) for ((_, circleKey) in choices) {
                    val bounds = rule.onNodeWithTag("circle-$circleKey-$checked").fetchSemanticsNode().boundsInRoot
                    assertEquals(34f, bounds.width, 0.001f)
                    assertEquals(34f, bounds.height, 0.001f)
                    val x = bounds.center.x.toInt()
                    val y = bounds.top.toInt() + 1
                    assertEquals("No theme-colored ring at top edge of $circleKey/$checked",
                        accentPreviewColor(circleKey).toArgb(), bitmap.getPixel(x, y))
                }
            }
            rule.onNodeWithTag("accent-color-selector").performClick()
            rule.onNodeWithTag("accent-color-selector-menu").assertExists()
            rule.onNode(hasText("Blue") and hasAnyAncestor(hasTestTag("accent-color-selector-menu"))).performClick()
            rule.onNodeWithTag("accent-color-selector-menu").assertDoesNotExist()
            rule.onNodeWithTag("accent-color-selector").assert(hasText("Blue"))
        }
        assertEquals(20, commits)
    }

    private fun capture(name: String): Bitmap {
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        var result: Bitmap? = null
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File(System.getProperty("user.dir"), "build/reports/visual/accent-no-outline/" + BuildConfig.FLAVOR)
            folder.mkdirs()
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            result = bitmap
        }
        return requireNotNull(result)
    }
}
