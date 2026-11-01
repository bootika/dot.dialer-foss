package dev.goodwy.rphone.controller.util

import android.content.Context
import android.telecom.PhoneAccountHandle

/** A stale explicit choice must ask again instead of silently changing the user's SIM. */
internal fun preferredOutgoingAccount(
    context: Context,
    prefs: PreferenceManager,
    number: String,
    contactId: String?,
    accounts: List<CallingAccountInfo>,
): PhoneAccountHandle? {
    val numberKey = prefs.getFavoriteSimForNumber(number)
    if (numberKey == NumberSimIdentities.CONFLICT) return null
    val contactNumber = contactId?.let(prefs::getFavoriteNumber)
    val contactKey = contactId?.let(prefs::getFavoriteSim)
        ?.takeIf { contactNumber == null || areNumbersEqual(number, contactNumber) }
    val explicitKey = numberKey ?: contactKey
        ?: prefs.getString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, null)
    if (explicitKey != null) return resolveCallingAccount(context, explicitKey, accounts)
    return accounts.getOrNull(prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, 0) - 1)?.handle
}

internal fun rememberOutgoingAccount(prefs: PreferenceManager, number: String?, key: String, scope: Int) {
    when (scope) {
        1 -> number?.takeIf(String::isNotBlank)?.let { prefs.setFavoriteSimForNumber(it, key) }
        2 -> {
            prefs.setString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, key)
            prefs.setInt(PreferenceManager.KEY_DEFAULT_SIM, 0)
        }
    }
}
