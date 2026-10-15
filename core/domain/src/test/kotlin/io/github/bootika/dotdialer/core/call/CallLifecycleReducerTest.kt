package io.github.bootika.dotdialer.core.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLifecycleReducerTest {
    @Test
    fun `ended call cannot be revived by a late callback`() {
        val active = reduce(
            CallLifecycleEvent.CallAdded("call-1", CallPhase.ACTIVE),
        )
        val ended = CallLifecycleReducer.reduce(
            active,
            CallLifecycleEvent.CallRemoved("call-1"),
        )

        val afterLateCallback = CallLifecycleReducer.reduce(
            ended,
            CallLifecycleEvent.PhaseChanged("call-1", CallPhase.ACTIVE),
        )

        assertSame(ended, afterLateCallback)
        assertTrue(afterLateCallback.sessions.isEmpty())
        assertEquals(CallSurfaceState(), afterLateCallback.surfaces)
    }

    @Test
    fun `disconnected phase immediately clears every call surface`() {
        val active = reduce(
            CallLifecycleEvent.CallAdded("call-1", CallPhase.ACTIVE),
        )

        val ended = CallLifecycleReducer.reduce(
            active,
            CallLifecycleEvent.PhaseChanged("call-1", CallPhase.ENDED),
        )

        assertNull(ended.foregroundSession)
        assertFalse(ended.surfaces.showCallUi)
        assertFalse(ended.surfaces.showIncomingNotification)
        assertFalse(ended.surfaces.showOngoingNotification)
        assertFalse(ended.surfaces.showFloatingUi)
    }

    @Test
    fun `cleanup from old call cannot remove the new call`() {
        val first = reduce(CallLifecycleEvent.CallAdded("old", CallPhase.ACTIVE))
        val second = CallLifecycleReducer.reduce(
            first,
            CallLifecycleEvent.CallAdded("new", CallPhase.RINGING),
        )

        val afterOldCleanup = CallLifecycleReducer.reduce(
            second,
            CallLifecycleEvent.CallRemoved("old"),
        )

        assertEquals(setOf("new"), afterOldCleanup.sessions.keys)
        assertEquals("new", afterOldCleanup.foregroundSessionId)
        assertTrue(afterOldCleanup.surfaces.showIncomingNotification)
    }

    @Test
    fun `removal is idempotent`() {
        val active = reduce(CallLifecycleEvent.CallAdded("call-1", CallPhase.ACTIVE))
        val once = CallLifecycleReducer.reduce(active, CallLifecycleEvent.CallRemoved("call-1"))
        val twice = CallLifecycleReducer.reduce(once, CallLifecycleEvent.CallRemoved("call-1"))

        assertSame(once, twice)
    }

    @Test
    fun `ringing call has foreground priority over an active call`() {
        val active = reduce(CallLifecycleEvent.CallAdded("active", CallPhase.ACTIVE))
        val withWaitingCall = CallLifecycleReducer.reduce(
            active,
            CallLifecycleEvent.CallAdded("waiting", CallPhase.RINGING),
        )

        assertEquals("waiting", withWaitingCall.foregroundSessionId)
        assertTrue(withWaitingCall.surfaces.showIncomingNotification)
        assertFalse(withWaitingCall.surfaces.showOngoingNotification)
    }

    @Test
    fun `reconciliation removes ghost sessions and preserves framework sessions`() {
        var state = reduce(CallLifecycleEvent.CallAdded("ghost", CallPhase.ACTIVE))
        state = CallLifecycleReducer.reduce(
            state,
            CallLifecycleEvent.CallAdded("real", CallPhase.HELD),
        )

        val reconciled = CallLifecycleReducer.reduce(
            state,
            CallLifecycleEvent.Reconciled(mapOf("real" to CallPhase.ACTIVE)),
        )

        assertEquals(setOf("real"), reconciled.sessions.keys)
        assertEquals(CallPhase.ACTIVE, reconciled.foregroundSession?.phase)
        assertTrue("ghost" in reconciled.endedSessionIds)
    }

    @Test
    fun `service stop clears active state deterministically`() {
        var state = reduce(CallLifecycleEvent.CallAdded("call-1", CallPhase.ACTIVE))
        state = CallLifecycleReducer.reduce(
            state,
            CallLifecycleEvent.CallAdded("call-2", CallPhase.HELD),
        )

        val stopped = CallLifecycleReducer.reduce(state, CallLifecycleEvent.ServiceStopped)

        assertTrue(stopped.sessions.isEmpty())
        assertNull(stopped.foregroundSessionId)
        assertEquals(setOf("call-1", "call-2"), stopped.endedSessionIds)
        assertEquals(CallSurfaceState(), stopped.surfaces)
    }

    @Test
    fun `duplicate add and duplicate phase do not create new state`() {
        val ringing = reduce(CallLifecycleEvent.CallAdded("call-1", CallPhase.RINGING))
        val duplicateAdd = CallLifecycleReducer.reduce(
            ringing,
            CallLifecycleEvent.CallAdded("call-1", CallPhase.RINGING),
        )
        val duplicatePhase = CallLifecycleReducer.reduce(
            duplicateAdd,
            CallLifecycleEvent.PhaseChanged("call-1", CallPhase.RINGING),
        )

        assertSame(ringing, duplicateAdd)
        assertSame(ringing, duplicatePhase)
        assertEquals(1, ringing.sessions.size)
    }

    @Test
    fun `unknown late event cannot create a session`() {
        val state = CallLifecycleReducer.reduce(
            CallLifecycleState(),
            CallLifecycleEvent.PhaseChanged("unknown", CallPhase.ACTIVE),
        )

        assertEquals(CallLifecycleState(), state)
    }

    private fun reduce(vararg events: CallLifecycleEvent): CallLifecycleState = events.fold(
        CallLifecycleState(),
        CallLifecycleReducer::reduce,
    )
}
