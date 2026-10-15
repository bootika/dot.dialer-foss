package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import dev.goodwy.rphone.view.components.DotDialerHangupIcon
import dev.goodwy.rphone.view.components.DotDialerMergeIcon
import dev.goodwy.rphone.view.components.CallControlIcons
import dev.goodwy.rphone.view.components.CallControlIconStyle
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
class CallIconOpticsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val baselineGlyphs: List<Pair<String, ImageVector>> = listOf(
        "Add" to Icons.Rounded.Add, "Mute" to Icons.Rounded.MicOff, "Handset" to DotDialerHandsetIcon,
        "Note" to Icons.AutoMirrored.Outlined.StickyNote2, "Keypad" to Icons.Rounded.Dialpad, "More" to Icons.Default.MoreVert,
        "Merge" to DotDialerMergeIcon, "Unmute" to Icons.Rounded.Mic, "Resume" to Icons.Rounded.PlayArrow,
        "Speaker" to Icons.AutoMirrored.Rounded.VolumeUp, "Bluetooth" to Icons.Rounded.Bluetooth, "Headset" to Icons.Rounded.Headset,
        "Message" to Icons.Rounded.ChatBubbleOutline, "Reminder" to Icons.Rounded.Alarm, "More incoming" to Icons.Rounded.MoreHoriz,
        "Answer" to DotDialerHandsetIcon, "Reject / End" to DotDialerHangupIcon,
    )
    private val glyphs = baselineGlyphs.map { it.first }.zip(listOf(
        CallControlIcons.Add, CallControlIcons.MicOff, DotDialerHandsetIcon,
        CallControlIcons.Note, CallControlIcons.Keypad, CallControlIcons.More,
        DotDialerMergeIcon, CallControlIcons.Mic, CallControlIcons.Resume,
        CallControlIcons.Speaker, CallControlIcons.Bluetooth, CallControlIcons.Headset,
        CallControlIcons.Message, CallControlIcons.Reminder, CallControlIcons.MoreHorizontal,
        DotDialerHandsetIcon, DotDialerHangupIcon,
    ))

    @Test fun `render and measure common optical profiles in light dark and disabled states`() {
        val dark = mutableStateOf(false)
        val baseline = mutableStateOf(true)
        val disabled = mutableStateOf(false)
        rule.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                    Text(if (baseline.value) "Before: shared 27 dp" else "After: optical sizes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    glyphs.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { (name, icon) ->
                                Column(Modifier.width(108.dp).height(116.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(68.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                                        val renderedIcon = if (baseline.value) baselineGlyphs.first { it.first == name }.second else icon
                                        Icon(renderedIcon, name, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (disabled.value) .5f else 1f),
                                            modifier = Modifier.size(if (baseline.value) 27.dp else callActionIconSize(icon)).testTag(name))
                                    }
                                    Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
        val output = File(System.getProperty("user.dir"), "build/reports/visual/call-icons").apply { mkdirs() }
        fun capture(name: String) {
            rule.waitForIdle()
            rule.runOnIdle {
                val view = rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(output, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
        }
        fun ink(name: String): Float {
            val coordinates = rule.onNodeWithTag(name).fetchSemanticsNode().layoutInfo.coordinates
            var area = 0f
            rule.runOnIdle {
            val origin = coordinates.localToWindow(Offset.Zero)
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            // Contrast-weighted painted area: antialiasing contributes proportionally.
            val background = android.graphics.Color.red(bitmap.getPixel(origin.x.toInt(), origin.y.toInt()))
            for (y in 0 until coordinates.size.height) for (x in 0 until coordinates.size.width) {
                val pixel = android.graphics.Color.red(bitmap.getPixel(origin.x.toInt() + x, origin.y.toInt() + y))
                area += kotlin.math.abs(pixel - background) / 255f
            }
            }
            return area
        }
        capture("01-before-light")
        val before = ink("Add")
        rule.runOnIdle { baseline.value = false }
        capture("02-after-light")
        assertTrue("Plus must gain real painted presence, not just layout bounds", ink("Add") > before * 1.3f)
        val metrics = glyphs.map { (name, icon) -> "$name,${callActionIconSize(icon).value},${ink(name)}" }
        File(output, "painted-area.csv").writeText("symbol,viewport_dp,contrast_weighted_area\n" + metrics.joinToString("\n"))
        assertEquals(ink("Answer"), ink("Reject / End"), 12f)
        rule.runOnIdle { dark.value = true }
        capture("03-after-dark")
        rule.runOnIdle { disabled.value = true }
        capture("04-after-dark-disabled")
        for ((name, icon) in glyphs) {
            assertTrue("$name stays comfortably inside the existing 68 dp circle", callActionIconSize(icon).value <= 34f)
        }
    }

    @Test fun `sparse symbols have explicit compensation and all phones share one profile`() {
        assertEquals(33.dp, callActionIconSize(CallControlIcons.Add))
        assertEquals(31.dp, callActionIconSize(CallControlIcons.More))
        assertEquals(29.dp, callActionIconSize(DotDialerMergeIcon))
        assertEquals(callActionIconSize(DotDialerHandsetIcon), callActionIconSize(DotDialerHangupIcon))
        assertEquals(callActionIconSize(CallControlIcons.Mic), callActionIconSize(CallControlIcons.MicOff))
        assertEquals(29.dp, callActionIconSize(CallControlIcons.Keypad))
    }

    @Test fun `optical compensation preserves exactly the same physical stroke and rounded ends`() {
        val lines = (glyphs.map { it.second } + dev.goodwy.rphone.view.components.CallControlIcons.Swap).distinct().filter {
            it != DotDialerHandsetIcon && it != DotDialerHangupIcon && it != CallControlIcons.Keypad &&
                it != CallControlIcons.More && it != CallControlIcons.MoreHorizontal
        }
        for (icon in lines) for (index in 0 until icon.root.size) {
            val path = icon.root[index] as VectorPath
            assertEquals("${icon.name} rendered stroke", CallControlIconStyle.StrokeWidthDp,
                path.strokeLineWidth * callActionIconSize(icon).value / icon.viewportWidth, .0001f)
            assertEquals(androidx.compose.ui.graphics.StrokeCap.Round, path.strokeLineCap)
            assertEquals(androidx.compose.ui.graphics.StrokeJoin.Round, path.strokeLineJoin)
        }
        assertEquals(10, CallControlIcons.Keypad.root.size)
        assertEquals(3, CallControlIcons.More.root.size)
        assertEquals(3, CallControlIcons.MoreHorizontal.root.size)
        assertTrue(CallControlIcons.Speaker.autoMirror)
        assertTrue(CallControlIcons.Note.autoMirror)
        assertFalse(DotDialerMergeIcon.autoMirror)
    }

    @Test fun `all incoming modes and the shared call grid consume optical sizes`() {
        val components = File(System.getProperty("user.dir"), "src/main/java/dev/goodwy/rphone/view/screen/CallScreenComponents.kt").readText()
        val functions = listOf("CallScreenActionButton", "HorizontalSwipeToAnswer", "VerticalSwipeToAnswer", "DualActionHorizontalSlider", "IPhoneSwipeToAnswer", "IncomingWaitingDecisionButton", "DefaultSwipeToAnswer")
        for (function in functions) {
            val declaration = requireNotNull(Regex("fun (?:RowScope\\.)?$function\\(").find(components))
            val section = components.substring(declaration.range.first).substringBefore("\n@Composable")
            assertTrue("$function must consume shared optical profile", section.contains("callActionIconSize("))
        }
        val radial = File(System.getProperty("user.dir"), "src/main/java/dev/goodwy/rphone/view/screen/SamsungRadialAnswerButtons.kt").readText()
        assertTrue(radial.contains("Modifier.size(callActionIconSize(icon))"))
        val main = File(System.getProperty("user.dir"), "src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt").readText()
        assertTrue(main.contains("Modifier.size(callActionIconSize(DotDialerHangupIcon))"))
    }
}
