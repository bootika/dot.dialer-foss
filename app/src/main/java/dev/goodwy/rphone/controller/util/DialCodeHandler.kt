package dev.goodwy.rphone.controller.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.net.toUri
import io.github.bootika.dotdialer.core.call.DialCodeAction
import io.github.bootika.dotdialer.core.call.DialCodeClassifier

internal object DialCodeHandler {
    private const val LOG_TAG = "DialCodeHandler"

    fun handle(
        context: Context,
        input: String,
        placeCarrierCode: (String) -> Unit,
    ): Boolean {
        val decoded = try {
            Uri.decode(input.trim())
        } catch (error: RuntimeException) {
            Log.w(LOG_TAG, "Unable to decode a dial code; using its original form", error)
            input.trim()
        }

        return when (val action = DialCodeClassifier.classify(decoded)) {
            DialCodeAction.None -> false
            DialCodeAction.OpenDeviceInfo -> {
                openSettings(context, Settings.ACTION_DEVICE_INFO_SETTINGS)
                true
            }
            DialCodeAction.OpenSarInfo -> {
                openSarSettings(context)
                true
            }
            is DialCodeAction.DispatchSecretCode -> {
                dispatchSecretCode(context, action.digits)
                true
            }
            is DialCodeAction.PlaceCarrierCode -> {
                placeCarrierCode(action.code)
                true
            }
        }
    }

    private fun openSarSettings(context: Context) {
        val actions = listOf(
            "android.settings.SAR_INFORMATION",
            "android.settings.RF_EXPOSURE_SETTINGS",
            Settings.ACTION_DEVICE_INFO_SETTINGS,
        )
        if (actions.none { openSettings(context, it, logFailure = false) }) {
            Log.w(LOG_TAG, "No system SAR or device-information screen is available")
        }
    }

    private fun openSettings(
        context: Context,
        action: String,
        logFailure: Boolean = true,
    ): Boolean = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (error: RuntimeException) {
        if (logFailure) Log.w(LOG_TAG, "Unable to open a system information screen", error)
        false
    }

    private fun dispatchSecretCode(context: Context, digits: String) {
        try {
            context.getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(digits)
        } catch (error: RuntimeException) {
            Log.w(LOG_TAG, "Telephony rejected a dialer special code", error)
        }

        val uri = "android_secret_code://$digits".toUri()
        sendSecretCodeBroadcast(context, "android.provider.Telephony.SECRET_CODE", uri)
        sendSecretCodeBroadcast(context, "android.telephony.action.SECRET_CODE", uri)
    }

    private fun sendSecretCodeBroadcast(context: Context, action: String, uri: Uri) {
        try {
            context.sendBroadcast(
                Intent(action, uri).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            )
        } catch (error: RuntimeException) {
            Log.w(LOG_TAG, "Unable to dispatch a secret-code broadcast", error)
        }
    }
}
