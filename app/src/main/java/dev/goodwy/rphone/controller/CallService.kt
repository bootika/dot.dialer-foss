package dev.goodwy.rphone.controller
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.BlockedNumberContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.widget.Toast
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.controller.util.resolvePreferredTelecomCall
import dev.goodwy.rphone.controller.util.selectPriorityTelecomCall
import dev.goodwy.rphone.controller.util.unregisterCallbackSafely
import dev.goodwy.rphone.controller.util.toast
import io.github.bootika.dotdialer.core.call.CallLifecycleCoordinator
import io.github.bootika.dotdialer.core.call.CallLifecycleEvent
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticComponent
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticEvent
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticOperation
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import dev.goodwy.rphone.view.screen.BiometricCallActivity
import io.github.bootika.dotdialer.core.call.CallPresentationPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.getValue
import kotlin.time.Duration.Companion.milliseconds
class CallService : InCallService() {
    private val preferenceManager: PreferenceManager by inject()
    private val callStateManager: CallStateManager by inject()
    private val notificationManager: CallNotificationManager by inject()
    private val callRepository: ICallRepository by inject()
    private val contactsRepository: dev.goodwy.rphone.modal.`interface`.IContactsRepository by inject()
    private val photoHydration: dev.goodwy.rphone.controller.util.CallPhotoHydrationManager by inject()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val photoSessions by lazy {
        CallPhotoSessionCoordinator(serviceScope, callStateManager, photoHydration::hydrateForCall,
            (contactsRepository as? dev.goodwy.rphone.modal.repository.CachingContactsRepository)?.changes
                ?: kotlinx.coroutines.flow.emptyFlow(),
            isPresent = { callSessionIds[it]?.let(lifecycleCoordinator.state.value.sessions::containsKey) == true && it.currentTelecomState() !in
                listOf(Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING) },
            regionForCall = { dev.goodwy.rphone.controller.util.resolveContactLookupRegion(this, it.details?.accountHandle) },
            diagnostics = if (dev.goodwy.rphone.BuildConfig.DEBUG) io.github.bootika.dotdialer.diagnostics.CallPhotoDiagnostics(this, serviceScope, callStateManager) { callSessionIds[it] } else null)
    }
    private var redialCount = 0
    private val callStartTimes = mutableMapOf<Call, Long>()
    private var lastFloatingCallMetadata: Triple<String, String, String?>? = null
    private var notificationUpdateJob: Job? = null
    private var notificationAttempt = 0
    private val lifecycleCoordinator = CallLifecycleCoordinator()
    private val callSessionIds = IdentityHashMap<Call, String>()
    private val registeredCalls = Collections.newSetFromMap(IdentityHashMap<Call, Boolean>())
    private val pendingFullScreenLaunchSessionIds = mutableSetOf<String>()
    private var nextCallSessionId = 1L
    private var lastDiagnosticLifecycleSummary: String? = null
    // BroadcastReceiver for monitoring when the screen is locked or turned off
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    callRepository.currentCallSession.value?.call?.let { currentCall ->
                        updateNotification(currentCall)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppDiagnostics.record(DiagnosticEvent.CallServiceStarted)
        (callRepository as? CallRepositoryImpl)?.bindService(this)

        // Register the receiver to track screen locks
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT) // Screen Unlocked
        }
        registerReceiver(screenStateReceiver, filter)

        serviceScope.launch {
            callRepository.isActivityVisible.collect {
                callRepository.currentCallSession.value?.call?.let { currentCall ->
                    updateNotification(currentCall)
                }
            }
        }
        serviceScope.launch {
            callStateManager.metadataVersion.collect {
                callRepository.currentCallSession.value?.call?.let { currentCall ->
                    updateNotification(currentCall)
                }
            }
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onConnectionEvent(call: Call, event: String, extras: android.os.Bundle?) {
            super.onConnectionEvent(call, event, extras)
            val number = call.details?.handle?.schemeSpecificPart?.let { android.net.Uri.decode(it) } ?: ""
            if (isUssdNumber(number)) {
                val resp = extras?.let { b ->
                    b.getString("ussdResult") ?: b.getString("android.telecom.extra.ussd_message")
                    ?: b.getString("android.telephony.extra.USSD_RESPONSE")
                    ?: b.getString("response") ?: b.getString("result") ?: b.getString("data") ?: b.getString("message")
                }
                if (!resp.isNullOrBlank()) UssdRepository.post(number, resp)
            }
        }

        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            callSessionIds[call]?.let { sessionId ->
                val phase = telecomCallPhase(state)
                lifecycleCoordinator.dispatch(
                    CallLifecycleEvent.PhaseChanged(sessionId, phase)
                )
                AppDiagnostics.record(
                    DiagnosticEvent.CallPhaseChanged(
                        sessionId = sessionId,
                        phase = phase.name,
                    )
                )
            }
            updateCallState()

            if (state != Call.STATE_RINGING) {
                callSessionIds[call]?.let(pendingFullScreenLaunchSessionIds::remove)
            }

            if (state == Call.STATE_ACTIVE) {
                redialCount = 0
            }

            if (state == Call.STATE_DISCONNECTED) {
                val cause = call.details.disconnectCause
                handleDisconnect(call, cause)
                photoSessions.remove(call)

            } else {
                photoSessions.update(call)
                updateNotification(call)
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            photoSessions.update(call)
            updateCallState()
            updateNotification(call)
        }

        override fun onChildrenChanged(call: Call, children: MutableList<Call>) {
            photoSessions.update(call)
            updateCallState()
        }
        override fun onParentChanged(call: Call, parent: Call?) {
            photoSessions.update(call)
            updateCallState()
        }
        override fun onConferenceableCallsChanged(call: Call, calls: MutableList<Call>) = updateCallState()
    }

    private fun handleDisconnect(call: Call, cause: DisconnectCause?) {
        val number = call.details.handle?.schemeSpecificPart ?: ""

        // Auto Redial on Busy
        if (cause?.code == DisconnectCause.BUSY &&
            preferenceManager.getBoolean(PreferenceManager.KEY_AUTO_REDIAL_BUSY, false)) {

            val maxAttempts = preferenceManager.getInt(PreferenceManager.KEY_REDIAL_ATTEMPTS, 3)
            val delayMs = preferenceManager.getInt(PreferenceManager.KEY_REDIAL_DELAY, 3000).toLong()

            if (redialCount < maxAttempts) {
                redialCount++
                serviceScope.launch {
                    delay(delayMs.milliseconds)
                    val callUri = Uri.fromParts("tel", number, null)
                    val intent = Intent(Intent.ACTION_CALL, callUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }
        }

        val wasNeverConnected = call.details.connectTimeMillis == 0L
        val isOutgoing = call.details.callDirection == Call.Details.DIRECTION_OUTGOING

        if (isOutgoing && wasNeverConnected) {
            val failMessage = when {
                dev.goodwy.rphone.controller.util.isAirplaneModeOn(this) ->
                    getString(R.string.call_failed_airplane_mode)
                cause?.code == DisconnectCause.RESTRICTED ->
                    getString(R.string.call_failed_restricted)
                cause?.code == DisconnectCause.ERROR ->
                    cause.description?.toString()?.takeIf { it.isNotBlank() } ?: getString(R.string.call_failed_generic)
                else -> null
            }
            if (failMessage != null) {
                serviceScope.launch(Dispatchers.Main) {
                    toast(failMessage, Toast.LENGTH_LONG)
                }
            }
        }

        if (shouldNotifyMissedCall(call.details)) {
            // Capture metadata before onCallRemoved clears it, and do not tie delivery to
            // the service scope which is cancelled immediately after the last call ends.
            val contactName = getContactNameFromCache(number, call)
            val photoUri = getContactPhotoFromCache(number, call)
            val showBlocked = preferenceManager.getInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, 0) == 1
            notificationManager.enqueueMissedCallNotification(call, contactName, photoUri) {
                !isNumberBlocked(number) || showBlocked
            }
        }
    }

    private suspend fun isNumberBlocked(number: String): Boolean = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext false
        return@withContext try {
            BlockedNumberContract.isBlocked(this@CallService, number)
        } catch (error: Exception) {
            android.util.Log.w("CallService", "Unable to query the blocked-number provider", error)
            false
        }
    }

    private fun handleBlockedCall(call: Call, number: String) {
        val method = preferenceManager.getInt(PreferenceManager.KEY_BLOCK_METHOD, 0) // 0: Decline, 1: Silent

        if (method == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call.reject(Call.REJECT_REASON_DECLINED)
            } else {
                call.disconnect()
            }
        }

        if (preferenceManager.getBoolean(PreferenceManager.KEY_BLOCK_NOTIFICATION, true)) {
            notificationManager.showBlockedNotification(number)
        }
    }

    private fun getContactNameFromCache(number: String, call: Call): String {
        if (number.isEmpty()) return getString(R.string.label_unknown_number)
        val metadata = callStateManager.callerMetadataMap.value[number]?.forLookupRegion(dev.goodwy.rphone.controller.util.resolveContactLookupRegion(this, call.details?.accountHandle))
        return if (metadata != null && metadata.name.isNotEmpty()) {
            metadata.name
        } else {
            number
        }
    }

    private fun getContactPhotoFromCache(number: String, call: Call): String? {
        val metadata = callStateManager.callerMetadataMap.value[number]?.forLookupRegion(dev.goodwy.rphone.controller.util.resolveContactLookupRegion(this, call.details?.accountHandle))
        return metadata?.photoUri
    }

    private fun updateCallState() {
        val callsList = calls.orEmpty().filter { call ->
            call in callSessionIds && call.currentTelecomState() != Call.STATE_DISCONNECTED
        }
        lifecycleCoordinator.dispatch(
            CallLifecycleEvent.Reconciled(
                callsList.associate { call ->
                    callSessionIds.getValue(call) to telecomCallPhase(call.currentTelecomState())
                }
            )
        )
        val lifecycleState = lifecycleCoordinator.state.value
        val surfaces = lifecycleState.surfaces
        val diagnosticSummary = listOf(
            callsList.size,
            lifecycleState.foregroundSession?.phase,
            surfaces.showCallUi,
            surfaces.showIncomingNotification,
            surfaces.showOngoingNotification,
            surfaces.showFloatingUi,
        ).joinToString(separator = ":")
        if (diagnosticSummary != lastDiagnosticLifecycleSummary) {
            lastDiagnosticLifecycleSummary = diagnosticSummary
            AppDiagnostics.record(
                DiagnosticEvent.CallsReconciled(
                    activeCallCount = callsList.size,
                    foregroundPhase = lifecycleState.foregroundSession?.phase?.name,
                    showCallUi = surfaces.showCallUi,
                    showNotification = surfaces.showIncomingNotification || surfaces.showOngoingNotification,
                    showFloatingUi = surfaces.showFloatingUi,
                )
            )
        }
        callRepository.updateAllCalls(callsList)

        callsList.forEach { c ->
            if (c.currentTelecomState() == Call.STATE_ACTIVE || c.currentTelecomState() == Call.STATE_HOLDING) {
                val detailsTime = c.details.connectTimeMillis
                if (detailsTime > 0) {
                    callStartTimes[c] = detailsTime
                } else if (c.currentTelecomState() == Call.STATE_ACTIVE && !callStartTimes.containsKey(c)) {
                    callStartTimes[c] = System.currentTimeMillis()
                }
            }
        }
        callStartTimes.keys.retainAll(callsList.toSet())

        val preferred = resolvePreferredTelecomCall(callsList, callRepository.getPreferredCall())
        if (preferred !== callRepository.getPreferredCall()) callRepository.setPreferredCall(preferred)
        val priorityCall = selectPriorityTelecomCall(callsList, preferred)

        if (priorityCall != null) {
            val connectTime = callStartTimes[priorityCall] ?: 0L
            callRepository.updateCurrentCallSession(CallSession(priorityCall, priorityCall.currentTelecomState(), connectTimeMillis = connectTime))
        } else {
            callRepository.updateCurrentCallSession(null)
            clearActiveCallSurfaces()
        }
    }

    private fun isDeviceLocked(): Boolean {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        return keyguardManager.isKeyguardLocked
    }

    private fun updateNotification(
        call: Call,
        forcedHigh: Boolean? = null,
        suppressIncomingAlert: Boolean = false,
    ) {
        val sessionId = callSessionIds[call] ?: return
        if (!canPublishCallNotification(sessionId)) {
            val surfaces = lifecycleCoordinator.state.value.surfaces
            if (!surfaces.showIncomingNotification && !surfaces.showOngoingNotification) {
                clearActiveCallSurfaces()
            }
            return
        }

        notificationUpdateJob?.cancel()
        val trace = beginCallPhotoTrace(sessionId, ++notificationAttempt)
        notificationUpdateJob = serviceScope.launch {
            val handle = call.details.handle
            val number = handle?.schemeSpecificPart?.let(Uri::decode) ?: ""
            val contactName = getContactNameFromCache(number, call)
            val photoUri = notificationCallPhotoUri(call, getContactPhotoFromCache(number, call))
            val published = publishCallPhotoNotification(photoUri != null,
                decode = { notificationManager.getContactBitmap(photoUri) },
                current = { isActive && canPublishCallNotification(sessionId) },
                build = { photo -> notificationManager.buildCallNotification(call, contactName, photo,
                    callRepository.audioState.value, forcedHigh ?: isDeviceLocked(),
                    suppressIncomingAlert = suppressIncomingAlert || callRepository.isActivityVisible.value ||
                        sessionId in pendingFullScreenLaunchSessionIds) },
                publish = { notification -> startForeground(CallNotificationManager.NOTIFICATION_ID,
                    notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL) }, trace = trace)
            if (!published) return@launch

            // Start/stop floating bubble based on preference
            if (lifecycleCoordinator.state.value.surfaces.showFloatingUi) {
                maybeStartFloatingCall(contactName, number, photoUri)
            } else {
                FloatingCallService.stop(this@CallService)
            }
        }.also { it.completeCallPhotoTrace(trace) }
    }

    private fun canPublishCallNotification(sessionId: String): Boolean {
        val lifecycle = lifecycleCoordinator.state.value
        val surfaces = lifecycle.surfaces
        return lifecycle.foregroundSessionId == sessionId &&
            (surfaces.showIncomingNotification || surfaces.showOngoingNotification)
    }

    private fun clearActiveCallSurfaces() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
        removeForeground()
        cancelNotification()
        FloatingCallService.stop(this)
        lastFloatingCallMetadata = null
    }

    private fun removeForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun launchBiometricCallActivity(action: String) {
        val intent = Intent(this, BiometricCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("NOTIFICATION_PENDING_ACTION", action)
        }
        startActivity(intent)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        redialCount = 0

        val number = call.details.handle?.schemeSpecificPart?.let { Uri.decode(it) } ?: ""
        // ── USSD / MMI outgoing calls ────────────────────────────────────────
        val isUssd = call.currentTelecomState() != Call.STATE_RINGING && isUssdNumber(number)
        val isIncomingRinging = call.currentTelecomState() == Call.STATE_RINGING
        val fullscreenCalls = preferenceManager.getBoolean(
            PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS,
            CallPresentationPolicy.DEFAULT_ALWAYS_FULL_SCREEN,
        )
        if (!isUssd) {
            val sessionId = sessionIdFor(call)
            val initialPhase = telecomCallPhase(call.currentTelecomState())
            lifecycleCoordinator.dispatch(
                CallLifecycleEvent.CallAdded(sessionId, initialPhase)
            )
            AppDiagnostics.record(
                DiagnosticEvent.CallAdded(
                    sessionId = sessionId,
                    phase = initialPhase.name,
                    activeCallCount = callSessionIds.size,
                )
            )
            if (isIncomingRinging && fullscreenCalls) {
                // Protect the interval between requesting CallActivity and receiving its visible
                // callback. Any Telecom state update in that interval must stay low priority.
                pendingFullScreenLaunchSessionIds += sessionId
            }
        }
        registerCallCallback(call)
        if (isUssd) return
        photoSessions.update(call)
        // Update call state synchronously so CallActivity sees active calls immediately
        updateCallState()

        serviceScope.launch {
            if (isNumberBlocked(number)) {
                handleBlockedCall(call, number)
                return@launch
            }

            val activityLaunchStarted = CallPresentationPolicy.shouldLaunchCallActivity(
                isIncomingRinging = isIncomingRinging,
                alwaysFullScreen = fullscreenCalls,
            ) && this@CallService.launchCallActivitySafely(outgoing = !isIncomingRinging)
            val sessionId = callSessionIds[call]
            if (!activityLaunchStarted && sessionId != null) {
                pendingFullScreenLaunchSessionIds.remove(sessionId)
            }
            val suppressIncomingAlert = activityLaunchStarted && isIncomingRinging && fullscreenCalls
            updateNotification(
                call,
                suppressIncomingAlert = suppressIncomingAlert,
            )
            if (suppressIncomingAlert) {
                serviceScope.launch {
                    val activityDidNotAppear = incomingCallActivityDidNotAppear(call) {
                        callRepository.isActivityVisible.value
                    }
                    sessionId?.let(pendingFullScreenLaunchSessionIds::remove)
                    if (activityDidNotAppear) {
                        updateNotification(call)
                    }
                }
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        unregisterCallCallback(call)
        callSessionIds.remove(call)?.let { sessionId ->
            pendingFullScreenLaunchSessionIds.remove(sessionId)
            lifecycleCoordinator.dispatch(CallLifecycleEvent.CallRemoved(sessionId))
            AppDiagnostics.record(
                DiagnosticEvent.CallRemoved(
                    sessionId = sessionId,
                    remainingCallCount = callSessionIds.size,
                )
            )
        }

        // If the call being deleted is the same one for which a floating window was launched,
        // we must clear the cache to allow it to be launched for the next call.
        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val currentMetadata = lastFloatingCallMetadata
        if (currentMetadata != null && currentMetadata.second == number) {
            lastFloatingCallMetadata = null
        }

        photoSessions.remove(call)
        updateCallState()
        if (callRepository.allCalls.value.isEmpty()) CallUiHandoff.controller.reset()

        if (callRepository.allCalls.value.isNotEmpty()) {
            callRepository.currentCallSession.value?.call?.let { updateNotification(it) }
        }
    }

    // Required on API29–33 and as a fallback until the first modern endpoint list arrives.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        callRepository.updateAudioState(audioState)
        publishCallAudioState(callRepository, photoSessions::audioChanged, ::updateNotification)
    }
    @androidx.annotation.RequiresApi(34) override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<android.telecom.CallEndpoint>) {
        (callRepository as? CallRepositoryImpl)?.updateAvailableAudioEndpoints(availableEndpoints)
        publishCallAudioState(callRepository, photoSessions::audioChanged, ::updateNotification)
    }
    @androidx.annotation.RequiresApi(34) override fun onCallEndpointChanged(callEndpoint: android.telecom.CallEndpoint) {
        (callRepository as? CallRepositoryImpl)?.updateCurrentAudioEndpoint(callEndpoint)
        publishCallAudioState(callRepository, photoSessions::audioChanged, ::updateNotification)
    }
    @androidx.annotation.RequiresApi(34) override fun onMuteStateChanged(isMuted: Boolean) {
        (callRepository as? CallRepositoryImpl)?.updateMuteState(isMuted)
        publishCallAudioState(callRepository, photoSessions::audioChanged, ::updateNotification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleCallServiceAction(
            service = this,
            intent = intent,
            preferenceManager = preferenceManager,
            callRepository = callRepository,
            launchBiometric = ::launchBiometricCallActivity,
        )
        return super.onStartCommand(intent, flags, startId)
    }

    private fun cancelNotification() {
        notificationManager.cancelNotification()
    }

    override fun onDestroy() {
        AppDiagnostics.record(DiagnosticEvent.CallServiceStopped(callSessionIds.size))
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (error: Exception) {
            AppDiagnostics.record(
                DiagnosticEvent.Failure(
                    component = DiagnosticComponent.CALL_SERVICE,
                    operation = DiagnosticOperation.UNREGISTER_RECEIVER,
                    errorType = error.javaClass.simpleName,
                )
            )
        }

        photoSessions.close()
        registeredCalls.toList().forEach(::unregisterCallCallback)
        lifecycleCoordinator.dispatch(CallLifecycleEvent.ServiceStopped)
        callSessionIds.clear()
        callStartTimes.clear()
        callRepository.updateAllCalls(emptyList())
        callRepository.updateCurrentCallSession(null)
        callRepository.updateAudioState(null)
        callRepository.setPreferredCall(null)
        clearActiveCallSurfaces()
        (callRepository as? CallRepositoryImpl)?.unbindService()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun sessionIdFor(call: Call): String = callSessionIds[call] ?: run {
        val id = "call-${nextCallSessionId++}"
        callSessionIds[call] = id
        id
    }

    private fun registerCallCallback(call: Call) {
        if (registeredCalls.add(call)) {
            call.registerCallback(callCallback)
        }
    }

    private fun unregisterCallCallback(call: Call) {
        if (registeredCalls.remove(call)) {
            call.unregisterCallbackSafely(callCallback)
        }
    }

    private fun maybeStartFloatingCall(contactName: String, number: String, photoUri: String?) {
        if (!preferenceManager.getBoolean(PreferenceManager.KEY_FLOATING_CALL, false)) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        val metadata = Triple(contactName, number, photoUri)
        if (lastFloatingCallMetadata == metadata) return
        lastFloatingCallMetadata = metadata

        FloatingCallService.start(this, contactName, number, photoUri)
    }

    /** Returns true for any MMI / USSD code like *124# *#06# ##002# *21*N# */
    private fun isUssdNumber(number: String): Boolean {
        if (number.isBlank()) return false
        val n = android.net.Uri.decode(number).trim()
        return (n.startsWith("*") || n.startsWith("#")) && n.endsWith("#")
    }
}
