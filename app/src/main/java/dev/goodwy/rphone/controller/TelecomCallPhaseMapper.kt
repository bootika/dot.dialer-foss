package dev.goodwy.rphone.controller

import android.telecom.Call
import io.github.bootika.dotdialer.core.call.CallPhase

internal fun telecomCallPhase(state: Int): CallPhase = when (state) {
    Call.STATE_RINGING -> CallPhase.RINGING
    Call.STATE_SELECT_PHONE_ACCOUNT,
    Call.STATE_CONNECTING,
    Call.STATE_DIALING,
    -> CallPhase.DIALING

    Call.STATE_ACTIVE -> CallPhase.ACTIVE
    Call.STATE_HOLDING -> CallPhase.HELD
    Call.STATE_DISCONNECTING -> CallPhase.ENDING
    Call.STATE_DISCONNECTED -> CallPhase.ENDED
    else -> CallPhase.DIALING
}
