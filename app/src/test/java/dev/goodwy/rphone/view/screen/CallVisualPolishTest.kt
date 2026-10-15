package dev.goodwy.rphone.view.screen

import android.telecom.CallAudioState
import android.telecom.Call
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.ui.graphics.Color
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import dev.goodwy.rphone.view.components.CallControlIcons
import org.junit.Assert.*
import org.junit.Test

class CallVisualPolishTest {
    @Test fun `active participant status uses readable text in both themes while hold keeps its accent`() {
        for (scheme in listOf(lightColorScheme(), darkColorScheme())) {
            assertEquals(scheme.onSurface, callParticipantStatusColor(Call.STATE_ACTIVE, scheme))
            assertEquals(scheme.primary, callParticipantStatusColor(Call.STATE_HOLDING, scheme))
        }
    }
    @Test fun `mute icon represents the next action rather than the current state`() {
        assertSame(CallControlIcons.MicOff, callMuteActionIcon(isMuted = false))
        assertSame(CallControlIcons.Mic, callMuteActionIcon(isMuted = true))
    }

    @Test fun `each audio route has its own unambiguous icon`() {
        assertSame(DotDialerHandsetIcon, callAudioRouteIcon(CallAudioState.ROUTE_EARPIECE))
        assertSame(CallControlIcons.Speaker, callAudioRouteIcon(CallAudioState.ROUTE_SPEAKER))
        assertSame(CallControlIcons.Bluetooth, callAudioRouteIcon(CallAudioState.ROUTE_BLUETOOTH))
        assertSame(CallControlIcons.Headset, callAudioRouteIcon(CallAudioState.ROUTE_WIRED_HEADSET))
        assertSame(DotDialerHandsetIcon, callAudioRouteIcon(0))
    }

    @Test fun `light call edge tint uses dark icons regardless of photo presence`() {
        assertFalse(callSystemBarUsesLightIcons(Color.White))
        assertFalse(callSystemBarUsesLightIcons(Color(0xFFF7F5EF)))
        assertFalse(callSystemBarUsesLightIcons(Color(0xFFBBBBBB)))
    }

    @Test fun `dark call edge tint uses light icons`() {
        assertTrue(callSystemBarUsesLightIcons(Color.Black))
        assertTrue(callSystemBarUsesLightIcons(Color(0xFF08100D)))
        assertTrue(callSystemBarUsesLightIcons(Color(0xFF333333)))
    }

    @Test fun `text regions are quieter and central photo keeps original brightness`() {
        listOf(0f, 150f, 200f, 600f, 800f).forEach { y ->
            assertEquals(0.70f, ambientDropReadabilityScale(y, 10f, 200f, 600f, 32f), 0.0001f)
        }
        assertEquals(1f, ambientDropReadabilityScale(400f, 10f, 200f, 600f, 32f), 0.0001f)
    }

    @Test fun `fade is smooth and symmetric and accounts for droplet radius`() {
        assertEquals(0.85f, ambientDropReadabilityScale(226f, 10f, 200f, 600f, 32f), 0.0001f)
        assertEquals(0.85f, ambientDropReadabilityScale(574f, 10f, 200f, 600f, 32f), 0.0001f)
        assertEquals(0.70f, ambientDropReadabilityScale(226f, 26f, 200f, 600f, 32f), 0.0001f)
        val scales = (200..250).map { ambientDropReadabilityScale(it.toFloat(), 10f, 200f, 600f, 32f) }
        assertTrue(scales.zipWithNext().all { (a, b) -> b >= a && b - a < 0.02f })
    }

    @Test fun `measurement fallback and cramped font layouts remain quiet`() {
        assertEquals(0.70f, ambientDropReadabilityScale(100f, 20f, Float.NaN, Float.NaN, 32f), 0f)
        assertEquals(0.70f, ambientDropReadabilityScale(100f, 20f, 200f, 150f, 32f), 0f)
        assertEquals(0.70f, ambientDropReadabilityScale(210f, 20f, 200f, 220f, 32f), 0f)
    }

    @Test fun `quiet treatment follows measured geometry at every screen density`() {
        listOf(1f, 2f, 3.5f).forEach { density ->
            assertEquals(0.85f, ambientDropReadabilityScale(
                226f * density, 10f * density, 200f * density, 600f * density, 32f * density,
            ), 0.0001f)
        }
    }
}
