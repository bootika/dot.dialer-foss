package io.github.bootika.dotdialer.core.call

data class CallHistoryWindow<T>(
    val entries: List<T>,
    val hasOlderEntries: Boolean,
)

/** Controls progressive disclosure on a contact's full call-history screen. */
object CallHistoryWindowPolicy {
    const val INITIAL_DAYS = 15
    const val PAGE_DAYS = 30

    fun expand(currentDays: Int): Int =
        if (currentDays > Int.MAX_VALUE - PAGE_DAYS) Int.MAX_VALUE else currentDays + PAGE_DAYS

    fun <T> apply(
        entries: List<T>,
        visibleDays: Int,
        nowMillis: Long,
        dateMillis: (T) -> Long,
    ): CallHistoryWindow<T> {
        val safeDays = visibleDays.coerceAtLeast(INITIAL_DAYS)
        val spanMillis = safeDays.toLong()
            .coerceAtMost(Long.MAX_VALUE / MILLIS_PER_DAY) * MILLIS_PER_DAY
        val cutoffMillis = (nowMillis - spanMillis).coerceAtLeast(0L)
        return CallHistoryWindow(
            entries = entries.filter { dateMillis(it) >= cutoffMillis },
            hasOlderEntries = entries.any { dateMillis(it) < cutoffMillis },
        )
    }

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
}
