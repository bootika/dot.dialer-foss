package dev.goodwy.rphone.view.screen

import android.telecom.Call
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.controller.util.isTelecomConference
import java.util.Collections
import java.util.IdentityHashMap

/** Presentation only: Telecom actions, session, duration and logical-call selection keep the parent. */
internal fun singleConferencePresentationCall(parent: Call, reported: List<Call>): Call? {
    if (!parent.isTelecomConference()) return null
    val single = reported.singleOrNull() ?: return null
    if (single.currentTelecomState() !in listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING)) return null
    val visited = Collections.newSetFromMap(IdentityHashMap<Call, Boolean>())
    val leaves = mutableListOf<Call>()
    fun complete(call: Call, depth: Int): Boolean {
        if (depth > 16) return false
        if (!visited.add(call)) return false
        if (call.currentTelecomState() == Call.STATE_DISCONNECTED) return true
        val children = call.children
        if (children.isNotEmpty()) return children.all { complete(it, depth + 1) }
        if (call.isTelecomConference()) return false // Operator has not reported all participants.
        leaves += call
        return true
    }
    if (parent.children.isEmpty() || !parent.children.all { complete(it, 0) }) return null
    return single.takeIf { leaves.size == 1 && leaves.single() === it }
}
