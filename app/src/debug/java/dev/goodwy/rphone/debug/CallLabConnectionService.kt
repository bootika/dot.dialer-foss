package dev.goodwy.rphone.debug

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import androidx.core.os.BundleCompat
import java.lang.ref.WeakReference

internal class CallLabConnectionService : ConnectionService() {
    override fun onCreate() {
        super.onCreate()
        currentService = WeakReference(this)
    }

    override fun onDestroy() {
        if (currentService?.get() === this) currentService = null
        super.onDestroy()
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection = createConnection(request, outgoing = false)

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection = createConnection(request, outgoing = true)

    override fun onConference(connection1: Connection, connection2: Connection) {
        val conference = CallLabConference(CallLabContract.phoneAccountHandle(this))
        conference.attach(connection1)
        conference.attach(connection2)
        addConference(conference)
        CallLabRegistry.add(conference)
    }

    private fun createConnection(request: ConnectionRequest, outgoing: Boolean): CallLabConnection {
        val extras = request.extras
        val callExtras = BundleCompat.getParcelable(
            extras,
            android.telecom.TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
            android.os.Bundle::class.java,
        ) ?: extras
        val address = request.address ?: BundleCompat.getParcelable(
            extras,
            android.telecom.TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
            android.net.Uri::class.java,
        )
        val connection = CallLabConnection(
            number = address?.schemeSpecificPart.orEmpty().ifBlank { CallLabContract.PRIMARY_NUMBER },
            name = callExtras.getString(CallLabContract.EXTRA_NAME).orEmpty().ifBlank {
                if (outgoing) CallLabContract.OUTGOING_NAME else CallLabContract.PRIMARY_NAME
            },
            autoConnect = callExtras.getBoolean(CallLabContract.EXTRA_AUTO_CONNECT, outgoing),
            quality = callExtras.getString(CallLabContract.EXTRA_QUALITY),
            outgoing = outgoing,
        )
        CallLabRegistry.add(connection)
        return connection
    }

    companion object {
        private var currentService: WeakReference<CallLabConnectionService>? = null

        fun conferenceAll(): Boolean {
            val service = currentService?.get() ?: return false
            val connections = CallLabRegistry.activeConnections().filter { it.conference == null }
            if (connections.size < 2) return false
            val conference = CallLabConference(CallLabContract.phoneAccountHandle(service))
            connections.forEach(conference::attach)
            service.addConference(conference)
            CallLabRegistry.add(conference)
            return true
        }
    }
}
