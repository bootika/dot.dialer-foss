package io.github.bootika.dotdialer.core.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLifecycleCoordinatorTest {
    @Test
    fun `publishes reduced state`() {
        val coordinator = CallLifecycleCoordinator()

        val result = coordinator.dispatch(
            CallLifecycleEvent.CallAdded("call-1", CallPhase.RINGING),
        )

        assertSame(result, coordinator.state.value)
        assertEquals("call-1", result.foregroundSessionId)
        assertTrue(result.surfaces.showIncomingNotification)
    }

    @Test
    fun `terminal cleanup followed by late event remains terminal`() {
        val coordinator = CallLifecycleCoordinator()
        coordinator.dispatch(CallLifecycleEvent.CallAdded("call-1", CallPhase.ACTIVE))
        val ended = coordinator.dispatch(CallLifecycleEvent.CallRemoved("call-1"))

        val afterLateEvent = coordinator.dispatch(
            CallLifecycleEvent.PhaseChanged("call-1", CallPhase.ACTIVE),
        )

        assertSame(ended, afterLateEvent)
        assertEquals(CallSurfaceState(), afterLateEvent.surfaces)
    }

    @Test
    fun `service stop is safe when repeated`() {
        val coordinator = CallLifecycleCoordinator()
        coordinator.dispatch(CallLifecycleEvent.CallAdded("call-1", CallPhase.ACTIVE))
        val first = coordinator.dispatch(CallLifecycleEvent.ServiceStopped)
        val second = coordinator.dispatch(CallLifecycleEvent.ServiceStopped)

        assertEquals(first, second)
        assertTrue(second.sessions.isEmpty())
    }
}
