package io.github.bootika.dotdialer.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentHistoryPolicyTest {
    @Test
    fun `defaults to one year and five hundred entries`() {
        assertEquals(365, RecentHistoryPolicy.sanitizePeriodDays(-1))
        assertEquals(1000, RecentHistoryPolicy.sanitizeMaxEntries(42))
    }

    @Test
    fun `all time has no date cutoff`() {
        assertNull(RecentHistoryPolicy.cutoffMillis(0, nowMillis = 10_000L))
    }

    @Test
    fun `applies the time window and maximum together`() {
        val day = 24L * 60L * 60L * 1_000L
        val now = 400L * day
        val entries = (0 until 700).map { index -> now - index * day }

        val result = RecentHistoryPolicy.apply(
            entries = entries,
            periodDays = 180,
            maxEntries = 100,
            nowMillis = now,
            dateMillis = { it },
        )

        assertEquals(100, result.size)
        assertEquals(now, result.first())
        assertEquals(now - 99L * day, result.last())
    }

    @Test
    fun `all time still respects the safety cap`() {
        val entries = (0 until 1_500).toList()
        val result = RecentHistoryPolicy.apply(entries, 0, 1_000, 0L) { 0L }
        assertEquals(1_000, result.size)
    }

    @Test
    fun `five hundred limit counts unified identities instead of raw entries`() {
        data class Entry(val identity: Int, val sequence: Int)

        val entries = buildList {
            repeat(500) { identity ->
                repeat(2) { add(Entry(identity = identity, sequence = size)) }
            }
            add(Entry(identity = 500, sequence = size))
        }
        val result = RecentHistoryPolicy.applyDistinct(
            entries = entries,
            periodDays = 0,
            maxEntries = 500,
            nowMillis = 0L,
            dateMillis = { 0L },
            identity = Entry::identity,
        )

        assertEquals(1_000, result.size)
        assertEquals(500, result.map(Entry::identity).distinct().size)
        assertEquals(499, result.last().identity)
    }
}
