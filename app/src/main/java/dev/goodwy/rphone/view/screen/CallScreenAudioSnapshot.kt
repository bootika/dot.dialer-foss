package dev.goodwy.rphone.view.screen

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
    val deviceName = try { state.activeBluetoothDevice?.name.orEmpty() }
        catch (error: SecurityException) {
            android.util.Log.w("CallScreenAudio", "Bluetooth name unavailable: " + error.javaClass.simpleName)
            ""
        }
    return CallScreenAudioSnapshot(state.isMuted, state.route, state.supportedRouteMask, deviceName)
}
