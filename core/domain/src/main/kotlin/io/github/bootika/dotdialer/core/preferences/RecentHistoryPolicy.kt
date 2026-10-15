package io.github.bootika.dotdialer.core.preferences

/**
 * Bounds the call history used by Dot Dialer without deleting anything from Android's call log.
 * The time window controls relevance, while the entry cap is a defensive performance ceiling.
 */
object RecentHistoryPolicy {
    const val ALL_TIME_DAYS = 0
    const val DEFAULT_PERIOD_DAYS = 365
    const val DEFAULT_MAX_ENTRIES = 1_000

    val allowedPeriodDays = setOf(30, 90, 180, 365, ALL_TIME_DAYS)
    val allowedMaxEntries = setOf(100, 250, 500, 1_000)

    fun sanitizePeriodDays(value: Int): Int =
        value.takeIf(allowedPeriodDays::contains) ?: DEFAULT_PERIOD_DAYS

    fun sanitizeMaxEntries(value: Int): Int =
        value.takeIf(allowedMaxEntries::contains) ?: DEFAULT_MAX_ENTRIES

    fun cutoffMillis(periodDays: Int, nowMillis: Long): Long? {
        val sanitized = sanitizePeriodDays(periodDays)
        if (sanitized == ALL_TIME_DAYS) return null
        return nowMillis - sanitized * MILLIS_PER_DAY
    }

    fun <T> apply(
        entries: List<T>,
        periodDays: Int,
        maxEntries: Int,
        nowMillis: Long,
        dateMillis: (T) -> Long,
    ): List<T> {
        val cutoff = cutoffMillis(periodDays, nowMillis)
        return entries.asSequence()
            .filter { cutoff == null || dateMillis(it) >= cutoff }
            .take(sanitizeMaxEntries(maxEntries))
            .toList()
    }

    /**
     * Applies the configured ceiling to distinct displayed identities rather than raw records.
     * Entries are expected newest-first. Repeated entries for an identity already admitted stay
     * in the result so callers can still merge their types and call-log IDs.
     */
    fun <T, K> applyDistinct(
        entries: List<T>,
        periodDays: Int,
        maxEntries: Int,
        nowMillis: Long,
        dateMillis: (T) -> Long,
        identity: (T) -> K,
    ): List<T> {
        val cutoff = cutoffMillis(periodDays, nowMillis)
        val maximum = sanitizeMaxEntries(maxEntries)
        val seen = mutableSetOf<K>()
        return buildList {
            entries.forEach { entry ->
                if (cutoff != null && dateMillis(entry) < cutoff) return@forEach
                val key = identity(entry)
                if (key !in seen && seen.size >= maximum) return@buildList
                seen += key
                add(entry)
            }
        }
    }

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
}
