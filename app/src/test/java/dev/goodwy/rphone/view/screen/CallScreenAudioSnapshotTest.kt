package dev.goodwy.rphone.view.screen

import android.telecom.CallAudioState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class CallScreenAudioSnapshotTest {
    @Test fun nullAudioBeforeCallbackAndAfterTeardownHasExistingFallbacks() {
        assertEquals(CallScreenAudioSnapshot(), callScreenAudioSnapshot(null))
        val active = CallAudioState(true, CallAudioState.ROUTE_SPEAKER, 9)
        assertEquals(CallScreenAudioSnapshot(true, CallAudioState.ROUTE_SPEAKER, 9), callScreenAudioSnapshot(active))
        assertEquals(CallScreenAudioSnapshot(), callScreenAudioSnapshot(null))
    }
    @Test fun allLiveRoutesAndMuteStatesKeepTelecomValues() {
        for (route in listOf(1, 2, 4, 8)) for (muted in listOf(false, true)) {
            val snapshot = callScreenAudioSnapshot(CallAudioState(muted, route, 15))
            assertEquals(muted, snapshot.muted)
            assertEquals(route, snapshot.route)
            assertEquals(15, snapshot.supportedRouteMask)
            assertEquals("", snapshot.bluetoothDeviceName)
        }
    }
}
