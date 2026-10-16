package dev.goodwy.rphone.view.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import dev.goodwy.rphone.view.screen.SecondaryCallAction
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.callConnectionMetadataTextStyle
import dev.goodwy.rphone.view.theme.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "en-w360dp-h800dp-mdpi")
class ControlTypographyRenderingTest(private val fontScale: Float) {
    companion object {
        @JvmStatic @Parameters(name = "Android font {0}")
        fun fontScales() = listOf(arrayOf(1f), arrayOf(1.5f), arrayOf(2f))
    }
    val rule = createAndroidComposeRule<ComponentActivity>()
    // Configure Android before the Activity starts. Changing it afterwards recreates
    // ComponentActivity and would discard the fixture's setContent composition.
    private val androidFontConfiguration = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                RuntimeEnvironment.setFontScale(fontScale)
                try { base.evaluate() } finally { RuntimeEnvironment.setFontScale(1f) }
            }
        }
    }
    @get:Rule val rules: TestRule = RuleChain.outerRule(androidFontConfiguration).around(rule)
    @After fun stop() { stopKoin() }

    @Test fun `controls use medium while explanations stay regular across themes and scaling`() {
        val prefs = PreferenceManager(RuntimeEnvironment.getApplication())
        runBlocking { prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_LIQUID_GLASS to false)) }
        startKoin { modules(module { single { prefs } }) }
        var scene by mutableIntStateOf(0)
        var dark by mutableStateOf(false)
        val font = fontScale
        var interfaceScale by mutableFloatStateOf(1f)
        var picked = ""
        var applied = 0
        var opened = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                DotDialerTheme(systemDark = dark, interfaceScale = interfaceScale) {
                    // Verify the actual themed roles, including the legacy emphasized aliases.
                    assertEquals(FontWeight.Medium, MaterialTheme.typography.labelLarge.fontWeight)
                    assertEquals(FontWeight.Medium, MaterialTheme.typography.labelLargeEmphasized.fontWeight)
                    assertEquals(ManropeFontFamily, MaterialTheme.typography.labelLargeEmphasized.fontFamily)
                    assertEquals(FontWeight.SemiBold, MaterialTheme.typography.titleLargeEmphasized.fontWeight)
                    assertEquals(FontWeight.Normal, callConnectionMetadataTextStyle().fontWeight)
                    assertEquals(16f * interfaceScale, MaterialTheme.typography.controlLabel.fontSize.value, .01f)
                    assertEquals(16f * interfaceScale, MaterialTheme.typography.bodyLarge.fontSize.value, .01f)
                    key(scene, dark, font, interfaceScale) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                RillSectionHeader("Contact preferences")
                                RillListItem(headline = "Contact options", supporting = "Secondary explanation", onClick = { opened++ })
                                SecondaryCallAction(icon = DotDialerHangupIcon, label = "End secondary call", testTag = "controlSecondaryEnd", onClick = {})
                                Button(onClick = { applied++ }) { Text("Apply changes") }
                                Button(enabled = false, onClick = { error("Disabled action") }) { Text("Unavailable") }
                            }
                            when (scene) {
                                1 -> RillSelectionDialog(onDismissRequest = { scene = 0 }, title = "Choose an option",
                                    items = listOf("First option", "Second option"), itemLabel = { it },
                                    itemSupporting = { "Secondary explanation" }, isSelected = { it == "First option" },
                                    onItemSelected = { picked = it })
                                2 -> RillConfirmationDialog(onDismissRequest = { scene = 0 }, onConfirm = { applied++; scene = 0 },
                                    title = "Apply contact changes", message = "Keep the secondary explanation easy to distinguish.",
                                    confirmLabel = "Apply selection")
                            }
                        }
                    }
                }
            }
        }
        fun weight(text: String, expected: FontWeight) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(expected, layouts.single().layoutInput.style.fontWeight)
            assertEquals("Real window font scale: $text", font, layouts.single().layoutInput.density.fontScale, .01f)
            assertFalse("Visible control text must not be ellipsized: $text, dark=$dark, font=$font", layouts.single().isLineEllipsized(0))
            for (line in 1 until layouts.single().lineCount) {
                assertFalse("Visible control text must not be ellipsized: $text", layouts.single().isLineEllipsized(line))
            }
        }
        fun capture(name: String, dialog: Boolean = false) {
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(700)
            rule.runOnIdle {
                val view = if (dialog) requireNotNull(ShadowDialog.getLatestDialog()).window!!.decorView else rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val out = File(System.getProperty("user.dir"), "build/reports/visual/control-typography/$name.png")
                out.parentFile!!.mkdirs()
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        for (theme in listOf(false, true)) {
            val scale = fontScale
            rule.runOnIdle { scene = 0; dark = theme }
            weight("Contact preferences", FontWeight.SemiBold)
            weight("Contact options", FontWeight.Medium)
            weight("Secondary explanation", FontWeight.Normal)
            weight("Apply changes", FontWeight.Medium)
            weight("End secondary call", FontWeight.Medium)
            rule.onNodeWithText("Unavailable").assertIsNotEnabled()
            rule.onNodeWithText("Contact options").performClick()
            capture("controls-$theme-$scale")
            rule.runOnIdle { scene = 1 }
            weight("Choose an option", FontWeight.SemiBold)
            weight("First option", FontWeight.Medium)
            weight("Second option", FontWeight.Medium)
            capture("selection-$theme-$scale", true)
            rule.onNodeWithText("Second option").performClick()
            rule.runOnIdle { assertEquals("Second option", picked); scene = 2 }
            weight("Apply contact changes", FontWeight.SemiBold)
            weight("Keep the secondary explanation easy to distinguish.", FontWeight.Normal)
            weight("Apply selection", FontWeight.Medium)
            capture("confirmation-$theme-$scale", true)
            rule.onNodeWithText("Apply selection", useUnmergedTree = true).performTouchInput { click() }
        }
        rule.runOnIdle { assertEquals(2, opened); assertEquals(2, applied); interfaceScale = 1.2f }
        weight("Contact options", FontWeight.Medium)
        weight("Secondary explanation", FontWeight.Normal)
    }
}
