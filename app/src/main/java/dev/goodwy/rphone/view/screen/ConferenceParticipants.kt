package dev.goodwy.rphone.view.screen

import android.telecom.Call
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.controller.util.isTelecomConference
import dev.goodwy.rphone.view.components.DotDialerHangupIcon
import dev.goodwy.rphone.view.theme.color_call_end
import java.util.Collections
import java.util.IdentityHashMap

/** Flatten nested conference parents without duplicating participants or following cycles. */
internal fun conferenceParticipantCalls(children: List<Call>): List<Call> {
    val visited = Collections.newSetFromMap(IdentityHashMap<Call, Boolean>())
    val participants = mutableListOf<Call>()
    fun visit(call: Call, depth: Int) {
        if (depth > 16 || !visited.add(call)) return
        if (call.currentTelecomState() == Call.STATE_DISCONNECTED) return
        val nested = call.children
        if (nested.isEmpty()) {
            if (!call.isTelecomConference()) participants.add(call)
        } else nested.forEach { visit(it, depth + 1) }
    }
    children.forEach { visit(it, 0) }
    return participants
}

internal fun canEndConferenceParticipant(parent: Call, participant: Call): Boolean =
    participant !== parent &&
        participant in conferenceParticipantCalls(parent.children) &&
        parent.currentTelecomState() in listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING) &&
        participant.currentTelecomState() in listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING) &&
        participant.details?.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE) == true

/** Recheck the current topology/capability on tap; never substitute a parent disconnect. */
internal fun endConferenceParticipant(parent: Call, participant: Call): Boolean {
    if (!canEndConferenceParticipant(parent, participant)) return false
    return try {
        participant.disconnect()
        true
    } catch (error: RuntimeException) {
        Log.w("ConferenceParticipants", "Unable to disconnect conference participant", error)
        false
    }
}

@Composable
internal fun SmallCallAvatar(
    name: String,
    number: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    isConference: Boolean = false,
    colorSeed: String? = null,
) {
    Surface(modifier = modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        if (isConference) {
            Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.padding(7.dp))
        } else {
            CalmCallAvatar(photoUri, name, number, avatarSize = 36.dp, colorSeed = colorSeed)
        }
    }
}

@Composable
internal fun ConferenceParticipants(parent: Call, identities: List<ConferenceParticipantIdentity>, parentState: Int, modifier: Modifier = Modifier) {
    val participants = identities.map { it.call }
    val keys = remember(parent) { ParticipantKeys() }
    keys.retain(participants)
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (participants.isEmpty()) {
                Text(stringResource(R.string.conference_participants_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            } else {
                Text(
                    pluralStringResource(R.plurals.conference_participant_count, participants.size, participants.size),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag("conferenceParticipantList"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(identities, key = { keys.key(it.call) }) { identity ->
                        ConferenceParticipantRow(parent, identity, parentState)
                    }
                }
            }
        }
    }
}

/** Bundle-compatible, stable keys even for callers with the same or a withheld number. */
private class ParticipantKeys {
    private val identities = IdentityHashMap<Call, Long>()
    private var nextKey = 0L
    fun key(call: Call): Long = identities.getOrPut(call) { nextKey++ }
    fun retain(calls: List<Call>) {
        val active = Collections.newSetFromMap(IdentityHashMap<Call, Boolean>()).apply { addAll(calls) }
        identities.keys.removeAll { it !in active }
    }
}

@Composable
private fun ConferenceParticipantRow(parent: Call, identity: ConferenceParticipantIdentity, parentState: Int) {
    val participant = identity.call
    val snapshot = identity.snapshot
    val details = snapshot.details
    val name = identity.name
    val enabled = parentState in listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING) && canEndConferenceParticipant(parent, participant)
    val endDescription = stringResource(R.string.end_conference_participant, name)
    val connectTime = details?.connectTimeMillis ?: 0L
    val elapsed = rememberCallElapsedSeconds(snapshot.state, connectTime)
    val status = stringResource(
        when (snapshot.state) {
            Call.STATE_HOLDING -> R.string.call_status_on_hold
            Call.STATE_ACTIVE -> R.string.call_status_active
            Call.STATE_RINGING -> R.string.call_status_incoming
            Call.STATE_DIALING -> R.string.call_status_calling
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> R.string.call_status_ended
            else -> R.string.call_status_connecting
        },
    )
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("conferenceParticipantRow"),
        shape = RoundedCornerShape(16.dp),
        color = dev.goodwy.rphone.view.theme.MyColors.bottomBarColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmallCallAvatar(name, identity.number, identity.photoUri, colorSeed = identity.colorSeed, modifier = Modifier.testTag("conferenceParticipantAvatar"))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(status, style = MaterialTheme.typography.labelSmall, color = callParticipantStatusColor(snapshot.state, MaterialTheme.colorScheme), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CallElapsedTimer(elapsed, "conferenceParticipantTimer")
            SecondaryCallAction(
                icon = DotDialerHangupIcon,
                label = endDescription,
                tint = color_call_end,
                compactEnd = true,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
                testTag = "conferenceParticipantEndAction",
                onClick = { endConferenceParticipant(parent, participant) },
            )
        }
    }
}

