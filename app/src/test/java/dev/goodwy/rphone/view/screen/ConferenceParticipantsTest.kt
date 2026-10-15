package dev.goodwy.rphone.view.screen

import android.telecom.Call
import android.app.Application
import dev.goodwy.rphone.controller.util.isTelecomConference
import java.util.Collections
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowCall
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, shadows = [RecordingConferenceCallShadow::class])
class ConferenceParticipantsTest {
    @Test fun `disconnect targets only the participant and never the conference parent`() {
        val parent = call()
        val participant = call(canDisconnect = true)
        val other = call(canDisconnect = true)
        children(parent, listOf(participant, other))
        assertTrue(endConferenceParticipant(parent, participant))
        assertEquals(1, shadow(participant).disconnects)
        assertEquals(0, shadow(parent).disconnects)
        assertEquals(0, shadow(other).disconnects)
    }

    @Test fun `a removed participant cannot be disconnected from a stale row`() {
        val parent = call()
        val participant = call(canDisconnect = true)
        children(parent, listOf(participant))
        assertTrue(canEndConferenceParticipant(parent, participant))
        children(parent, emptyList())
        assertFalse(endConferenceParticipant(parent, participant))
        assertEquals(0, shadow(participant).disconnects)
    }

    @Test fun `capability and current states are required again at tap time`() {
        val parent = call()
        val participant = call(canDisconnect = false)
        children(parent, listOf(participant))
        assertFalse(endConferenceParticipant(parent, participant))
        ReflectionHelpers.setField(participant.details, "mCallCapabilities", Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)
        assertTrue(canEndConferenceParticipant(parent, participant))
        for (state in listOf(Call.STATE_CONNECTING, Call.STATE_RINGING, Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED)) {
            ReflectionHelpers.setField(parent.details, "mState", state)
            assertFalse(endConferenceParticipant(parent, participant))
        }
        ReflectionHelpers.setField(parent.details, "mState", Call.STATE_HOLDING)
        assertTrue(canEndConferenceParticipant(parent, participant))
        ReflectionHelpers.setField(participant.details, "mState", Call.STATE_DISCONNECTING)
        assertFalse(endConferenceParticipant(parent, participant))
        assertEquals(0, shadow(parent).disconnects)
        assertEquals(0, shadow(participant).disconnects)
    }

    @Test fun `nested conferences flatten without duplicates or disconnected participants`() {
        val first = call()
        val second = call()
        val ended = call(state = Call.STATE_DISCONNECTED)
        val nested = call()
        children(nested, listOf(first, second, ended))
        assertEquals(listOf(first, second), conferenceParticipantCalls(listOf(nested, first)))
        children(nested, listOf(nested, first))
        assertEquals(listOf(first), conferenceParticipantCalls(listOf(nested)))
    }

    @Test fun `a conference with no reported children remains a conference`() {
        val parent = call()
        ReflectionHelpers.setField(parent.details, "mCallProperties", Call.Details.PROPERTY_CONFERENCE)
        assertTrue(parent.isTelecomConference())
        assertFalse(canEndConferenceParticipant(parent, parent))
    }

    @Test fun `single presentation follows two to one to two without changing the conference`() {
        val parent = call()
        val first = call()
        val second = call()
        children(parent, listOf(first, second))
        assertNull(singleConferencePresentationCall(parent, listOf(first, second)))
        children(parent, listOf(first))
        assertSame(first, singleConferencePresentationCall(parent, listOf(first)))
        assertTrue(parent.isTelecomConference())
        children(parent, listOf(first, second))
        assertNull(singleConferencePresentationCall(parent, listOf(first, second)))
        assertEquals(0, shadow(parent).disconnects)
    }

    @Test fun `unknown nested topology cannot masquerade as one participant`() {
        val parent = call()
        val first = call()
        val unknown = call()
        ReflectionHelpers.setField(unknown.details, "mCallProperties", Call.Details.PROPERTY_CONFERENCE)
        children(parent, listOf(first, unknown))
        assertNull(singleConferencePresentationCall(parent, listOf(first)))
        children(parent, emptyList())
        assertNull(singleConferencePresentationCall(parent, emptyList()))
    }

    @Test fun `nested participant can supply identity while terminal and cyclic topology cannot`() {
        val parent = call()
        val nested = call()
        val first = call(state = Call.STATE_HOLDING)
        children(nested, listOf(first))
        children(parent, listOf(nested))
        assertSame(first, singleConferencePresentationCall(parent, listOf(first)))
        ReflectionHelpers.setField(first.details, "mState", Call.STATE_DISCONNECTING)
        assertNull(singleConferencePresentationCall(parent, listOf(first)))
        ReflectionHelpers.setField(first.details, "mState", Call.STATE_ACTIVE)
        children(nested, listOf(first, nested))
        assertNull(singleConferencePresentationCall(parent, listOf(first)))
    }

    @Test fun `ended child does not prevent a known remaining caller but disconnecting child does`() {
        val parent = call()
        val first = call()
        val second = call(state = Call.STATE_DISCONNECTING)
        children(parent, listOf(first, second))
        assertNull(singleConferencePresentationCall(parent, listOf(first)))
        ReflectionHelpers.setField(second.details, "mState", Call.STATE_DISCONNECTED)
        assertSame(first, singleConferencePresentationCall(parent, listOf(first)))
    }

    private fun call(state: Int = Call.STATE_ACTIVE, canDisconnect: Boolean = false): Call {
        val call = Shadow.newInstanceOf(Call::class.java)
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        ReflectionHelpers.setField(details, "mState", state)
        ReflectionHelpers.setField(details, "mCallCapabilities", if (canDisconnect) Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE else 0)
        ReflectionHelpers.setField(call, "mDetails", details)
        ReflectionHelpers.setField(call, "mState", state)
        children(call, emptyList())
        return call
    }

    private fun children(call: Call, children: List<Call>) {
        ReflectionHelpers.setField(call, "mChildren", ArrayList(children))
        ReflectionHelpers.setField(call, "mUnmodifiableChildren", Collections.unmodifiableList(children))
        ReflectionHelpers.setField(call, "mChildrenIds", ArrayList(children.indices.map(Int::toString)))
        ReflectionHelpers.setField(call, "mChildrenCached", true)
    }

    private fun shadow(call: Call) = Shadow.extract<RecordingConferenceCallShadow>(call)
}

@Implements(Call::class)
class RecordingConferenceCallShadow : ShadowCall() {
    var disconnects = 0
        private set
    @Implementation fun disconnect() { disconnects++ }
}
