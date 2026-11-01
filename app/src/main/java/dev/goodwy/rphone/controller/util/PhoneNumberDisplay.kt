package dev.goodwy.rphone.controller.util

import com.google.i18n.phonenumbers.PhoneNumberUtil

/** Presentation only: never feed this output into storage, matching or outgoing call intents. */
internal object PhoneNumberDisplay {
    const val GROUP_SPACE_SCALE = 1f
    private val phone by lazy { PhoneNumberUtil.getInstance() }
    private val cache = object : LinkedHashMap<Pair<String, String?>, String>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String?>, String>) = size > 512
    }
    private val bidi = setOf('\u200e', '\u200f', '\u202a', '\u202c', '\u2066', '\u2069')
    fun candidate(value: String): Boolean {
        val n = value.filterNot { it in bidi }.trim()
        return n.count { it in '0'..'9' } in 7..15 && n.all { it in "0123456789+ ()-." }
    }
    @Synchronized
    fun format(value: String, region: String?): String {
        if (!candidate(value)) return value
        val key = value to region
        return cache.getOrPut(key) { formatUncached(value, region) }
    }
    private fun formatUncached(value: String, region: String?): String {
        val clean = value.filterNot { it in bidi }.trim()
        val compact = clean.filter { it in '0'..'9' || it == '+' }
        if (!compact.startsWith('+') && region.isNullOrBlank()) return value
        return runCatching {
            val parsed = phone.parse(clean, region ?: "ZZ")
            if (!phone.isValidNumber(parsed)) return value
            val mode = if (compact.startsWith('+')) PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
                else PhoneNumberUtil.PhoneNumberFormat.NATIONAL
            val formatted = phone.format(parsed, mode).replace(Regex("[()\\-]"), " ")
                .replace(Regex(" +"), " ").trim()
            // No loss/addition of a trunk digit, international prefix or any diallable character.
            if (formatted.filter { it in '0'..'9' || it == '+' } != compact) value else value.takeWhile { it in bidi } + formatted + value.takeLastWhile { it in bidi }
        }.getOrDefault(value)
    }
    fun boundaries(raw: String, region: String?): Set<Int> {
        if (raw.any { it !in "0123456789+" }) return emptySet()
        var offset = 0
        return buildSet {
            for (ch in format(raw, region)) {
                if (ch == ' ') add(offset) else offset++
            }
        }
    }
}
