package dev.goodwy.rphone.debug

import android.telecom.Conference
import android.telecom.Connection
import android.telecom.DisconnectCause
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CallLabSnapshot(
    val connectionCount: Int = 0,
    val conferenceCount: Int = 0,
    val latestState: Int? = null,
)

internal object CallLabRegistry {
    private val connections = linkedSetOf<CallLabConnection>()
    private val conferences = linkedSetOf<CallLabConference>()
    private val mutableSnapshot = MutableStateFlow(CallLabSnapshot())

    val snapshot: StateFlow<CallLabSnapshot> = mutableSnapshot.asStateFlow()

    fun add(connection: CallLabConnection) {
        connections += connection
        refreshConferenceables()
        publish()
    }

    fun remove(connection: CallLabConnection) {
        connections -= connection
        refreshConferenceables()
        publish()
    }

    fun add(conference: CallLabConference) {
        conferences += conference
        refreshConferenceables()
        publish()
    }

    fun remove(conference: CallLabConference) {
        conferences -= conference
        refreshConferenceables()
        publish()
    }

    fun changed() {
        refreshConferenceables()
        publish()
    }

    fun latest(): CallLabConnection? = connections.lastOrNull {
        it.state != Connection.STATE_DISCONNECTED
    }

    fun activeConnections(): List<CallLabConnection> = connections.filter {
        it.state != Connection.STATE_DISCONNECTED
    }

    fun setLatestActive() {
        val target = latest() ?: return
        connections
            .filter { it !== target && it.state == Connection.STATE_ACTIVE }
            .forEach(CallLabConnection::setLabOnHold)
        target.setLabActive()
    }

    fun holdLatest() = latest()?.setLabOnHold()

    fun resumeLatest() = latest()?.setLabActive()

    fun disconnectLatest() = latest()?.disconnectFromLab()

    fun reset() {
        conferences.toList().forEach { it.disconnectFromLab() }
        connections.toList().forEach { connection ->
            if (connection.state != Connection.STATE_DISCONNECTED) {
                connection.disconnectFromLab()
            }
        }
        publish()
    }

    private fun refreshConferenceables() {
        val liveConnections = activeConnections()
        val liveConferences = conferences.filter { it.state != Connection.STATE_DISCONNECTED }
        liveConnections.forEach { connection ->
            if (connection.conference != null) {
                connection.setConferenceables(emptyList())
            } else {
                val candidates = buildList<android.telecom.Conferenceable> {
                    addAll(liveConnections.filter { it !== connection && it.conference == null })
                    addAll(liveConferences)
                }
                connection.setConferenceables(candidates)
            }
        }
    }

    private fun publish() {
        val liveConnections = activeConnections()
        mutableSnapshot.value = CallLabSnapshot(
            connectionCount = liveConnections.size,
            conferenceCount = conferences.count { it.state != Connection.STATE_DISCONNECTED },
            latestState = liveConnections.lastOrNull()?.state,
        )
    }
}
