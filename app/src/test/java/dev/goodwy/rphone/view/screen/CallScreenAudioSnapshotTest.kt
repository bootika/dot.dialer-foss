package dev.goodwy.rphone.view.screen

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.telecom.CallAudioState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter.from

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

    @Test fun activeBluetoothRouteUsesAliasAndFallsBackToOriginalName() {
        val device = bluetoothDevice()
        val shadow = shadowOf(device)
        shadow.setName("Car Audio 123")
        shadow.setAlias("My car")
        val state = ReflectionHelpers.callConstructor(
            CallAudioState::class.java,
            from(Boolean::class.javaPrimitiveType, false),
            from(Int::class.javaPrimitiveType, CallAudioState.ROUTE_BLUETOOTH),
            from(Int::class.javaPrimitiveType, CallAudioState.ROUTE_BLUETOOTH),
            from(BluetoothDevice::class.java, device),
            from(Collection::class.java, listOf(device)),
        )
        assertEquals("My car", callScreenAudioSnapshot(state).bluetoothDeviceName)

        shadow.setAlias(" ")
        assertEquals("Car Audio 123", callScreenAudioSnapshot(state).bluetoothDeviceName)

        shadow.setName(" ")
        assertEquals("", callScreenAudioSnapshot(state).bluetoothDeviceName)
    }

    @Test @Config(sdk = [29])
    fun android10UsesOriginalNameWithoutReadingAlias() {
        val device = bluetoothDevice()
        shadowOf(device).setName("Car Audio 123")
        shadowOf(device).setAlias("My car")
        assertEquals("Car Audio 123", device.callRouteDisplayName())
    }

    @Test fun unavailableBluetoothPermissionLeavesGenericFallback() {
        val device = bluetoothDevice()
        shadowOf(device).setName("Car Audio 123")
        shadowOf(device).setAlias("My car")
        shadowOf(device).setShouldThrowSecurityExceptions(true)
        assertNull(device.callRouteDisplayName())
    }

    private fun bluetoothDevice() = checkNotNull(
        RuntimeEnvironment.getApplication().getSystemService(BluetoothManager::class.java)?.adapter,
    ).getRemoteDevice("00:11:22:33:44:55")
}
