package dev.goodwy.rphone.view.screen

import android.telecom.Call
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import dev.goodwy.rphone.controller.util.elapsedTelecomCallSeconds
import dev.goodwy.rphone.view.components.DotDialerMergeIcon
import org.junit.Assert.*
import org.junit.Test

class CallElapsedTimeTest {
    @Test fun `secondary card has the approved padding and accessible actions`() {
        assertEquals(6, SecondaryCallCardStyle.OuterVerticalPaddingDp)
        assertEquals(12, SecondaryCallCardStyle.InnerVerticalPaddingDp)
        assertEquals(48, SecondaryCallCardStyle.ActionMinHeightDp)
        assertEquals(12, SecondaryCallCardStyle.ActionGapDp)
        assertEquals(28f, dev.goodwy.rphone.view.components.CallControlIcons.Swap.defaultWidth.value, 0f)
        assertEquals(32f, callActionIconSize(dev.goodwy.rphone.view.components.DotDialerHangupIcon).value, 0f)
        for (dark in listOf(false, true)) {
            val berry = dev.goodwy.rphone.view.theme.color_call_end
            val shield = SecondaryCallCardStyle.actionShield(berry, dark)
            assertEquals(berry.red, shield.red, 0f)
            assertEquals(berry.green, shield.green, 0f)
            assertEquals(berry.blue, shield.blue, 0f)
            assertEquals(if (dark) .14f else .08f, shield.alpha, 1f / 255f)
        }
    }
    @Test fun `merge has two left branches and one right facing arrow in either locale`() {
        assertFalse(DotDialerMergeIcon.autoMirror)
        val nodes = (DotDialerMergeIcon.root[0] as VectorPath).pathData
        assertEquals(listOf(PathNode.MoveTo(4f, 5f), PathNode.LineTo(11f, 12f), PathNode.LineTo(20f, 12f),
            PathNode.MoveTo(4f, 19f), PathNode.LineTo(11f, 12f), PathNode.MoveTo(16f, 8f),
            PathNode.LineTo(20f, 12f), PathNode.LineTo(16f, 16f)), nodes)
    }
    @Test fun `active and held calls keep their own connected elapsed time`() {
        for (state in listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING)) {
            assertEquals(94L, elapsedTelecomCallSeconds(state, 10_000, 104_000))
            assertEquals(114L, elapsedTelecomCallSeconds(state, 10_000, 124_000))
            assertEquals(3_605L, elapsedTelecomCallSeconds(state, 10_000, 3_615_000))
        }
    }
    @Test fun `unconnected or ended calls never acquire a conversation duration`() {
        for (state in listOf(Call.STATE_RINGING, Call.STATE_DIALING, Call.STATE_CONNECTING,
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED)) {
            assertNull(elapsedTelecomCallSeconds(state, 10_000, 104_000))
        }
        assertNull(elapsedTelecomCallSeconds(Call.STATE_ACTIVE, 0, 104_000))
        assertNull(elapsedTelecomCallSeconds(Call.STATE_HOLDING, -1, 104_000))
    }
    @Test fun `future timestamp is clamped and different calls never share elapsed time`() {
        assertEquals(0L, elapsedTelecomCallSeconds(Call.STATE_ACTIVE, 105_000, 104_000))
        assertEquals(94L, elapsedTelecomCallSeconds(Call.STATE_ACTIVE, 10_000, 104_000))
        assertEquals(54L, elapsedTelecomCallSeconds(Call.STATE_HOLDING, 50_000, 104_000))
    }
    @Test fun `secondary actions move below on narrow cards and large fonts`() {
        assertTrue(secondaryCallActionsNeedSeparateRow(288f, 1f, true))
        assertTrue(secondaryCallActionsNeedSeparateRow(400f, 1.5f, true))
        assertTrue(secondaryCallActionsNeedSeparateRow(500f, 1f, true))
        assertFalse(secondaryCallActionsNeedSeparateRow(288f, 1f, false))
        assertTrue(secondaryCallActionsNeedSeparateRow(288f, 1.5f, false))
    }
}

