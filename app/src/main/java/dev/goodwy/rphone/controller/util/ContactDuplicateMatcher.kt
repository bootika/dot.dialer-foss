package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.modal.data.Contact
import java.util.Locale

/** Merge is destructive enough to require full identities, never loose suffix/name comparisons. */
internal fun mergeNumberKey(number: String, region: String?, format: (String, String) -> String?): String? {
    val input = number.trim()
    if (input.isEmpty() || input.any { !it.isDigit() && it !in "+()- .\u00a0" }) return null
    val compact = input.filter { it.isDigit() || it == '+' }
    if (compact.count { it == '+' } > 1 || ('+' in compact && !compact.startsWith('+'))) return null
    if (!compact.startsWith('+') && region == null) return null
    return format(compact, region ?: "ZZ")?.takeIf { it.matches(Regex("\\+[1-9][0-9]{5,14}")) }
}

internal fun canonicalMergeNumber(number: String, region: String?): String? =
    mergeNumberKey(number, region, PhoneNumberUtils::formatNumberToE164)

internal fun resolveMergeRegion(context: Context): String? {
    val prefs = PreferenceManager(context)
    val manual = prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, null)
        ?.uppercase(Locale.ROOT)?.takeIf { it in Locale.getISOCountries() }
    if (manual != null) return manual
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
        val countries = try {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
                .map { it.countryIso.uppercase(Locale.ROOT) }.filter { it in Locale.getISOCountries() }.distinct()
        } catch (error: SecurityException) {
            android.util.Log.w("ContactMaintenance", "SIM region unavailable: ${error.javaClass.simpleName}")
            emptyList()
        }
        // Two national numbering plans are ambiguous, even with a default voice SIM.
        if (countries.size > 1) return null
        countries.singleOrNull()?.let { return it }
    }
    return resolveOutgoingNumberRegionIso(context, null, null)?.takeIf { it in Locale.getISOCountries() }
}

internal fun duplicatePhoneGroups(
    contacts: List<Contact>, region: String?,
    canonical: (String, String?) -> String? = ::canonicalMergeNumber,
): List<List<Contact>> {
    val public = contacts.filterNot { it.isPrivate }.distinctBy(Contact::id)
    val byNumber = mutableMapOf<String, MutableSet<String>>()
    public.forEach { contact -> contact.phoneNumbers.forEach { number ->
        canonical(number, region)?.let { byNumber.getOrPut(it) { linkedSetOf() }.add(contact.id) }
    } }
    val parent = public.associate { it.id to it.id }.toMutableMap()
    fun root(id: String): String { var key = id; while (parent[key] != key) key = parent.getValue(key); return key }
    byNumber.values.filter { it.size > 1 }.forEach { ids ->
        val target = root(ids.first()); ids.drop(1).forEach { parent[root(it)] = target }
    }
    return public.groupBy { root(it.id) }.values.filter { it.size > 1 }
        .map { it.sortedBy(Contact::id) }.sortedBy { it.first().displayName.lowercase(Locale.ROOT) }
}
