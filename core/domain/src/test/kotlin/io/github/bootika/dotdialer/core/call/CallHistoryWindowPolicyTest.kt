package io.github.bootika.dotdialer.core.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallHistoryWindowPolicyTest {
    @Test
    fun `initial window shows fifteen days and reports older entries`() {
        val now = 100L * DAY
        val result = CallHistoryWindowPolicy.apply(
            entries = listOf(now - DAY, now - 15L * DAY, now - 16L * DAY),
            visibleDays = CallHistoryWindowPolicy.INITIAL_DAYS,
            nowMillis = now,
            dateMillis = { it },
        )

        assertEquals(listOf(now - DAY, now - 15L * DAY), result.entries)
        assertTrue(result.hasOlderEntries)
    }

    @Test
    fun `each expansion adds thirty days`() {
        assertEquals(45, CallHistoryWindowPolicy.expand(15))
        assertEquals(75, CallHistoryWindowPolicy.expand(45))
    }

    @Test
    fun `older flag clears when all entries fit`() {
        val now = 100L * DAY
        val result = CallHistoryWindowPolicy.apply(
            entries = listOf(now - 20L * DAY),
            visibleDays = 45,
            nowMillis = now,
            dateMillis = { it },
        )

        assertFalse(result.hasOlderEntries)
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1_000L
    }
}
