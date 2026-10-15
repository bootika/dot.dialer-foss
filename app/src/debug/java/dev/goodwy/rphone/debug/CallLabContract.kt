package dev.goodwy.rphone.debug

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccountHandle

internal object CallLabContract {
    const val ACTION = "dev.goodwy.rphone.debug.CALL_LAB"
    const val EXTRA_COMMAND = "command"
    const val EXTRA_NUMBER = "number"
    const val EXTRA_NAME = "name"
    const val EXTRA_AUTO_CONNECT = "auto_connect"
    const val EXTRA_QUALITY = "quality"
    const val QUALITY_HD_PLUS = "hd_plus"
    const val PHONE_ACCOUNT_ID = "dot-dialer-call-lab"

    const val COMMAND_REGISTER = "register"
    const val COMMAND_INCOMING = "incoming"
    const val COMMAND_OUTGOING = "outgoing"
    const val COMMAND_ACTIVE = "active"
    const val COMMAND_HOLD = "hold"
    const val COMMAND_RESUME = "resume"
    const val COMMAND_CONFERENCE = "conference"
    const val COMMAND_DISCONNECT = "disconnect"
    const val COMMAND_RESET = "reset"

    const val PRIMARY_NUMBER = "0700000101"
    const val PRIMARY_NAME = "Test Alexandru Butica"
    const val SECONDARY_NUMBER = "0700000102"
    const val SECONDARY_NAME = "Mara Ionescu"
    const val OUTGOING_NUMBER = "0700000199"
    const val OUTGOING_NAME = PRIMARY_NAME

    fun phoneAccountHandle(context: Context): PhoneAccountHandle = PhoneAccountHandle(
        ComponentName(context, CallLabConnectionService::class.java),
        PHONE_ACCOUNT_ID,
    )
}
