package dev.goodwy.rphone.controller.lock

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.goodwy.rphone.controller.util.PreferenceManager

object AppLockManager {
    private const val TAG = "AppLockManager"

    var isUnlocked: Boolean = false
        private set

    private var lastBackgroundTime: Long = 0L

    fun isLocked(prefs: PreferenceManager): Boolean {
        if (!prefs.isAppLockEnabled()) return false
        if (!isUnlocked) return true

        if (lastBackgroundTime > 0) {
            val timeoutMinutes = prefs.getAppLockTimeout()
            val elapsedMillis = android.os.SystemClock.elapsedRealtime() - lastBackgroundTime
            val timeoutMillis = timeoutMinutes * 60 * 1000L
            if (elapsedMillis >= timeoutMillis) {
                isUnlocked = false
                return true
            }
        }
        return false
    }

    fun onAppBackgrounded() {
        lastBackgroundTime = android.os.SystemClock.elapsedRealtime()
    }

    fun onAppForegrounded(prefs: PreferenceManager) {
        if (isLocked(prefs)) {
            isUnlocked = false
        }
        lastBackgroundTime = 0L
    }

    fun unlock() {
        isUnlocked = true
        lastBackgroundTime = 0L
    }

    fun lock() {
        isUnlocked = false
        lastBackgroundTime = 0L
    }

    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = getAuthenticators()
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    internal fun getAuthenticators(): Int {
        // STRONG | DEVICE_CREDENTIAL is unsupported on API 29; WEAK includes STRONG.
        return BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onSuccess: () -> Unit,
        onError: (Int, String) -> Unit = {_, _ -> }
    ): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d(TAG, "Authentication error ($errorCode): $errString")
                onError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d(TAG, "Authentication failed")
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(getAuthenticators())
            .build()

        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "BiometricPrompt launch failed: ${e.javaClass.simpleName}")
            onError(BiometricPrompt.ERROR_HW_UNAVAILABLE, "Authentication unavailable")
        }
        return prompt
    }
}
