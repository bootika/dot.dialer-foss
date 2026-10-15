package dev.goodwy.rphone.view.screen

import android.telecom.Call

internal data class CallControlAvailability(
    val primaryAction: Boolean,
    val mute: Boolean,
    val audioRoute: Boolean,
    val keypad: Boolean,
    val more: Boolean,
)

/**
 * Keeps unavailable call actions visibly disabled instead of letting a tap fail silently.
 * The end-call action is intentionally excluded because it remains independently available.
 */
internal fun callControlAvailability(
    callState: Int,
    canHold: Boolean,
    canMute: Boolean,
    isConference: Boolean = false,
    supportedAudioRouteCount: Int,
    hasOtherCall: Boolean,
): CallControlAvailability {
    val isDialingOrConnecting =
        callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING
    val isActive = callState == Call.STATE_ACTIVE
    val isHolding = callState == Call.STATE_HOLDING
    val hasCallAudio = isDialingOrConnecting || isActive || isHolding

    return CallControlAvailability(
        primaryAction = !hasOtherCall && canHold && (isActive || isHolding),
        // Mute is an InCallService audio control. Some Telecom implementations omit the
        // per-call capability from a conference parent even though muting still works.
        mute = (canMute || isConference) && (isDialingOrConnecting || isActive),
        audioRoute = hasCallAudio && supportedAudioRouteCount > 1,
        keypad = isActive,
        more = isActive || isHolding,
    )
}
