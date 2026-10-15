package dev.goodwy.rphone.view.screen

import android.telecom.Call
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallControlAvailabilityTest {
    @Test
    fun `connecting exposes only supported audio controls`() {
        val controls = callControlAvailability(
            callState = Call.STATE_CONNECTING,
            canHold = true,
            canMute = true,
            supportedAudioRouteCount = 3,
            hasOtherCall = false,
        )

        assertFalse(controls.primaryAction)
        assertTrue(controls.mute)
        assertTrue(controls.audioRoute)
        assertFalse(controls.keypad)
        assertFalse(controls.more)
    }

    @Test
    fun `active call exposes actions backed by telecom capabilities`() {
        val controls = callControlAvailability(
            callState = Call.STATE_ACTIVE,
            canHold = true,
            canMute = true,
            supportedAudioRouteCount = 2,
            hasOtherCall = false,
        )

        assertTrue(controls.primaryAction)
        assertTrue(controls.mute)
        assertTrue(controls.audioRoute)
        assertTrue(controls.keypad)
        assertTrue(controls.more)
    }

    @Test
    fun `second established call disables add call because swap and merge live in its card`() {
        val controls = callControlAvailability(
            callState = Call.STATE_ACTIVE,
            canHold = true,
            canMute = true,
            supportedAudioRouteCount = 2,
            hasOtherCall = true,
        )

        assertFalse(controls.primaryAction)
    }

    @Test
    fun `single audio route and missing mute capability stay disabled`() {
        val controls = callControlAvailability(
            callState = Call.STATE_ACTIVE,
            canHold = true,
            canMute = false,
            supportedAudioRouteCount = 1,
            hasOtherCall = false,
        )

        assertFalse(controls.mute)
        assertFalse(controls.audioRoute)
    }

    @Test
    fun `active conference keeps global mute available when parent omits mute capability`() {
        val controls = callControlAvailability(
            callState = Call.STATE_ACTIVE,
            canHold = true,
            canMute = false,
            isConference = true,
            supportedAudioRouteCount = 2,
            hasOtherCall = false,
        )

        assertTrue(controls.mute)
    }
}
