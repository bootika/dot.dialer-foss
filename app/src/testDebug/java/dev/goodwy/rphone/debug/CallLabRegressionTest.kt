package dev.goodwy.rphone.debug

import android.app.Application
import android.content.ComponentName
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CallLabRegressionTest {
    @Before fun before() = CallLabRegistry.reset()
    @After fun after() = CallLabRegistry.reset()

    private fun connection(number: String = "0700000101") = CallLabConnection(
        number, "Test Alexandru Butica", false, null, false,
    ).also(CallLabRegistry::add)

    @Test fun modernRejectDisconnectsAndRemovesTheIncomingFixture() {
        val call = connection()
        call.onReject(1)
        assertEquals(Connection.STATE_DISCONNECTED, call.state)
        assertEquals(DisconnectCause.REJECTED, call.disconnectCause.code)
        assertEquals(0, CallLabRegistry.snapshot.value.connectionCount)
    }

    @Test fun replyRejectAlsoDisconnectsTheIncomingFixture() {
        val call = connection()
        call.onReject("Unavailable")
        assertEquals(Connection.STATE_DISCONNECTED, call.state)
        assertEquals(0, CallLabRegistry.snapshot.value.connectionCount)
    }

    @Test fun mergeActivatesChildrenAndIndividualEndDoesNotEndTheOtherChild() {
        val first = connection().apply { setLabActive() }
        val second = connection("0700000102").apply { setLabOnHold() }
        val parent = CallLabConference(PhoneAccountHandle(
            ComponentName("dot.dialer.test", CallLabConnectionService::class.java.name), "qa-lab",
        )).also(CallLabRegistry::add)
        parent.attach(first)
        parent.attach(second)
        assertEquals(Connection.STATE_ACTIVE, first.state)
        assertEquals(Connection.STATE_ACTIVE, second.state)
        assertTrue(second.connectionCapabilities and Connection.CAPABILITY_DISCONNECT_FROM_CONFERENCE != 0)
        second.onDisconnect()
        assertEquals(Connection.STATE_ACTIVE, first.state)
        assertEquals(Connection.STATE_ACTIVE, parent.state)
        assertEquals(1, parent.connections.size)
        first.onDisconnect()
        assertEquals(Connection.STATE_DISCONNECTED, parent.state)
        assertEquals(0, CallLabRegistry.snapshot.value.connectionCount)
        assertEquals(0, CallLabRegistry.snapshot.value.conferenceCount)
    }
}
