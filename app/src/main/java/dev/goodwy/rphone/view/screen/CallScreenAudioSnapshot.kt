package dev.goodwy.rphone.view.screen

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.telecom.CallAudioState

internal data class CallScreenAudioSnapshot(
    val muted: Boolean = false,
    val route: Int = CallAudioState.ROUTE_EARPIECE,
    val supportedRouteMask: Int = 0,
    val bluetoothDeviceName: String = "",
)

/** Keep nullable Telecom reads outside the large Compose method and verify their compiled guards. */
internal fun callScreenAudioSnapshot(state: CallAudioState?): CallScreenAudioSnapshot {
    if (state == null) return CallScreenAudioSnapshot()
    val deviceName = try { state.activeBluetoothDevice?.callRouteDisplayName().orEmpty() }
        catch (error: SecurityException) {
            android.util.Log.w("CallScreenAudio", "Bluetooth name unavailable: " + error.javaClass.simpleName)
            ""
        }
    return CallScreenAudioSnapshot(state.isMuted, state.route, state.supportedRouteMask, deviceName)
}

// The existing Bluetooth grant can be revoked while a call is active.
@SuppressLint("MissingPermission")
internal fun BluetoothDevice.callRouteDisplayName(): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val alias = try { alias } catch (error: SecurityException) {
            android.util.Log.d("CallScreenAudio", "Bluetooth alias unavailable: " + error.javaClass.simpleName)
            null
        }
        if (!alias.isNullOrBlank()) return alias
    }
    return try { name?.takeIf { it.isNotBlank() } } catch (error: SecurityException) {
        android.util.Log.d("CallScreenAudio", "Bluetooth name unavailable: " + error.javaClass.simpleName)
        null
    }
}
