package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

private const val SIM_UTILS_LOG_TAG = "SimUtils"

/** Active subscriptions, not physical slots, saved eSIM profiles or third-party calling accounts. */
fun activeSimCount(context: Context): Int {
    return try {
        val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (!hasPhoneState) return 0
        context.getSystemService(android.telephony.SubscriptionManager::class.java)
            ?.activeSubscriptionInfoList.orEmpty().map { it.subscriptionId }.distinct().size
    } catch (error: Exception) {
        Log.w(SIM_UTILS_LOG_TAG, "Unable to inspect active subscriptions", error)
        0
    }
}

fun hasDualSim(context: Context): Boolean = activeSimCount(context) >= 2

fun isAirplaneModeOn(context: Context): Boolean {
    return try {
        android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    } catch (e: Exception) {
        Log.w(SIM_UTILS_LOG_TAG, "Unable to read airplane mode", e)
        false
    }
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun isWifiConnected(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    } catch (e: Exception) {
        Log.w(SIM_UTILS_LOG_TAG, "Unable to inspect Wi-Fi connectivity", e)
        false
    }
}

fun isVoicemailNumber(context: Context, number: String?): Boolean {
    if (number.isNullOrBlank()) return false
    val clean = number.trim()
    if (clean.equals("voicemail", ignoreCase = true) || clean.startsWith("voicemail:", ignoreCase = true)) {
        return true
    }
    val prefs = PreferenceManager(context)
    val configuredVm = prefs.getString(PreferenceManager.KEY_VOICEMAIL_NUMBER, null)
    if (!configuredVm.isNullOrBlank() && areNumbersEqual(clean, configuredVm)) {
        return true
    }
    val sysVm = getSystemVoicemailNumber(context)
    if (!sysVm.isNullOrBlank() && areNumbersEqual(clean, sysVm)) {
        return true
    }
    return try {
        @Suppress("DEPRECATION")
        PhoneNumberUtils.isVoiceMailNumber(clean)
    } catch (e: Exception) {
        Log.w(SIM_UTILS_LOG_TAG, "Unable to compare against the system voicemail number", e)
        false
    }
}

fun getSystemVoicemailNumber(context: Context): String? {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        ?: return null
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
        try {
            val accounts = telecomManager.callCapablePhoneAccounts
            val defaultHandle =
                telecomManager.getDefaultOutgoingPhoneAccount(Uri.fromParts("tel", "123", null).scheme)

            val handle = defaultHandle ?: accounts.firstOrNull()
            if (handle != null) {
                val num = telecomManager.getVoiceMailNumber(handle)
                if (!num.isNullOrEmpty()) return num
            }
        } catch (error: SecurityException) {
            Log.w(SIM_UTILS_LOG_TAG, "Phone-account access was denied while reading voicemail", error)
        } catch (error: Exception) {
            Log.w(SIM_UTILS_LOG_TAG, "Unable to read voicemail from the phone account", error)
        }

        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val num = tm.voiceMailNumber
            if (!num.isNullOrEmpty()) return num
        } catch (error: SecurityException) {
            Log.w(SIM_UTILS_LOG_TAG, "Telephony access was denied while reading voicemail", error)
        } catch (error: Exception) {
            Log.w(SIM_UTILS_LOG_TAG, "Unable to read voicemail from telephony", error)
        }
    }
    return null
}
