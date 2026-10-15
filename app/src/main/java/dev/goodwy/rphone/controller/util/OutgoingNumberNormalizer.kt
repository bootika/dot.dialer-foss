package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

private const val OUTGOING_NUMBER_LOG_TAG = "OutgoingNumber"

/**
 * Applies an international prefix only to ordinary national phone numbers.
 * Emergency numbers, short codes, MMI/USSD commands and already-international
 * numbers are deliberately left untouched.
 */
object OutgoingNumberNormalizer {
    fun normalize(
        rawNumber: String,
        enabled: Boolean,
        regionIso: String?,
        toE164: (String, String) -> String?,
    ): String {
        if (!enabled || regionIso.isNullOrBlank()) return rawNumber
        val compact = rawNumber.filterNot(Char::isWhitespace)
        if (compact.startsWith("+") || compact.startsWith("00")) return rawNumber
        if (compact.any { it == '*' || it == '#' }) return rawNumber
        if (compact.count(Char::isDigit) < MIN_NATIONAL_DIGITS) return rawNumber

        return toE164(rawNumber, regionIso.uppercase(Locale.ROOT))
            ?.takeIf { it.startsWith("+") }
            ?: rawNumber
    }

    private const val MIN_NATIONAL_DIGITS = 7
}

internal fun resolveOutgoingNumberRegionIso(
    context: Context,
    selectedAccount: PhoneAccountHandle?,
    manualRegionIso: String?,
): String? {
    manualRegionIso?.trim()?.takeIf { it.isNotEmpty() }?.let {
        return it.uppercase(Locale.ROOT)
    }

    val hasPhoneState = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED

    if (hasPhoneState) {
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
        val subscriptions = try {
            subscriptionManager?.activeSubscriptionInfoList.orEmpty()
                .sortedBy(SubscriptionInfo::getSimSlotIndex)
        } catch (error: SecurityException) {
            Log.w(OUTGOING_NUMBER_LOG_TAG, "Unable to read active subscriptions", error)
            emptyList()
        }
        val selectedSubscription = findSubscriptionForAccount(context, selectedAccount, subscriptions)
        selectedSubscription?.countryIso?.normalizedCountryIso()?.let { return it }

        val defaultVoiceId = SubscriptionManager.getDefaultVoiceSubscriptionId()
        subscriptions.firstOrNull { it.subscriptionId == defaultVoiceId }
            ?.countryIso
            ?.normalizedCountryIso()
            ?.let { return it }
    }

    val telephony = context.getSystemService(TelephonyManager::class.java)
    try {
        telephony?.simCountryIso?.normalizedCountryIso()?.let { return it }
    } catch (error: SecurityException) {
        Log.w(OUTGOING_NUMBER_LOG_TAG, "Unable to read the SIM country", error)
    }
    try {
        telephony?.networkCountryIso?.normalizedCountryIso()?.let { return it }
    } catch (error: SecurityException) {
        Log.w(OUTGOING_NUMBER_LOG_TAG, "Unable to read the network country", error)
    }
    return Locale.getDefault().country.normalizedCountryIso()
}

private fun findSubscriptionForAccount(
    context: Context,
    selectedAccount: PhoneAccountHandle?,
    subscriptions: List<SubscriptionInfo>,
): SubscriptionInfo? {
    if (selectedAccount == null || subscriptions.isEmpty()) return null

    selectedAccount.id.toIntOrNull()?.let { accountId ->
        subscriptions.firstOrNull { it.subscriptionId == accountId }?.let { return it }
    }

    val telecom = context.getSystemService(TelecomManager::class.java)
    val accounts = try {
        telecom?.callCapablePhoneAccounts.orEmpty()
    } catch (error: SecurityException) {
        Log.w(OUTGOING_NUMBER_LOG_TAG, "Unable to read call-capable accounts", error)
        emptyList()
    }
    val index = accounts.indexOfFirst { it == selectedAccount }
    return subscriptions.getOrNull(index)
}

private fun String?.normalizedCountryIso(): String? = this
    ?.trim()
    ?.takeIf { it.length == 2 }
    ?.uppercase(Locale.ROOT)

internal fun normalizeOutgoingNumber(
    context: Context,
    rawNumber: String,
    selectedAccount: PhoneAccountHandle?,
    prefs: PreferenceManager,
): String {
    if (rawNumber.startsWith("voicemail:")) return rawNumber
    val enabled = prefs.getBoolean(
        PreferenceManager.KEY_OUTGOING_NUMBER_NORMALIZATION,
        PreferenceManager.DEFAULT_OUTGOING_NUMBER_NORMALIZATION,
    )
    val manualRegion = prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, null)
    val region = resolveOutgoingNumberRegionIso(context, selectedAccount, manualRegion)
    return OutgoingNumberNormalizer.normalize(rawNumber, enabled, region) { number, iso ->
        PhoneNumberUtils.formatNumberToE164(number, iso)
    }
}
