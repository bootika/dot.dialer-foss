package dev.goodwy.rphone.controller

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.TelecomManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.AndroidHaptics
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.currentTelecomState
import io.github.bootika.dotdialer.core.haptics.HapticIntent
import io.github.bootika.dotdialer.core.call.CallPresentationPolicy
import io.github.bootika.dotdialer.core.diagnostics.ActivityStage
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticEvent
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.view.screen.ExpressiveCallScreen
import dev.goodwy.rphone.view.screen.callSystemBarUsesLightIcons
import dev.goodwy.rphone.view.theme.DotDialerAppTheme
import kotlinx.coroutines.delay
import org.koin.android.ext.android.inject
import java.util.function.Consumer
import kotlin.getValue
import kotlin.time.Duration.Companion.milliseconds

// Acrylic-style blur: enough to retain only broad colour and luminance from the window below.
private const val CALL_FROSTED_WINDOW_BLUR_DP = 80f
class CallActivity : FragmentActivity() { //ComponentActivity()

    private val contactsRepo: IContactsRepository by inject()
    private val preferenceManager: PreferenceManager by inject()
    private val callViewModel: CallViewModel by inject()
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private val isFinishingCall = java.util.concurrent.atomic.AtomicBoolean(false)
    private val outgoingLaunch = PendingOutgoingCallLaunch()
    private val pendingOutgoingLaunch get() = outgoingLaunch.isPending
    private val identityResolver by lazy { CallIdentityResolver(contactsRepo) }
    private var crossWindowBlurEnabled by mutableStateOf(false)
    private var crossWindowBlurListener: Consumer<Boolean>? = null

    companion object {
        const val EXTRA_PENDING_OUTGOING_CALL = "pending_outgoing_call"
        private const val PROXIMITY_LOCK_TIMEOUT_MILLIS = 60 * 60 * 1_000L

        /** FloatingCallService observes this to hide the bubble when CallActivity is visible. */
        val isInForeground = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupCrossWindowBlurCapability()

        outgoingLaunch.restore(savedInstanceState, intent.getBooleanExtra(EXTRA_PENDING_OUTGOING_CALL, false))

        CallBackgroundStore.attach(preferenceManager)

        val isTelecomInCall = telecomInCall()
        AppDiagnostics.record(
            DiagnosticEvent.CallActivityLifecycle(
                stage = ActivityStage.CREATED,
                pendingOutgoingLaunch = pendingOutgoingLaunch,
                telecomInCall = isTelecomInCall,
            )
        )
        if (
            CallPresentationPolicy.shouldFinishBeforeRendering(
                repositoryHasActiveCall = hasManagedCall(),
                pendingOutgoingLaunch = pendingOutgoingLaunch,
            )
        ) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            finishAndRemoveTask()
            return
        }

        turnScreenOnAndShowWhileLocked()
        applyKeepScreenOnPreference()

        setupProximitySensor()
        applySystemBarStyle(dev.goodwy.rphone.view.theme.resolveDarkTheme(
            preferenceManager.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto", isNightMode(),
        ))

        setContent {
            DotDialerAppTheme {
                dev.goodwy.rphone.view.screen.settings.PlanUpgradeHost()
                val session by callViewModel.currentCallSession.collectAsStateWithLifecycle()
                val audioState by callViewModel.audioState.collectAsStateWithLifecycle()
                val callerMetadata by callViewModel.callerMetadataMap.collectAsStateWithLifecycle()
                val settingsState by preferenceManager.settingsChanged.collectAsStateWithLifecycle()
                var retainedSession by remember { mutableStateOf<CallSession?>(null) }
                val awaitingOutgoingSession = pendingOutgoingLaunch
                LaunchedEffect(outgoingLaunch.deadlineMillis) {
                    if (outgoingLaunch.awaitExpiry()) dismissCallScreen()
                }
                LaunchedEffect(session, outgoingLaunch.deadlineMillis) {
                    session?.let {
                        retainedSession = it
                        outgoingLaunch.consume()
                    }
                }

                val displaySession = session ?: retainedSession
                val displayCall = displaySession?.call
                val callState = session?.state
                LaunchedEffect(displayCall) {
                    if (displayCall != null) {
                        val transfer = CallUiHandoff.controller.state.value?.token
                        androidx.compose.runtime.withFrameNanos { }
                        transfer?.let { CallUiHandoff.controller.contentReady(it) }
                    }
                }

                val identityVersion = rememberPhotoHydrationIdentityVersion(settingsState)
                val identity = if (displayCall != null) {
                    identityResolver.rememberIdentity(displayCall, identityVersion, callerMetadata[eligibleCallPhotoNumber(displayCall)])
                } else {
                    null
                }

                val wantsFrostedWindow = remember(settingsState) {
                    preferenceManager.getBoolean(
                        PreferenceManager.KEY_FROSTED_CALL_BACKGROUND,
                        PreferenceManager.DEFAULT_FROSTED_CALL_BACKGROUND,
                    )
                }
                val frostedCallEffect = remember(wantsFrostedWindow, crossWindowBlurEnabled) {
                    resolveFrostedCallEffect(wantsFrostedWindow, crossWindowBlurEnabled)
                }
                val lightSystemBarIcons = callSystemBarUsesLightIcons(MaterialTheme.colorScheme.surface)

                DisposableEffect(frostedCallEffect.backgroundBlur) {
                    applyFrostedWindow(frostedCallEffect.backgroundBlur)
                    onDispose { }
                }

                androidx.compose.runtime.SideEffect {
                    dev.goodwy.rphone.view.theme.applySystemBarIconAppearance(window, darkTheme = lightSystemBarIcons)
                }

                LaunchedEffect(settingsState) {
                    applyKeepScreenOnPreference()
                }

                LaunchedEffect(callState, settingsState, audioState) {
                    when (callState) {
                        Call.STATE_ACTIVE, Call.STATE_DIALING -> {
                            if (preferenceManager.getBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, true)) {
                                acquireProximityLock()
                            } else {
                                releaseProximityLock()
                            }
                        }

                        else -> releaseProximityLock()
                    }
                }

                LaunchedEffect(callState) {
                    when (callState) {
                        Call.STATE_ACTIVE -> AndroidHaptics.performOnView(
                            view = this@CallActivity.window.decorView,
                            intent = HapticIntent.CONFIRM,
                            appEnabled = preferenceManager.getBoolean(
                                PreferenceManager.KEY_VIBRATE_ON_ANSWER,
                                true,
                            ),
                        )

                        Call.STATE_DISCONNECTED -> {
                            AndroidHaptics.performOnView(
                                view = this@CallActivity.window.decorView,
                                intent = HapticIntent.REJECT,
                                appEnabled = preferenceManager.getBoolean(
                                    PreferenceManager.KEY_VIBRATE_ON_HANGUP,
                                    false,
                                ),
                            )
                            releaseProximityLock()
                            delay(400.milliseconds)
                            dismissCallScreen()
                        }
                    }
                }

                LaunchedEffect(session, awaitingOutgoingSession) {
                    if (session == null) {
                        delay(
                            CallPresentationPolicy.missingSessionDismissDelayMillis(
                                awaitingOutgoingSession = awaitingOutgoingSession,
                            ).milliseconds
                        )
                        dismissCallScreen()
                    }
                }

                CallSurfaceTransition(displayCall) { targetCall ->
                        val answeredFromNotification = intent?.getBooleanExtra("ANSWERED_FROM_NOTIFICATION", false) ?: false

                        if (targetCall == null) {
                            Box(modifier = Modifier.fillMaxSize())
                        } else {
                            val isDisplayed = targetCall === displayCall
                            val targetIdentity =
                                if (isDisplayed && identity != null) {
                                    identity
                                } else {
                                    identityResolver.rememberIdentity(targetCall, identityVersion, callerMetadata[eligibleCallPhotoNumber(targetCall)])
                                }
                            val targetState = if (isDisplayed) {
                                checkNotNull(displaySession).state
                            } else {
                                targetCall.currentTelecomState()
                            }
                            val connectTime = if (isDisplayed) {
                                checkNotNull(displaySession).connectTimeMillis
                            } else {
                                targetCall.details?.connectTimeMillis ?: 0L
                            }

                            val liquidGlassBackdrop = rememberLayerBackdrop()
                            CompositionLocalProvider(LocalLiquidGlassBackdrop provides liquidGlassBackdrop,
                                dev.goodwy.rphone.view.components.LocalCallPhotoRevision provides identityVersion,
                                dev.goodwy.rphone.view.components.LocalCallTapHapticsEnabled provides preferenceManager.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                ExpressiveCallScreen(
                                    call = targetCall,
                                    callState = targetState,
                                    contactName = targetIdentity.name,
                                    companyName = targetIdentity.company,
                                    phoneNumber = targetIdentity.number,
                                    photoUri = targetIdentity.photoUri,
                                    photoLookupComplete = targetIdentity.photoLookupComplete,
                                    audioState = audioState,
                                    initialConnectTime = connectTime,
                                    backgroundUri = targetIdentity.backgroundUri,
                                    skipIncomingScreen = answeredFromNotification,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    frostedWindow = frostedCallEffect.translucentBackground,
                                )
                            }
                        }
                }
            }
        }
    }

    private fun isNightMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun applySystemBarStyle(lightIcons: Boolean) {
        val style = if (lightIcons) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        dev.goodwy.rphone.view.theme.applySystemBarIconAppearance(window, darkTheme = lightIcons)
    }

    override fun onResume() {
        super.onResume()
        AppDiagnostics.record(
            DiagnosticEvent.CallActivityLifecycle(
                stage = ActivityStage.RESUMED,
                pendingOutgoingLaunch = pendingOutgoingLaunch,
                telecomInCall = telecomInCall(),
            )
        )
        if (hasPresentableCall()) {
            turnScreenOnAndShowWhileLocked()
            applyKeepScreenOnPreference()
        } else {
            dismissCallScreen()
            return
        }
        isInForeground.value = true
        val state = callViewModel.currentCallSession.value?.state
        if (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING) {
            acquireProximityLock()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outgoingLaunch.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()
        isInForeground.value = false
        CallUiHandoff.controller.contentReady()
        releaseProximityLock()
    }

    private fun setupProximitySensor() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "RillPhoneApp::ProximityWakeLock"
            )
        }
    }

    override fun onDestroy() {
        AppDiagnostics.record(
            DiagnosticEvent.CallActivityLifecycle(
                stage = ActivityStage.DESTROYED,
                pendingOutgoingLaunch = pendingOutgoingLaunch,
                telecomInCall = telecomInCall(),
            )
        )
        releaseProximityLock()
        proximityWakeLock = null
        callViewModel.setIsActivityVisible(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = crossWindowBlurListener
            if (listener != null) {
                getSystemService(WindowManager::class.java)
                    .removeCrossWindowBlurEnabledListener(listener)
            }
        }
        super.onDestroy()
    }

    private fun setupCrossWindowBlurCapability() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val windowManager = getSystemService(WindowManager::class.java)
        crossWindowBlurEnabled = windowManager.isCrossWindowBlurEnabled
        val listener = Consumer<Boolean> { enabled ->
            crossWindowBlurEnabled = enabled
        }
        crossWindowBlurListener = listener
        windowManager.addCrossWindowBlurEnabledListener(listener)
    }

    private fun applyFrostedWindow(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        val layoutParams = window.attributes
        layoutParams.setBlurBehindRadius(
            if (enabled) {
                (CALL_FROSTED_WINDOW_BLUR_DP * resources.displayMetrics.density).toInt()
            } else {
                0
            },
        )
        window.attributes = layoutParams
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        outgoingLaunch.start(intent.getBooleanExtra(EXTRA_PENDING_OUTGOING_CALL, false))
        AppDiagnostics.record(
            DiagnosticEvent.CallActivityLifecycle(
                stage = ActivityStage.NEW_INTENT,
                pendingOutgoingLaunch = pendingOutgoingLaunch,
                telecomInCall = telecomInCall(),
            )
        )
        if (!isFinishingCall.get() && hasPresentableCall()) {
            turnScreenOnAndShowWhileLocked()
            applyKeepScreenOnPreference()
        } else {
            dismissCallScreen()
        }
    }

    private fun dismissCallScreen() {
        if (isFinishingCall.getAndSet(true)) return

        if (hasPresentableCall()) {
            isFinishingCall.set(false)
            return
        }

        finishCallTaskWithoutTransition()
    }

    /** Global Telecom state is diagnostic: an external app's call does not own this screen. */
    private fun telecomInCall(): Boolean? = try {
        (getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.isInCall
    } catch (error: SecurityException) {
        Log.w("CallActivity", "Permission denied while reading Telecom call state", error)
        null
    } catch (error: RuntimeException) {
        Log.w("CallActivity", "Android Telecom could not report call state", error)
        null
    }

    private fun turnScreenOnAndShowWhileLocked() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }

    private fun applyKeepScreenOnPreference() {
        if (preferenceManager.getBoolean(PreferenceManager.KEY_KEEP_SCREEN_ON, true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun hasManagedCall(): Boolean = callViewModel.allCalls.value.any {
        it.currentTelecomState() != Call.STATE_DISCONNECTED &&
            it.currentTelecomState() != Call.STATE_DISCONNECTING
    }

    private fun hasPresentableCall(): Boolean {
        return !CallPresentationPolicy.shouldFinishBeforeRendering(
            repositoryHasActiveCall = hasManagedCall(),
            pendingOutgoingLaunch = pendingOutgoingLaunch,
        )
    }

    override fun onStart() {
        super.onStart()
        callViewModel.setIsActivityVisible(true)
    }

    override fun onStop() {
        super.onStop()
        AppDiagnostics.record(
            DiagnosticEvent.CallActivityLifecycle(
                stage = ActivityStage.STOPPED,
                pendingOutgoingLaunch = pendingOutgoingLaunch,
                telecomInCall = telecomInCall(),
            )
        )
        if (proximityWakeLock?.isHeld != true) {
            callViewModel.setIsActivityVisible(false)
        }
    }

    private fun acquireProximityLock() {
        val route = callViewModel.audioState.value?.route
        val isHandsFree = route == android.telecom.CallAudioState.ROUTE_SPEAKER ||
                route == android.telecom.CallAudioState.ROUTE_BLUETOOTH ||
                route == android.telecom.CallAudioState.ROUTE_WIRED_HEADSET

        if (!isHandsFree && preferenceManager.getBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, true)) {
            try {
                proximityWakeLock?.let { if (!it.isHeld) it.acquire(PROXIMITY_LOCK_TIMEOUT_MILLIS) }
            } catch (e: Exception) {
                Log.e("CallActivity", "Failed to acquire proximity lock", e)
            }
        } else {
            releaseProximityLock()
        }
    }

    private fun releaseProximityLock() {
        try {
            proximityWakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.e("CallActivity", "Failed to release proximity lock", e)
        }
    }
}
