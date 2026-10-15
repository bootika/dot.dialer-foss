package io.github.bootika.dotdialer.diagnostics

import android.Manifest
import android.app.UiModeManager
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticDetail

/** Public API states only. -1 means unknown/unavailable, never falsely reports disconnected. */
internal fun readCarConnection(context: Context, projection: Int): DiagnosticDetail.ConnectionState {
    val permission = context.checkSelfPermission(if (Build.VERSION.SDK_INT >= 31)
        Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
    var adapterState = -1; var handsFree = -1; var media = -1
    if (permission) try {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.let { adapter ->
            adapterState = adapter.state
            handsFree = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
            media = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
        }
    } catch (failure: RuntimeException) { /* Unknown, not a missing car. No identity/error message retained. */ }
    val uiMode = context.getSystemService(UiModeManager::class.java)?.currentModeType ?: -1
    return DiagnosticDetail.ConnectionState(permission, adapterState, handsFree, media, projection, uiMode)
}
