package dev.goodwy.rphone.view.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.components.*
import dev.goodwy.rphone.view.screen.CallScreenActionButton
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
@Config(sdk = [36], application = Application::class, qualifiers = "w420dp-h900dp-mdpi")
class SemanticAccentRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `semantic statuses remain meaningful while interactions follow all accents`() {
        var dark by mutableStateOf(false)
        var accent by mutableStateOf(DotAccent.Jade)
        var highContrast by mutableStateOf(false)
        var colors: ColorScheme? = null
        var statuses: List<Color> = emptyList()
        var resume = 0
        var muted = 0
        rule.setContent {
            DotDialerTheme(systemDark = dark, accent = accent, highContrast = highContrast) {
                val scheme = MaterialTheme.colorScheme
                val resolved = CallLogVisualStatus.entries.map { callStatusColor(it) }
                SideEffect { colors = scheme; statuses = resolved }
                Surface(Modifier.fillMaxSize(), color = scheme.surface) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Calls", style = MaterialTheme.typography.headlineMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CallLogVisualStatus.entries.forEach { status ->
                                SamsungStyleCallStatusIcon(status, Modifier.size(28.dp))
                            }
                        }
                        Text("Ready", color = scheme.semanticSuccess)
                        Box(Modifier.size(30.dp).testTag("success").background(scheme.semanticSuccess))
                        Box(Modifier.size(30.dp).testTag("accent").background(scheme.primary))
                        Switch(checked = true, onCheckedChange = {})
                        Row {
                            CallScreenActionButton(CallControlIcons.Resume, "Resume", isResumeAction = true) { resume++ }
                            CallScreenActionButton(CallControlIcons.MicOff, "Mute", isActive = true) { muted++ }
                            CallScreenActionButton(CallControlIcons.Keypad, "Keypad", enabled = false) {}
                        }
                        Button(onClick = {}, colors = ButtonDefaults.buttonColors(
                            containerColor = CallActionColors.answerCommitted, contentColor = CallActionColors.onAction)) { Text("Call") }
                        Button(onClick = {}, colors = ButtonDefaults.buttonColors(
                            containerColor = CallActionColors.rejectCommitted, contentColor = CallActionColors.onAction)) { Text("End") }
                        Text("Failed", color = scheme.error)
                    }
                }
            }
        }
        for (mode in listOf(false, true)) for (contrast in listOf(false, true)) {
            var reference: List<Color>? = null
            val primaries = mutableSetOf<Color>()
            for (choice in DotAccent.entries) {
                rule.runOnIdle { dark = mode; accent = choice; highContrast = contrast }
                rule.waitForIdle()
                val current = requireNotNull(colors)
                val expected = semanticSuccessColor(mode)
                assertEquals(Color(0xFF00BB77), statuses[CallLogVisualStatus.INCOMING.ordinal])
                assertEquals(Color(0xFF00BB77), statuses[CallLogVisualStatus.OUTGOING.ordinal])
                assertEquals(current.onSurfaceVariant, statuses[CallLogVisualStatus.NOT_ANSWERED.ordinal])
                assertEquals(current.onSurfaceVariant, statuses[CallLogVisualStatus.OTHER.ordinal])
                assertNotEquals(expected, statuses[CallLogVisualStatus.MISSED.ordinal])
                assertEquals(statuses[CallLogVisualStatus.REJECTED.ordinal], statuses[CallLogVisualStatus.BLOCKED.ordinal])
                reference?.let { assertEquals("Accent must not change call meanings", it, statuses) }
                reference = statuses.toList()
                primaries.add(current.primary)
                val ratio = (maxOf(expected.luminance(), current.surface.luminance()) + .05f) /
                    (minOf(expected.luminance(), current.surface.luminance()) + .05f)
                assertTrue("Readable success foreground: $ratio", ratio >= 4.5f)
                val bitmap = capture("${choice.name}-$mode-$contrast")
                for ((tag, expectedPixel) in listOf("success" to expected, "accent" to current.primary)) {
                    val center = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center
                    assertEquals(expectedPixel.toArgb(), bitmap.getPixel(center.x.toInt(), center.y.toInt()))
                }
                rule.onNodeWithContentDescription("Keypad").assertIsNotEnabled()
                rule.onNodeWithContentDescription("Resume").performClick()
                rule.onNodeWithContentDescription("Mute").performClick()
            }
            assertEquals("Actual interactive accents remain distinct", 4, primaries.size)
        }
        assertEquals(16, resume)
        assertEquals(16, muted)
    }

    private fun capture(name: String): Bitmap {
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        var result: Bitmap? = null
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File(System.getProperty("user.dir"), "build/reports/visual/semantic-colors")
            folder.mkdirs()
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            result = bitmap
        }
        return requireNotNull(result)
    }
}
