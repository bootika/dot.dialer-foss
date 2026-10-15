package dev.goodwy.rphone.view.screen

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.telecom.Call
import android.util.Log
import dev.goodwy.rphone.MainActivity
import dev.goodwy.rphone.controller.ACTION_VIEW_CONTACT
import dev.goodwy.rphone.controller.util.currentTelecomState

private const val IN_CALL_ACTION_LOG_TAG = "InCallActionLauncher"

internal fun openInAppContactDetails(
    context: Context,
    contactId: String?,
    phoneNumber: String,
) {
    if (!contactId.isNullOrBlank() && !contactId.startsWith("p")) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_VIEW_CONTACT
                putExtra("contact_id", contactId)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        return
    }

    context.startActivity(
        Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
        },
    )
}

internal fun launchAdditionalCall(
    context: Context,
    call: Call,
    callState: Int,
    hasOtherCall: Boolean,
) {
    if (hasOtherCall || callState == Call.STATE_DIALING) return
    if (callState != Call.STATE_HOLDING) {
        try {
            call.hold()
        } catch (error: Exception) {
            Log.w(IN_CALL_ACTION_LOG_TAG, "Unable to hold call before opening the dialer", error)
        }
    }
    context.startActivity(
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_DIAL
            putExtra(MainActivity.EXTRA_RETURN_TO_CALL_ON_BACK, true)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        },
    )
}

internal fun performAddCallOrResume(
    context: Context,
    call: Call,
    callState: Int,
    hasOtherCall: Boolean,
) {
    if (callState == Call.STATE_HOLDING && !hasOtherCall) {
        try {
            call.unhold()
        } catch (error: Exception) {
            Log.w(IN_CALL_ACTION_LOG_TAG, "Unable to resume held call", error)
        }
        return
    }

    launchAdditionalCall(context, call, callState, hasOtherCall)
}

/**
 * Telecom is the source of truth for whether two calls can be joined. Some carriers and
 * PhoneAccounts never expose this list, so the UI must not advertise a merge action merely
 * because two calls happen to exist.
 */
internal fun canMergeTelecomCalls(first: Call, second: Call): Boolean =
    runCatching { second in first.conferenceableCalls }.getOrDefault(false) ||
        runCatching { first in second.conferenceableCalls }.getOrDefault(false)

internal fun mergeTelecomCalls(first: Call, second: Call): Boolean {
    return runCatching {
        when {
            second in first.conferenceableCalls -> first.conference(second)
            first in second.conferenceableCalls -> second.conference(first)
            else -> return false
        }
        true
    }.onFailure { error ->
        Log.w(IN_CALL_ACTION_LOG_TAG, "Unable to merge Telecom calls", error)
    }.getOrDefault(false)
}

internal fun swapTelecomCalls(current: Call, other: Call) {
    try {
        if (current.currentTelecomState() == Call.STATE_ACTIVE) {
            current.hold()
        }
        if (other.currentTelecomState() == Call.STATE_HOLDING) {
            other.unhold()
        }
    } catch (error: Exception) {
        Log.w(IN_CALL_ACTION_LOG_TAG, "Unable to swap Telecom calls", error)
        if (other.currentTelecomState() == Call.STATE_HOLDING) {
            runCatching { other.unhold() }
                .onFailure { Log.w(IN_CALL_ACTION_LOG_TAG, "Unable to resume fallback Telecom call", it) }
        }
    }
}
