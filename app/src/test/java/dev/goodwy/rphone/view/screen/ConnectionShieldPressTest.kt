package dev.goodwy.rphone.view.screen

import android.app.Application
import android.content.ComponentName
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectionShieldPressTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun pressOnlyChangesVisiblePillOpacityAndReleaseCancelRestoreIt() {
        var dark by mutableStateOf(false)
        var connecting by mutableStateOf(false)
        var scale by mutableStateOf(1f)
        var clicks = 0
        var darkPressedRed = 0
        val account = PhoneAccount.builder(
            PhoneAccountHandle(ComponentName("dot.dialer.test", "TestPhoneAccountService"), "sim-1"), "SIM 1",
        ).build()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                DotDialerTheme(systemDark = dark) {
                    darkPressedRed = android.graphics.Color.red(MaterialTheme.colorScheme.surface.toArgb())
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        CallConnectionStatusRow(
                            CallConnectionInfo(CallTransportKind.Cellular, VoiceQualityTier.HdPlus, null, null, null),
                            "00:05", { clicks++ }, Modifier.width(312.dp), account = account, connecting = connecting,
                        )
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (setup in listOf(false, true)) for (font in listOf(1f, 2f)) {
            rule.runOnIdle { dark = theme; connecting = setup; scale = font; Snapshot.sendApplyNotifications() }
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            val target = rule.onNodeWithTag("callConnectionTouchTarget", true)
            val pill = rule.onNodeWithTag("callConnectionShield", true)
            val bounds = pill.fetchSemanticsNode().boundsInRoot
            val touchBounds = target.fetchSemanticsNode().boundsInRoot
            assertTrue(touchBounds.height >= 48f)
            val idle = bitmap()
            target.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(250)
            val pressed = bitmap()
            assertEquals(bounds, pill.fetchSemanticsNode().boundsInRoot)
            assertEquals(touchBounds, target.fetchSemanticsNode().boundsInRoot)
            // The top-center pixel is background only, above all cell content.
            val x = bounds.center.x.toInt()
            val y = bounds.top.toInt() + 1
            val base = if (theme) 51 else 128
            val pushed = if (theme) kotlin.math.round(darkPressedRed * .3f).toInt() else 230
            assertEquals(base.toDouble(), android.graphics.Color.red(idle.getPixel(x, y)).toDouble(), 1.0)
            assertEquals(pushed.toDouble(), android.graphics.Color.red(pressed.getPixel(x, y)).toDouble(), 1.0)
            var changed = 0
            for (py in 0 until idle.height) for (px in 0 until idle.width) {
                if (idle.getPixel(px, py) != pressed.getPixel(px, py)) {
                    changed++
                    assertTrue("press painted outside pill at $px,$py", px >= bounds.left && px < bounds.right && py >= bounds.top && py < bounds.bottom)
                    val before = android.graphics.Color.red(idle.getPixel(px, py))
                    val after = android.graphics.Color.red(pressed.getPixel(px, py))
                    // Native color interpolation/edge antialiasing may round by one channel unit.
                    assertTrue("fill changes toward readable90light30dark theme=$theme font=$font setup=$setup at $px,$py: $before -> $after target=$pushed", if (theme) after <= before + 1 else after + 1 >= before)
                }
            }
            assertTrue(changed > 100)
            save(pressed, "pressed-$theme-$setup-$font")
            target.performTouchInput { cancel() }
            rule.mainClock.advanceTimeBy(400)
            assertSamePixels(idle, bitmap())
            assertEquals(0, clicks)
            target.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(250)
            target.performTouchInput { up() }
            rule.mainClock.advanceTimeBy(400)
            assertEquals(1, clicks)
            assertSamePixels(idle, bitmap())
            save(idle, "idle-$theme-$setup-$font")
            rule.runOnIdle { clicks = 0 }
        }
    }

    private fun assertSamePixels(expected: Bitmap, actual: Bitmap) {
        assertTrue("release/cancel restores identical pixels", expected.sameAs(actual))
    }

    private fun bitmap(): Bitmap {
        lateinit var result: Bitmap
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            result = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(result))
        }
        return result
    }

    private fun save(bitmap: Bitmap, name: String) {
        val folder = File("../evidence/local-verification/shield-dark30-20261018").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
