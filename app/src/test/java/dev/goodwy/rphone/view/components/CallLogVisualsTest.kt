package dev.goodwy.rphone.view.components

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogVisualsTest {
    @Test
    fun `missed calls highlight the whole row`() {
        val visual = resolveCallLogVisual(
            type = CallLog.Calls.MISSED_TYPE,
            durationSeconds = 0,
            isBlocked = false,
        )

        assertEquals(CallLogVisualStatus.MISSED, visual.status)
        assertTrue(visual.accentMetadata)
        assertTrue(visual.emphasizeTime)
    }

    @Test
    fun `rejected calls keep metadata neutral`() {
        val visual = resolveCallLogVisual(
            type = CallLog.Calls.REJECTED_TYPE,
            durationSeconds = 0,
            isBlocked = false,
        )

        assertEquals(CallLogVisualStatus.REJECTED, visual.status)
        assertFalse(visual.accentMetadata)
        assertFalse(visual.emphasizeTime)
    }

    @Test
    fun `zero duration outgoing calls are not answered`() {
        val visual = resolveCallLogVisual(
            type = CallLog.Calls.OUTGOING_TYPE,
            durationSeconds = 0,
            isBlocked = false,
        )

        assertEquals(CallLogVisualStatus.NOT_ANSWERED, visual.status)
        assertFalse(visual.accentMetadata)
        assertFalse(visual.emphasizeTime)
    }

    @Test
    fun `answered outgoing calls keep the outgoing state`() {
        val visual = resolveCallLogVisual(
            type = CallLog.Calls.OUTGOING_TYPE,
            durationSeconds = 12,
            isBlocked = false,
        )

        assertEquals(CallLogVisualStatus.OUTGOING, visual.status)
    }

    @Test
    fun `blocked flag wins over provider type`() {
        val visual = resolveCallLogVisual(
            type = CallLog.Calls.INCOMING_TYPE,
            durationSeconds = 0,
            isBlocked = true,
        )

        assertEquals(CallLogVisualStatus.BLOCKED, visual.status)
        assertFalse(visual.accentMetadata)
    }
}
