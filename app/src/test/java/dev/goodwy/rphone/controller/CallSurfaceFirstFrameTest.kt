package dev.goodwy.rphone.controller

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.view.theme.DotDialerTheme
import dev.goodwy.rphone.modal.`interface`.CallSession
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallSurfaceFirstFrameTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightFirstFrameNeverShowsPreviousCall() = verifyFrames(false)
    @Test fun darkFirstFrameNeverShowsPreviousCall() = verifyFrames(true)

    private fun verifyFrames(isDark: Boolean) {
        val displayed = mutableStateOf<Call?>(null)
        val first = Shadow.newInstanceOf(Call::class.java)
        val second = Shadow.newInstanceOf(Call::class.java)
        val composed = mutableSetOf<Call>()
        var appliedCall: Call? = null
        var surface = Color.Unspecified
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DotDialerTheme(systemDark = isDark) {
                surface = MaterialTheme.colorScheme.surface
                Box(Modifier.fillMaxSize().background(Color.Green)) {
                    val call = displayed.value
                    SideEffect { appliedCall = call }
                    CallSurface(call) {
                        if (call != null) {
                            TrackCallSurface(call, composed)
                            Box(Modifier.fillMaxSize().background(if (call === first) Color.Blue else Color.Red))
                        }
                    }
                }
            }
        }
        val theme = if (isDark) "dark" else "light"
        renderFrame()
        assertEquals(surface.toArgb(), capture("$theme-waiting"))
        rule.runOnIdle { displayed.value = first }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertNotEquals(Color.Green.toArgb(), capture("$theme-update-frame"))
        // One frame applies composition state, the next presents its resulting layout.
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertEquals(Color.Blue.toArgb(), capture("$theme-first-call-frame"))
        rule.runOnIdle { displayed.value = second }
        awaitApplied { appliedCall === second }
        assertEquals(setOf(second), rule.runOnIdle { composed.toSet() })
    }

    @Test fun newOutgoingRequestSuppressesEndedSessionWhileWaitingForTelecom() {
        val first = Shadow.newInstanceOf(Call::class.java)
        val second = Shadow.newInstanceOf(Call::class.java)
        val session = mutableStateOf<CallSession?>(CallSession(first, Call.STATE_DISCONNECTED))
        val outgoing = PendingOutgoingCallLaunch()
        val composed = mutableSetOf<Call>()
        var appliedSession: CallSession? = null
        var appliedPending = false
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DotDialerTheme {
                val incoming = session.value
                val pending = outgoing.isPending
                SideEffect {
                    appliedSession = incoming
                    appliedPending = pending
                }
                val call = rememberPresentedCallSession(incoming, outgoing, false) {}?.call
                CallSurface(call) {
                    if (call != null) TrackCallSurface(call, composed)
                }
            }
        }
        renderFrame()
        assertEquals(setOf(first), rule.runOnIdle { composed.toSet() })

        rule.runOnIdle { outgoing.start(true) }
        awaitApplied { appliedPending }
        assertTrue(rule.runOnIdle { composed.isEmpty() })
        assertTrue(outgoing.isPending)

        rule.runOnIdle { session.value = CallSession(second, Call.STATE_CONNECTING) }
        awaitApplied { appliedSession?.call === second }
        assertEquals(setOf(second), rule.runOnIdle { composed.toSet() })
    }

    @Test fun matchingDirectRequestKeepsCompositionWhenTelecomAttaches() {
        val call = Shadow.newInstanceOf(Call::class.java)
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        val displayed = mutableStateOf<Call?>(null)
        val requestId = "pending-request"
        ReflectionHelpers.setField(call, "mDetails", details)
        ReflectionHelpers.setField(details, "mIntentExtras", Bundle().apply {
            putString(DirectCallWindow.REQUEST_EXTRA, requestId)
        })
        var state: Any? = null
        rule.setContent {
            DotDialerTheme {
                CallSurface(displayed.value, requestId.takeIf { displayed.value == null }) {
                    val token = remember { Any() }
                    SideEffect { state = token }
                }
            }
        }
        val pending = rule.runOnIdle { requireNotNull(state) }
        rule.runOnIdle { displayed.value = call }
        rule.waitForIdle()
        assertSame(pending, rule.runOnIdle { state })
    }

    private fun renderFrame() {
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    /** Advance only until Compose applies the input, without waiting for a transition to finish. */
    private fun awaitApplied(inputApplied: () -> Boolean) {
        rule.waitUntil(5_000) {
            rule.mainClock.advanceTimeByFrame()
            rule.runOnIdle(inputApplied)
        }
    }

    private fun capture(name: String): Int {
        return rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/call-handoff/$name.png")
                output.parentFile?.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            } finally {
                bitmap.recycle()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun TrackCallSurface(call: Call, composed: MutableSet<Call>) {
    DisposableEffect(call) {
        composed.add(call)
        onDispose { composed.remove(call) }
    }
}
