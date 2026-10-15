package dev.goodwy.rphone.controller.util

import android.telecom.Call

internal fun resolvePreferredTelecomCall(calls: List<Call>, preferred: Call?): Call? {
    if (preferred == null || preferred !in calls || preferred.currentTelecomState() == Call.STATE_DISCONNECTED) {
        return null
    }

    return preferred.telecomConferenceRoot().takeIf { it in calls } ?: preferred
}

internal fun selectPriorityTelecomCall(calls: List<Call>, preferred: Call?): Call? {
    val activePreferred = preferred?.takeIf {
        it.currentTelecomState() != Call.STATE_DISCONNECTED &&
            it.currentTelecomState() != Call.STATE_HOLDING
    }

    return calls.find { it.currentTelecomState() == Call.STATE_RINGING }
        ?: activePreferred
        ?: calls.find {
            it.currentTelecomState() == Call.STATE_DIALING ||
                it.currentTelecomState() == Call.STATE_CONNECTING
        }
        ?: calls.find {
            it.currentTelecomState() == Call.STATE_ACTIVE &&
                runCatching { it.parent }.getOrNull() == null &&
                runCatching { it.children.isNotEmpty() }.getOrDefault(false)
        }
        ?: calls.find { it.currentTelecomState() == Call.STATE_ACTIVE }
        ?: calls.find { it === preferred }
        ?: calls.find { it.currentTelecomState() == Call.STATE_HOLDING }
        ?: calls.firstOrNull { it.currentTelecomState() != Call.STATE_DISCONNECTED }
}

/**
 * Selects the other logical call session shown beside [current]. Conference participants are
 * represented by child Calls, but every child must collapse to its conference root before the UI
 * chooses a second session. Otherwise a third incoming/outgoing call can accidentally hold, swap
 * or disconnect one participant instead of the complete conference.
 */
internal fun selectOtherLogicalTelecomCall(calls: List<Call>, current: Call): Call? {
    val currentRoot = current.telecomConferenceRoot()
    val logicalCalls = mutableListOf<Call>()

    calls.forEach { candidate ->
        val root = candidate.telecomConferenceRoot()
        if (root.currentTelecomState() != Call.STATE_DISCONNECTED &&
            logicalCalls.none { existing -> existing === root }
        ) {
            logicalCalls += root
        }
    }

    val others = logicalCalls.filterNot { candidate -> candidate === currentRoot }
    return others.firstOrNull { it.currentTelecomState() == Call.STATE_RINGING }
        ?: others.firstOrNull {
            it.currentTelecomState() == Call.STATE_DIALING ||
                it.currentTelecomState() == Call.STATE_CONNECTING
        }
        ?: others.firstOrNull { it.currentTelecomState() == Call.STATE_ACTIVE }
        ?: others.firstOrNull { it.currentTelecomState() == Call.STATE_HOLDING }
        ?: others.firstOrNull()
}
