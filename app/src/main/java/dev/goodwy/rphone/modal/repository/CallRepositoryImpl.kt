package dev.goodwy.rphone.modal.repository

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.controller.util.nextCallAudioRoute
import dev.goodwy.rphone.controller.util.CallEndpointAudioRouter
import android.telecom.CallEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val CALL_REPOSITORY_LOG_TAG = "CallRepository"

class CallRepositoryImpl : ICallRepository {

    private val _currentCallSession = MutableStateFlow<CallSession?>(null)
    override val currentCallSession: StateFlow<CallSession?> = _currentCallSession.asStateFlow()

    private val _allCalls = MutableStateFlow<List<Call>>(emptyList())
    override val allCalls: StateFlow<List<Call>> = _allCalls.asStateFlow()

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    override val audioState: StateFlow<CallAudioState?> = _audioState.asStateFlow()

    private val _isActivityVisible = MutableStateFlow(false)
    override val isActivityVisible: StateFlow<Boolean> = _isActivityVisible.asStateFlow()

    private val _preferredCall = MutableStateFlow<Call?>(null)

    private var inCallService: InCallService? = null
    private var endpointRouter: CallEndpointAudioRouter? = null

    fun bindService(service: InCallService) {
        this.inCallService = service
        _audioState.value = null
        if (Build.VERSION.SDK_INT >= 34) {
            endpointRouter = CallEndpointAudioRouter(
                request = { endpoint, receiver ->
                    if (inCallService === service) {
                        service.requestCallEndpointChange(endpoint, service.mainExecutor, receiver)
                    }
                },
                publish = { state -> if (inCallService === service) _audioState.value = state },
            )
        }
    }

    fun unbindService() {
        this.inCallService = null
        endpointRouter = null
        _audioState.value = null
    }

    override fun updateCurrentCallSession(session: CallSession?) {
        _currentCallSession.value = session
    }

    override fun updateAllCalls(calls: List<Call>) {
        _allCalls.value = ArrayList(calls)
    }

    override fun updateAudioState(state: CallAudioState?) {
        if (Build.VERSION.SDK_INT >= 34 && endpointRouter != null) {
            endpointRouter?.legacyChanged(state)
        } else _audioState.value = state
    }

    @androidx.annotation.RequiresApi(34)
    fun updateAvailableAudioEndpoints(endpoints: List<CallEndpoint>) {
        endpointRouter?.availableChanged(endpoints)
    }

    @androidx.annotation.RequiresApi(34)
    fun updateCurrentAudioEndpoint(endpoint: CallEndpoint) {
        endpointRouter?.currentChanged(endpoint)
    }

    @androidx.annotation.RequiresApi(34)
    fun updateMuteState(muted: Boolean) {
        endpointRouter?.muteChanged(muted)
    }

    override fun setIsActivityVisible(visible: Boolean) {
        _isActivityVisible.value = visible
    }

    override fun setPreferredCall(call: Call?) {
        _preferredCall.value = call
    }

    override fun getPreferredCall(): Call? = _preferredCall.value

    override fun answerCall() {
        _currentCallSession.value?.call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    override fun declineCall() {
        val call = _currentCallSession.value?.call ?: return
        val isRinging = call.currentTelecomState() == Call.STATE_RINGING
        try {
            if (isRinging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call.reject(Call.REJECT_REASON_DECLINED)
            } else {
                call.disconnect()
            }
        } catch (error: Exception) {
            Log.w(CALL_REPOSITORY_LOG_TAG, "Unable to reject the ringing call; disconnecting", error)
            try {
                call.disconnect()
            } catch (fallbackError: Exception) {
                Log.w(CALL_REPOSITORY_LOG_TAG, "Fallback call disconnect failed", fallbackError)
            }
        }
    }

    override fun mute(muted: Boolean) {
        inCallService?.setMuted(muted)
    }

    override fun toggleMute() {
        val currentMute = _audioState.value?.isMuted ?: false
        mute(!currentMute)
    }

    // Use confirmed modern endpoints on API34+, preserving legacy before the first list and on29–33.
    @Suppress("DEPRECATION")
    override fun setAudioRoute(route: Int) {
        if (Build.VERSION.SDK_INT >= 34 && endpointRouter?.setRoute(route) == true) return
        inCallService?.setAudioRoute(route)
    }

    override fun cycleAudioRoute() {
        val state = _audioState.value ?: return
        nextCallAudioRoute(state.route, state.supportedRouteMask)?.let(::setAudioRoute)
    }
}
