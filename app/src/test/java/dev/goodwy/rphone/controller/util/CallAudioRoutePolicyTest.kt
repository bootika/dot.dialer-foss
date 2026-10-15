package dev.goodwy.rphone.controller.util

import android.telecom.CallAudioState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallAudioRoutePolicyTest {
    private val everyRoute = CallAudioState.ROUTE_EARPIECE or
        CallAudioState.ROUTE_SPEAKER or
        CallAudioState.ROUTE_WIRED_HEADSET or
        CallAudioState.ROUTE_BLUETOOTH

    @Test
    fun `common cycle is bluetooth then speaker then handset`() {
        val commonRoutes = CallAudioState.ROUTE_EARPIECE or
            CallAudioState.ROUTE_SPEAKER or
            CallAudioState.ROUTE_BLUETOOTH

        assertEquals(
            CallAudioState.ROUTE_SPEAKER,
            nextCallAudioRoute(CallAudioState.ROUTE_BLUETOOTH, commonRoutes),
        )
        assertEquals(
            CallAudioState.ROUTE_EARPIECE,
            nextCallAudioRoute(CallAudioState.ROUTE_SPEAKER, commonRoutes),
        )
        assertEquals(
            CallAudioState.ROUTE_BLUETOOTH,
            nextCallAudioRoute(CallAudioState.ROUTE_EARPIECE, commonRoutes),
        )
    }

    @Test
    fun `wired headset remains reachable after the preferred three routes`() {
        assertEquals(
            CallAudioState.ROUTE_WIRED_HEADSET,
            nextCallAudioRoute(CallAudioState.ROUTE_EARPIECE, everyRoute),
        )
        assertEquals(
            CallAudioState.ROUTE_BLUETOOTH,
            nextCallAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET, everyRoute),
        )
        assertEquals(
            CallAudioState.ROUTE_SPEAKER,
            nextCallAudioRoute(CallAudioState.ROUTE_BLUETOOTH, everyRoute),
        )
    }

    @Test
    fun `without bluetooth speaker and handset alternate directly`() {
        val supported = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER

        assertEquals(
            CallAudioState.ROUTE_SPEAKER,
            nextCallAudioRoute(CallAudioState.ROUTE_EARPIECE, supported),
        )
        assertEquals(
            CallAudioState.ROUTE_EARPIECE,
            nextCallAudioRoute(CallAudioState.ROUTE_SPEAKER, supported),
        )
    }

    @Test
    fun `unsupported routes are skipped`() {
        val supported = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_BLUETOOTH

        assertEquals(
            CallAudioState.ROUTE_BLUETOOTH,
            nextCallAudioRoute(CallAudioState.ROUTE_EARPIECE, supported),
        )
        assertEquals(
            CallAudioState.ROUTE_EARPIECE,
            nextCallAudioRoute(CallAudioState.ROUTE_BLUETOOTH, supported),
        )
    }

    @Test
    fun `one route cannot be cycled`() {
        assertNull(
            nextCallAudioRoute(
                CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE,
            ),
        )
    }
}
