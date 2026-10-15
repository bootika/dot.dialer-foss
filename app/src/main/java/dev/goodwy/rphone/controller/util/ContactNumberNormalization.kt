package dev.goodwy.rphone.controller.util

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

internal fun contactNumberRegion(context: Context, prefs: PreferenceManager? = null): String? {
    val configured = prefs?.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, null)
    if (!configured.isNullOrBlank() && configured.length == 2) return configured.uppercase(Locale.ROOT)
    // Network country/locale are not evidence of a stored contact's country.
    return runCatching { context.getSystemService(TelephonyManager::class.java)?.simCountryIso }
        .getOrNull()?.takeIf { it.length == 2 }?.uppercase(Locale.ROOT)
}
internal fun normalizedContactNumber(number: String, region: String?): String? {
    val value = number.trim()
    if (value.isBlank() || value.any { !it.isDigit() && it !in "+ ()-." } ||
        value.count(Char::isDigit) !in 7..15) return null
    val iso = region ?: if (value.startsWith("+")) "ZZ" else return null
    return PhoneNumberUtils.formatNumberToE164(value, iso)?.takeIf { it.matches(Regex("\\+[1-9]\\d{7,14}")) }
}
internal fun safeE164Replacement(context: Context, storedNumber: String, matchedNumber: String, regionOverride: String? = null): String? {
    val region = regionOverride ?: contactNumberRegion(context)
    val stored = normalizedContactNumber(storedNumber, region) ?: return null
    val matched = normalizedContactNumber(matchedNumber, region) ?: return null
    return stored.takeIf { it == matched }
}
