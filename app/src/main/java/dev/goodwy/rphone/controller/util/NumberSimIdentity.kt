package dev.goodwy.rphone.controller.util

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.security.MessageDigest
import java.util.Locale

internal data class NumberSimIdentity(val key: String, val legacyKeys: List<String>, val unsafeLegacyKeys: List<String>)

/** Full identity only. Legacy twelve-digit keys may be truncated and cannot prove ownership. */
internal object NumberSimIdentities {
    const val CONFLICT = "number-sim-choice-conflict"
    const val USE_DEFAULT = "number-sim-use-default"
    private const val PREFIX = "favorite_sim_number_"
    private val phone by lazy { PhoneNumberUtil.getInstance() }
    private val plain = Regex("[+0-9 ()-.]+")
    private val cache = object : LinkedHashMap<Pair<String, String?>, NumberSimIdentity?>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String?>, NumberSimIdentity?>) = size > 512
    }
    @Synchronized
    fun resolve(value: String, region: String?): NumberSimIdentity? {
        val normalizedRegion = region?.uppercase(Locale.ROOT)
        val pair = value to normalizedRegion
        return cache.getOrPut(pair) { create(value, normalizedRegion) }
    }
    private fun create(value: String, region: String?): NumberSimIdentity? {
        val raw = value.trim().filterNot(Char::isWhitespace)
        if (raw.isEmpty()) return null
        val simple = plain.matches(value.trim())
        val legacy = linkedSetOf<String>()
        val unsafe = linkedSetOf<String>()
        fun oldKey(number: String) {
            val digits = number.filter { it in '0'..'9' }
            if (digits.length in 7..11) legacy += PREFIX + digits
            else if (digits.length >= 12) unsafe += PREFIX + digits.takeLast(12)
        }
        val e164 = if (simple && (raw.startsWith('+') || !region.isNullOrBlank())) runCatching {
            val parsed = phone.parse(value.trim(), region ?: "ZZ")
            if (!phone.isValidNumber(parsed)) return@runCatching null
            val international = phone.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            oldKey(international)
            // A foreign national alias could belong to a different country on this installation.
            if (region != null && phone.getCountryCodeForRegion(region) == parsed.countryCode) {
                oldKey(phone.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL))
            }
            international
        }.getOrNull() else null
        if (e164 != null) return NumberSimIdentity(PREFIX + "v2_e164_" + e164, legacy.toList(), unsafe.toList())
        // Without a reliable region, retain exact old ordinary-number choices, never code aliases.
        if (simple) oldKey(raw)
        val exact = if (simple) raw.filterNot { it in "()-." } else raw
        val digest = MessageDigest.getInstance("SHA-256").digest(exact.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return NumberSimIdentity(PREFIX + "v2_raw_" + digest, legacy.toList(), unsafe.toList())
    }
}
