package dev.goodwy.rphone.view.screen

import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallActivity
import dev.goodwy.rphone.controller.CallViewModel
import dev.goodwy.rphone.controller.lock.AppLockManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.settings.PasswordDialogContent
import dev.goodwy.rphone.view.screen.settings.PinDialogContent
import dev.goodwy.rphone.view.theme.DotDialerAppTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

private const val BIOMETRIC_CALL_LOG_TAG = "BiometricCall"

private fun performAuthenticatedCallAction(call: Call?, action: String) {
    if (call == null) {
        Log.w(BIOMETRIC_CALL_LOG_TAG, "Unable to perform $action because no active call exists")
        return
    }
    try {
        when (action) {
            "ANSWER" -> call.answer(VideoProfile.STATE_AUDIO_ONLY)
            "DECLINE" -> call.disconnect()
            else -> Log.w(BIOMETRIC_CALL_LOG_TAG, "Ignoring unknown authenticated call action")
        }
    } catch (error: RuntimeException) {
        Log.w(BIOMETRIC_CALL_LOG_TAG, "Authenticated $action action failed", error)
    }
}

class BiometricCallActivity : FragmentActivity() {

    private val prefs: PreferenceManager by inject()
    private val callViewModel: CallViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val action = intent?.getStringExtra("NOTIFICATION_PENDING_ACTION") ?: run { finish(); return }
        val biometricType = prefs.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: ""
        val requestedCall = callViewModel.currentCallSession.value?.call ?: run { finish(); return }

        // Verify we should actually gate this specific call
        val callPhoneNumber = callViewModel.currentCallSession.value?.call?.details?.handle?.schemeSpecificPart
        if (!prefs.shouldGateCallWithBiometric(callPhoneNumber) || biometricType.isEmpty()) {
            // Lock scope excludes this number — perform action directly
            val call = callViewModel.currentCallSession.value?.call
            when (action) {
                "ANSWER" -> {
                    performAuthenticatedCallAction(call, action)
                    startActivity(Intent(this, CallActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        putExtra("ANSWERED_FROM_NOTIFICATION", true)
                    })
                }
                "DECLINE" -> performAuthenticatedCallAction(call, action)
            }
            finish()
            return
        }

        setContent {
            DotDialerAppTheme {
                val activity = this
                val call = callViewModel.currentCallSession.collectAsStateWithLifecycle().value?.call
                if (call != null) BiometricFloatingUi(
                    biometricType  = biometricType,
                    activity       = activity,
                    verifyPin = { prefs.verifyAuthentication("pin", it) },
                    verifyPassword = { prefs.verifyAuthentication("password", it) },
                    onSuccess = {
                        if (callViewModel.currentCallSession.value?.call !== requestedCall) {
                            finish()
                            return@BiometricFloatingUi
                        }
                        when (action) {
                            "ANSWER" -> {
                                performAuthenticatedCallAction(call, action)
                                startActivity(Intent(activity, CallActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                    putExtra("ANSWERED_FROM_NOTIFICATION", true)
                                })
                            }
                            "DECLINE" -> performAuthenticatedCallAction(call, action)
                        }
                        finish()
                    },
                    onDismiss = { finish() }
                ) else finish()
            }
        }
    }
}

@Composable
private fun BiometricFloatingUi(
    biometricType: String,
    activity: FragmentActivity,
    verifyPin: suspend (String) -> Boolean,
    verifyPassword: suspend (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { if (biometricType != "system") onDismiss() }
    ) {
        when (biometricType) {
            "system" -> {
                dev.goodwy.rphone.view.components.AuthenticationSystemPrompt(
                    activity = activity,
                    title = stringResource(R.string.verify_your_identity_to_access_call),
                    onSuccess = { onSuccess() },
                    onError = { _, _ -> onDismiss()}
                )
            }
            "pin" -> {
                Surface(
                    modifier = Modifier
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent,//MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Box(
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        PinDialogContent(
                            title = stringResource(R.string.enter_pin),
                            isVerify = true,
                            verifySecret = verifyPin,
                            onConfirm = { onSuccess() },
                            onDismiss = onDismiss
                        )
                    }
                }
            }
            "password" -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp)
                        .widthIn(max = 360.dp)
                        .wrapContentHeight()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    PasswordDialogContent(
                        title            = stringResource(R.string.enter_password),
                        isVerify         = true,
                        verifySecret = verifyPassword,
                        showCloseButton  = true,
                        onConfirm        = { onSuccess() },
                        onDismiss        = onDismiss
                    )
                }
            }
        }
    }
}
