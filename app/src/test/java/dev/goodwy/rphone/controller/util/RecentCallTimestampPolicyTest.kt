package dev.goodwy.rphone.controller.util

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentCallTimestampPolicyTest {
    private val zone = ZoneId.of("Europe/Bucharest")
    private val now = epochMillis(2026, 9, 23, 13, 10)

    @Test
    fun `today uses time`() {
        assertEquals(
            RecentCallTimestampKind.TIME,
            recentCallTimestampKind(epochMillis(2026, 9, 23, 0, 1), now, zone),
        )
    }

    @Test
    fun `yesterday uses localized relative label`() {
        assertEquals(
            RecentCallTimestampKind.YESTERDAY,
            recentCallTimestampKind(epochMillis(2026, 9, 22, 23, 59), now, zone),
        )
    }

    @Test
    fun `previous six days use weekday`() {
        assertEquals(
            RecentCallTimestampKind.WEEKDAY,
            recentCallTimestampKind(epochMillis(2026, 9, 17, 12, 0), now, zone),
        )
    }

    @Test
    fun `calls older than six days use short date`() {
        assertEquals(
            RecentCallTimestampKind.DATE,
            recentCallTimestampKind(epochMillis(2026, 9, 16, 23, 59), now, zone),
        )
    }

    @Test
    fun `calendar comparison survives daylight saving changes`() {
        val dstNow = epochMillis(2026, 10, 26, 0, 30)
        assertEquals(
            RecentCallTimestampKind.YESTERDAY,
            recentCallTimestampKind(epochMillis(2026, 10, 25, 0, 45), dstNow, zone),
        )
    }

    private fun epochMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
}
