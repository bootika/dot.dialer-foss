package dev.goodwy.rphone.controller.util

import android.telecom.Call

/**
 * Returns the Telecom call which represents the complete conference containing this call.
 * Telecom keeps the individual participants as child calls, so UI code must operate on the
 * parent instead of treating those children as separate simultaneous calls.
 */
internal fun Call.telecomConferenceRoot(): Call {
    var candidate = this
    repeat(MAX_CONFERENCE_DEPTH) {
        val parentCall = runCatching { candidate.parent }.getOrNull() ?: return candidate
        if (parentCall === candidate) return candidate
        candidate = parentCall
    }
    return candidate
}

internal fun Call.belongsToSameTelecomConference(other: Call): Boolean {
    if (this === other) return true

    val thisParent = runCatching { parent }.getOrNull()
    val otherParent = runCatching { other.parent }.getOrNull()
    if (thisParent == null && otherParent == null) return false

    return telecomConferenceRoot() === other.telecomConferenceRoot()
}

/**
 * Conference parent calls commonly have no address or caller display name. Treat the presence of
 * child calls as the source of truth instead of letting those calls fall through to "Unknown".
 */
internal fun Call.isTelecomConference(): Boolean =
    runCatching {
        children.isNotEmpty() || details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
    }.getOrDefault(false)

private const val MAX_CONFERENCE_DEPTH = 8
