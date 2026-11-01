package dev.goodwy.rphone.controller

import android.telecom.Call
import android.telecom.TelecomManager

/** Correlate only the original app intent; never infer cancellation from a phone-number match. */
internal fun directCallRequestId(call: Call): String? {
    val extras = call.details?.intentExtras ?: return null
    return extras.getString(DirectCallWindow.REQUEST_EXTRA)
        ?: extras.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)?.getString(DirectCallWindow.REQUEST_EXTRA)
}

internal fun bindDirectTelecomCall(call: Call) {
    DirectCallWindow.bindTelecomCall(
        directCallRequestId(call), call.details?.callDirection == Call.Details.DIRECTION_OUTGOING,
    ) {
        try { call.disconnect() }
        catch (failure: RuntimeException) {
            android.util.Log.w("DirectCallHandoff", "Disconnect unavailable: ${failure.javaClass.simpleName}")
        }
    }
}

internal fun registerDirectTelecomCall(registered: MutableSet<Call>, call: Call, callback: Call.Callback) {
    if (registered.add(call)) call.registerCallback(callback)
    bindDirectTelecomCall(call)
}
