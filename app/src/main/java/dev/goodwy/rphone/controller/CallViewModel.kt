package dev.goodwy.rphone.controller

import android.content.Context
import android.telecom.Call
import dev.goodwy.rphone.controller.util.currentTelecomState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.R
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.controller.util.loadPhoneAccount
import dev.goodwy.rphone.controller.util.elapsedTelecomCallSeconds
import dev.goodwy.rphone.domain.model.CallerMetadata
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CallViewModel(
    context: Context,
    private val callRepository: ICallRepository,
    private val callStateManager: CallStateManager
) : ViewModel() {

    private val appContext = context.applicationContext

    val currentCallSession: StateFlow<CallSession?> = callRepository.currentCallSession
    val allCalls: StateFlow<List<Call>> = callRepository.allCalls
    val audioState = callRepository.audioState
    val isCallUiVisible = callRepository.isActivityVisible
    val callerMetadataMap = callStateManager.callerMetadataMap
    
    val callerMetadata: StateFlow<CallerMetadata?> = combine(
        callRepository.currentCallSession,
        callStateManager.callerMetadataMap
    ) { session, metadataMap ->
        val number = session?.call?.details?.handle?.schemeSpecificPart ?: ""
        metadataMap[number]?.forLookupRegion(dev.goodwy.rphone.controller.util.resolveContactLookupRegion(appContext, session?.call?.details?.accountHandle))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val callDuration: StateFlow<Long> = callRepository.currentCallSession.flatMapLatest { session ->
        if (session == null || session.state !in listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING)) {
            flowOf(0L)
        } else {
            flow {
                val connectTime = session.connectTimeMillis
                while (true) {
                    val duration = elapsedTelecomCallSeconds(session.state, connectTime, System.currentTimeMillis()) ?: 0L
                    emit(duration)
                    delay(1000)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val simLabel: StateFlow<String?> = callRepository.currentCallSession.map { session ->
        val accountHandle = session?.call?.details?.accountHandle ?: return@map null
        val account = loadPhoneAccount(appContext, accountHandle)
        account?.label?.toString() ?: appContext.getString(R.string.call_screen_sim_label, accountHandle.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun answerCall() = callRepository.answerCall()
    fun declineCall() = callRepository.declineCall()
    /** A stale banner must never disconnect a replacement call, ringing call or every call. */
    fun endBannerCall(expectedCall: Call): Boolean {
        val current = currentCallSession.value ?: return false
        if (current.call !== expectedCall || allCalls.value.none { it === expectedCall } ||
            !isOngoingBannerState(current.state) ||
            !isOngoingBannerState(expectedCall.currentTelecomState())) return false
        return try {
            expectedCall.disconnect()
            true
        } catch (error: RuntimeException) {
            android.util.Log.w("CallViewModel", "Unable to end the displayed ongoing call", error)
            false
        }
    }
    fun toggleMute() = callRepository.toggleMute()
    fun cycleAudioRoute() = callRepository.cycleAudioRoute()
    fun setAudioRoute(route: Int) = callRepository.setAudioRoute(route)
    fun setPreferredCall(call: Call?) = callRepository.setPreferredCall(call)
    fun setIsActivityVisible(visible: Boolean) = callRepository.setIsActivityVisible(visible)
}

internal fun isOngoingBannerState(state: Int): Boolean = state in listOf(
    Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_SELECT_PHONE_ACCOUNT,
    Call.STATE_ACTIVE, Call.STATE_HOLDING,
)
