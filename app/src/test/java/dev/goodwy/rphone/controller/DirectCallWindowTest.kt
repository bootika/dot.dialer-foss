package dev.goodwy.rphone.controller

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DirectCallWindowTest {
    @After fun clear() = DirectCallWindow.cancel()

    @Test fun `duplicate taps never queue extra calls`() {
        var calls = 0
        assertTrue(DirectCallWindow.begin("0700000011", null) { calls++ })
        val id = checkNotNull(DirectCallWindow.state.value).id
        assertFalse(DirectCallWindow.begin("0700000012", null) { calls += 100 })
        DirectCallWindow.commit(id)
        DirectCallWindow.commit(id)
        assertEquals(1, calls)
    }

    @Test fun `cancelled timer cannot submit a later request`() {
        var calls = 0
        DirectCallWindow.begin("0700000011", null) { calls += 100 }
        val oldId = checkNotNull(DirectCallWindow.state.value).id
        DirectCallWindow.cancel()
        DirectCallWindow.begin("0700000012", null) { calls++ }
        val currentId = checkNotNull(DirectCallWindow.state.value).id
        DirectCallWindow.commit(oldId)
        assertEquals(0, calls)
        DirectCallWindow.commit(currentId)
        assertEquals(1, calls)
    }

    @Test fun `submitted presentation stays until its exact call arrives`() {
        DirectCallWindow.begin("0700000011", null) { }
        val id = checkNotNull(DirectCallWindow.state.value).id
        DirectCallWindow.commit(id)
        assertTrue(checkNotNull(DirectCallWindow.state.value).submitted)
        DirectCallWindow.consume("another-call")
        DirectCallWindow.consume(null)
        assertNotNull(DirectCallWindow.state.value)
        DirectCallWindow.consume(id)
        assertNull(DirectCallWindow.state.value)
    }

    @Test fun `end during Telecom delivery only disconnects exact outgoing request`() {
        var disconnects = 0
        DirectCallWindow.begin("0700000011", null) { }
        val id = checkNotNull(DirectCallWindow.state.value).id
        DirectCallWindow.commit(id)
        assertFalse(DirectCallWindow.requestCancellation())
        DirectCallWindow.bindTelecomCall(null, true) { disconnects += 100 }
        DirectCallWindow.bindTelecomCall("other", true) { disconnects += 100 }
        DirectCallWindow.bindTelecomCall(id, false) { disconnects += 100 }
        assertEquals(0, disconnects)
        DirectCallWindow.bindTelecomCall(id, true) { disconnects++ }
        assertEquals(1, disconnects)
        assertNull(DirectCallWindow.state.value)
    }

    @Test fun `failure clears only the matching submitted request`() {
        DirectCallWindow.begin("0700000011", null) { }
        val id = checkNotNull(DirectCallWindow.state.value).id
        DirectCallWindow.commit(id)
        DirectCallWindow.fail("old-request")
        assertNotNull(DirectCallWindow.state.value)
        DirectCallWindow.fail(id)
        assertNull(DirectCallWindow.state.value)
    }
}
