package io.github.bootika.dotdialer.diagnostics

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.bootika.dotdialer.core.diagnostics.*

/** Public broadcasts only, while this process is alive. Does not read devices or HCI packets. */
internal class BluetoothDiagnosticMonitor(private val application: Application,
    private val allowed: () -> Boolean, private val record: (DiagnosticDetail) -> Unit) {
    private var registered = false
    private val last = mutableMapOf<DiagnosticBluetoothProfile,DiagnosticDetail.BluetoothTransition>()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(!allowed()) { close(); return }
            val event = intent?.let(::bluetoothDiagnosticTransition) ?: return
            if(last[event.profile] == event) return
            last[event.profile] = event
            record(event)
        }
    }
    fun start() {
        if(registered || !allowed()) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        try {
            // Bluetooth broadcasts originate from a privileged system UID other than system_server.
            ContextCompat.registerReceiver(application, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        } catch(failure: RuntimeException) { registered = false }
    }
    fun close() {
        if(registered) try { application.unregisterReceiver(receiver) } catch(failure: IllegalArgumentException) { /* no identity retained */ }
        registered = false; last.clear()
    }
}
internal fun bluetoothDiagnosticTransition(intent: Intent): DiagnosticDetail.BluetoothTransition? {
    val profile = when(intent.action) {
        BluetoothAdapter.ACTION_STATE_CHANGED -> DiagnosticBluetoothProfile.ADAPTER
        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> DiagnosticBluetoothProfile.HFP
        BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> DiagnosticBluetoothProfile.HFP_AUDIO
        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> DiagnosticBluetoothProfile.A2DP
        else -> return null
    }
    val adapter = profile == DiagnosticBluetoothProfile.ADAPTER
    val previous = intent.getIntExtra(if(adapter) BluetoothAdapter.EXTRA_PREVIOUS_STATE else BluetoothProfile.EXTRA_PREVIOUS_STATE,-1)
    val current = intent.getIntExtra(if(adapter) BluetoothAdapter.EXTRA_STATE else BluetoothProfile.EXTRA_STATE,-1)
    val allowed = when(profile) {
        DiagnosticBluetoothProfile.ADAPTER -> 10..13
        DiagnosticBluetoothProfile.HFP_AUDIO -> 10..12
        else -> 0..3
    }
    return DiagnosticDetail.BluetoothTransition(profile, previous.takeIf { it in allowed } ?: -1, current.takeIf { it in allowed } ?: -1)
}
