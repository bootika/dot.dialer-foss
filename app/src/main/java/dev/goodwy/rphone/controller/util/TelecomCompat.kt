package dev.goodwy.rphone.controller.util

import android.os.Build
import android.telecom.Call
import android.util.Log

/**
 * Returns the current Telecom state without leaking API-level branching throughout the app.
 *
 * [Call.Details.getState] replaced [Call.getState] in Android 12 (API 31). Keeping the
 * compatibility decision here makes every call-selection and notification path use the same
 * source of truth while retaining support for the app's API 29 and 30 devices.
 */
fun Call.currentTelecomState(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    details.state
} else {
    @Suppress("DEPRECATION")
    state
}

fun Call.unregisterCallbackSafely(callback: Call.Callback) {
    try {
        unregisterCallback(callback)
    } catch (error: RuntimeException) {
        Log.w("TelecomCompat", "Unable to unregister a Telecom call callback", error)
    }
}
