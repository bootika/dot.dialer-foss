package dev.goodwy.rphone.controller.util

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.util.Locale

internal const val EXTRA_PREPARED_TEL_NUMBER = "dev.goodwy.rphone.PREPARED_TEL_NUMBER"

/** A local external tel link uses the visited network, not the home SIM, abroad. */
internal fun externalTelRegion(networkCountry: String?, simCountry: String?, fallback: String?): String? {
    fun country(value: String?): String? = value?.trim()?.uppercase(Locale.ROOT)
        ?.takeIf { it in Locale.getISOCountries() }
    val network = country(networkCountry) ?: return fallback
    return if (network != country(simCountry)) network else fallback
}

internal fun normalizeExternalTelNumber(
    number: String, enabled: Boolean, networkCountry: String?, simCountry: String?, fallback: String?,
    formatter: (String, String) -> String?,
): String {
    // Preserve extensions, post-dial sequences and URI parameters; they are not ordinary national numbers.
    if (number.any { it == ',' || it == ';' }) return number
    return runCatching {
        OutgoingNumberNormalizer.normalize(number, enabled,
            externalTelRegion(networkCountry, simCountry, fallback), formatter)
    }.getOrDefault(number)
}

internal fun normalizeExternalTelNumber(context: Context, number: String, prefs: PreferenceManager): String {
    val enabled = prefs.getBoolean(PreferenceManager.KEY_OUTGOING_NUMBER_NORMALIZATION,
        PreferenceManager.DEFAULT_OUTGOING_NUMBER_NORMALIZATION)
    if (!enabled) return number
    val compact = number.filterNot(Char::isWhitespace)
    if (compact.startsWith("+") || compact.startsWith("00") || compact.any { it == '*' || it == '#' } ||
        compact.count(Char::isDigit) < 7 || compact.any { it == ',' || it == ';' }) return number
    val manualRegion = prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, null)
        ?.trim()?.takeIf(String::isNotEmpty)
    if (manualRegion != null) return normalizeExternalTelNumber(number, true, null, null, manualRegion,
        PhoneNumberUtils::formatNumberToE164)
    val base = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()
    val telephony = runCatching {
        val id = SubscriptionManager.getDefaultVoiceSubscriptionId()
        if (SubscriptionManager.isValidSubscriptionId(id)) base?.createForSubscriptionId(id) else base
    }.getOrNull() ?: base
    val network = runCatching { telephony?.networkCountryIso }.getOrNull()
    val sim = runCatching { telephony?.simCountryIso }.getOrNull()
    val region = runCatching {
        resolveOutgoingNumberRegionIso(context, null,
            prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, null))
    }.getOrNull()
    return normalizeExternalTelNumber(number, enabled, network, sim, region, PhoneNumberUtils::formatNumberToE164)
}
