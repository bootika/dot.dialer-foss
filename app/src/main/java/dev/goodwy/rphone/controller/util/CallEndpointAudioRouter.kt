package dev.goodwy.rphone.controller.util

import android.os.OutcomeReceiver
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.util.Log
import androidx.annotation.RequiresApi

/** Adapts confirmed Telecom endpoints to the audio snapshot shared by every call surface. */
@RequiresApi(34)
internal class CallEndpointAudioRouter(
    private val request: (CallEndpoint, OutcomeReceiver<Void?, CallEndpointException>) -> Unit,
    private val publish: (CallAudioState) -> Unit,
) {
    private var legacy: CallAudioState? = null
    private var available: List<CallEndpoint>? = null
    private var current: CallEndpoint? = null
    private var muted: Boolean? = null

    fun legacyChanged(state: CallAudioState?) {
        legacy = state
        emit()
    }

    fun availableChanged(endpoints: List<CallEndpoint>) {
        available = endpoints.toList()
        emit()
    }

    fun currentChanged(endpoint: CallEndpoint) {
        current = endpoint
        emit()
    }

    fun muteChanged(isMuted: Boolean) {
        muted = isMuted
        emit()
    }

    /** False only before the modern list is known, when the legacy routing path still applies. */
    fun setRoute(route: Int): Boolean {
        val endpoints = available ?: return false
        val endpoint = endpoints.firstOrNull { endpointRoute(it) == route } ?: return true
        if (current?.identifier == endpoint.identifier) return true
        try {
            request(endpoint, object : OutcomeReceiver<Void?, CallEndpointException> {
                override fun onResult(result: Void?) = Unit // Wait for the confirmed endpoint callback.
                override fun onError(error: CallEndpointException) {
                    Log.w("CallAudioRouting", "Endpoint request failed: code=${error.code}")
                }
            })
        } catch (error: Exception) {
            Log.w("CallAudioRouting", "Endpoint request failed: type=${error.javaClass.simpleName}")
        }
        return true
    }

    private fun emit() {
        if (legacy == null && available == null && current == null && muted == null) return
        val route = current?.let(::endpointRoute) ?: legacy?.route ?: 0
        val mask = available?.fold(0) { value, endpoint -> value or endpointRoute(endpoint) }
            ?: legacy?.supportedRouteMask ?: 0
        val isMuted = muted ?: legacy?.isMuted ?: false
        val old = legacy
        // Reuse the complete legacy object when it agrees, preserving Bluetooth device metadata.
        publish(if (old != null && old.route == route && old.supportedRouteMask == mask &&
            old.isMuted == isMuted) old else CallAudioState(isMuted, route, mask))
    }

    private fun endpointRoute(endpoint: CallEndpoint): Int = when (endpoint.endpointType) {
        CallEndpoint.TYPE_EARPIECE -> CallAudioState.ROUTE_EARPIECE
        CallEndpoint.TYPE_SPEAKER -> CallAudioState.ROUTE_SPEAKER
        CallEndpoint.TYPE_BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
        CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
        else -> 0 // Streaming/unknown endpoints are not invented as handset or speaker.
    }
}
