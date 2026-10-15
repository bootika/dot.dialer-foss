package dev.goodwy.rphone.view.screen

import android.provider.CallLog
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.CallLogFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedHomeFilterTest {
    @Test
    fun `all filter keeps every call type`() {
        assertTrue(callMatchesFilter(call(CallLog.Calls.OUTGOING_TYPE), CallLogFilter.All))
        assertTrue(callMatchesFilter(call(CallLog.Calls.MISSED_TYPE), CallLogFilter.All))
    }

    @Test
    fun `missed filter only keeps missed calls`() {
        assertTrue(callMatchesFilter(call(CallLog.Calls.MISSED_TYPE), CallLogFilter.Missed))
        assertFalse(callMatchesFilter(call(CallLog.Calls.REJECTED_TYPE), CallLogFilter.Missed))
        assertFalse(callMatchesFilter(call(CallLog.Calls.INCOMING_TYPE), CallLogFilter.Missed))
    }

    @Test
    fun `direction filters remain mutually exclusive`() {
        assertTrue(callMatchesFilter(call(CallLog.Calls.INCOMING_TYPE), CallLogFilter.Incoming))
        assertTrue(callMatchesFilter(call(CallLog.Calls.OUTGOING_TYPE), CallLogFilter.Outgoing))
        assertTrue(callMatchesFilter(call(CallLog.Calls.REJECTED_TYPE), CallLogFilter.Rejected))
        assertFalse(callMatchesFilter(call(CallLog.Calls.INCOMING_TYPE), CallLogFilter.Outgoing))
    }

    private fun call(type: Int) = CallLogEntry(
        id = type.toLong(),
        number = "+40123456789",
        name = "Test Alexandru Butica",
        type = type,
        date = 1L,
        duration = 0L,
        photoUri = null,
        contactId = null,
    )
}
