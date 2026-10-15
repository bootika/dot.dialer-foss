package dev.goodwy.rphone.debug

import android.telecom.Conference
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle

internal class CallLabConference(
    phoneAccountHandle: PhoneAccountHandle,
) : Conference(phoneAccountHandle) {
    private var disconnecting = false
    init {
        connectionCapabilities = Connection.CAPABILITY_HOLD or
            Connection.CAPABILITY_SUPPORT_HOLD or
            Connection.CAPABILITY_MANAGE_CONFERENCE or
            Connection.CAPABILITY_MERGE_CONFERENCE or
            Connection.CAPABILITY_SWAP_CONFERENCE
    }

    fun attach(connection: Connection) {
        connection.setActive()
        addConnection(connection)
        setActive()
        CallLabRegistry.changed()
    }

    override fun onMerge(connection: Connection) = attach(connection)

    override fun onMerge() {
        connections.forEach(Connection::setActive)
        setActive()
        CallLabRegistry.changed()
    }

    override fun onSeparate(connection: Connection) {
        removeConnection(connection)
        connection.setActive()
        CallLabRegistry.changed()
    }

    override fun onSwap() {
        val children = connections
        val activeIndex = children.indexOfFirst { it.state == Connection.STATE_ACTIVE }
        children.forEachIndexed { index, connection ->
            if (index == activeIndex) connection.setOnHold() else connection.setActive()
        }
        CallLabRegistry.changed()
    }

    override fun onHold() {
        connections.forEach(Connection::setOnHold)
        setOnHold()
        CallLabRegistry.changed()
    }

    override fun onUnhold() {
        connections.forEach(Connection::setActive)
        setActive()
        CallLabRegistry.changed()
    }

    override fun onDisconnect() = disconnectFromLab()

    fun detach(connection: Connection) {
        removeConnection(connection)
        if (connections.isEmpty()) disconnectFromLab()
    }

    fun disconnectFromLab() {
        if (disconnecting || state == Connection.STATE_DISCONNECTED) return
        disconnecting = true
        connections.toList().forEach { connection ->
            if (connection is CallLabConnection) connection.disconnectFromLab()
        }
        if (state != Connection.STATE_DISCONNECTED) {
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        }
        destroy()
        CallLabRegistry.remove(this)
    }
}
