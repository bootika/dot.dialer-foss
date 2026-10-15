package dev.goodwy.rphone.controller.util

import android.os.OutcomeReceiver
import android.os.ParcelUuid
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 36], application = android.app.Application::class)
class CallEndpointAudioRouterTest {
    private var state: CallAudioState? = null
    private var requested: CallEndpoint? = null
    private var receiver: OutcomeReceiver<Void?, CallEndpointException>? = null
    private val router = CallEndpointAudioRouter({ endpoint, result ->
        requested = endpoint; receiver = result
    }, { state = it })
    private val handset = endpoint(CallEndpoint.TYPE_EARPIECE)
    private val speaker = endpoint(CallEndpoint.TYPE_SPEAKER)
    private fun endpoint(type: Int) = CallEndpoint("Audio device", type, ParcelUuid(UUID.randomUUID()))
    private fun legacy(mask: Int = 1, route: Int = 1, muted: Boolean = false) =
        CallAudioState(muted, route, mask)

    @Test fun modernSpeakerAvailabilityOverridesIncompleteLegacyMask() {
        router.legacyChanged(legacy())
        assertNull(nextCallAudioRoute(state!!.route, state!!.supportedRouteMask))
        router.availableChanged(listOf(handset, speaker))
        assertEquals(CallAudioState.ROUTE_SPEAKER, nextCallAudioRoute(state!!.route, state!!.supportedRouteMask))
        router.legacyChanged(legacy())
        assertEquals(9, state!!.supportedRouteMask)
    }

    @Test fun requestUsesExactAvailableEndpointAndWaitsForConfirmation() {
        router.legacyChanged(legacy())
        router.availableChanged(listOf(handset, speaker))
        assertTrue(router.setRoute(CallAudioState.ROUTE_SPEAKER))
        assertSame(speaker, requested)
        receiver!!.onResult(null)
        assertEquals(1, state!!.route)
        router.currentChanged(speaker)
        assertEquals(8, state!!.route)
        router.legacyChanged(legacy())
        assertEquals(8, state!!.route)
    }

    @Test fun failureAndRemovedEndpointNeverInventOrOptimisticallyChangeRoute() {
        router.legacyChanged(legacy())
        router.availableChanged(listOf(handset, speaker))
        router.setRoute(8)
        receiver!!.onError(CallEndpointException(null, CallEndpointException.ERROR_ENDPOINT_DOES_NOT_EXIST))
        assertEquals(1, state!!.route)
        requested = null
        router.availableChanged(listOf(handset))
        assertTrue(router.setRoute(8))
        assertNull(requested)
        assertEquals(1, state!!.supportedRouteMask)
    }

    @Test fun callbacksInEitherOrderKeepCurrentRouteMuteAndAuthoritativeEmptyList() {
        router.currentChanged(speaker)
        router.muteChanged(true)
        router.availableChanged(listOf(handset, speaker))
        router.legacyChanged(legacy(muted = false))
        assertTrue(state!!.isMuted)
        assertEquals(8, state!!.route)
        router.availableChanged(emptyList())
        router.legacyChanged(legacy(mask = 15))
        assertEquals(0, state!!.supportedRouteMask)
        router.muteChanged(false)
        assertFalse(state!!.isMuted)
    }

    @Test fun bluetoothAndWiredRoutesCycleAndUnknownTypesAreNotMappedToSpeaker() {
        val bluetooth = endpoint(CallEndpoint.TYPE_BLUETOOTH)
        val wired = endpoint(CallEndpoint.TYPE_WIRED_HEADSET)
        router.availableChanged(listOf(handset, speaker, bluetooth, wired, endpoint(CallEndpoint.TYPE_STREAMING)))
        assertEquals(15, state!!.supportedRouteMask)
        router.currentChanged(bluetooth)
        assertEquals(8, nextCallAudioRoute(state!!.route, state!!.supportedRouteMask))
        router.setRoute(4)
        assertSame(wired, requested)
        router.currentChanged(wired)
        router.setRoute(2)
        assertSame(bluetooth, requested)
    }

    @Test fun legacyRemainsAvailableUntilFirstEndpointListAndPreservesMatchingObject() {
        val original = legacy(mask = 9)
        router.legacyChanged(original)
        assertSame(original, state)
        assertFalse(router.setRoute(8))
        router.currentChanged(handset)
        router.availableChanged(listOf(handset, speaker))
        assertSame(original, state)
        requested = null
        router.setRoute(1)
        assertNull(requested)
    }
}
