package dev.goodwy.rphone.view.screen

import android.telecom.Call
import android.telecom.TelecomManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.data.getDisplayName
import java.util.Collections
import java.util.IdentityHashMap
import org.koin.compose.koinInject
import kotlinx.coroutines.CancellationException

internal data class ConferenceParticipantIdentity(
    val call: Call,
    val snapshot: TelecomCallSnapshot,
    val name: String,
    val number: String,
    val photoUri: String?,
    val colorSeed: String = name,
)

/** One live identity source for both the rows and artwork, including off-screen participants. */
@Composable
internal fun rememberConferenceParticipantIdentities(children: List<Call>): List<ConferenceParticipantIdentity> {
    val visited = Collections.newSetFromMap(IdentityHashMap<Call, Boolean>())
    return children.flatMap { collectConferenceIdentities(it, 0, visited) }
}

@Composable
private fun collectConferenceIdentities(
    call: Call,
    depth: Int,
    visited: MutableSet<Call>,
): List<ConferenceParticipantIdentity> {
    if (depth > 16 || !visited.add(call)) return emptyList()
    // Returning values (rather than mutating an accumulator in a restartable Unit composable)
    // propagates snapshot/identity changes to both consumers, including nested child callbacks.
    return key(call) {
        val snapshot by rememberTelecomCallSnapshot(call)
        when {
            snapshot.state == Call.STATE_DISCONNECTED -> emptyList()
            snapshot.children.isNotEmpty() -> snapshot.children.flatMap { collectConferenceIdentities(it, depth + 1, visited) }
            !snapshot.isConference -> listOf(rememberConferenceParticipantIdentity(call, snapshot))
            else -> emptyList()
        }
    }
}

@Composable
private fun rememberConferenceParticipantIdentity(call: Call, snapshot: TelecomCallSnapshot): ConferenceParticipantIdentity {
    val details = snapshot.details
    val number = android.net.Uri.decode(details?.handle?.schemeSpecificPart.orEmpty())
        .takeIf { details?.handlePresentation == TelecomManager.PRESENTATION_ALLOWED }.orEmpty()
    val providedName = details?.callerDisplayName
        ?.takeIf { details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED }
        ?.takeIf { it.isNotBlank() }
    val unknown = stringResource(R.string.label_unknown_number)
    val contacts = koinInject<IContactsRepository>()
    val prefs = koinInject<PreferenceManager>()
    val metadata by koinInject<dev.goodwy.rphone.controller.CallViewModel>().callerMetadataMap.collectAsStateWithLifecycle()
    val photoRevision = dev.goodwy.rphone.view.components.LocalCallPhotoRevision.current
    val displayOrder = prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0)
    val initialName = providedName ?: number.ifBlank { unknown }
    var name by remember(call, number, initialName, displayOrder) { mutableStateOf(initialName) }
    var colorSeed by remember(call, number) { mutableStateOf(initialName) }
    var photo by remember(call, number) { mutableStateOf<String?>(null) }
    LaunchedEffect(call, number, initialName, displayOrder, metadata, photoRevision) {
        val contact = if (number.isBlank()) null else try {
            contacts.getContactByNumber(number)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("ConferenceParticipants", "Unable to resolve participant identity", error)
            null
        }
        name = contact?.let { getDisplayName(it, displayOrder) } ?: initialName
        colorSeed = contact?.displayName ?: name
        photo = if (number in metadata) metadata[number]?.photoUri else
            contact?.photoUri ?: dev.goodwy.rphone.controller.telecomCallerPhotoUri(call)
    }
    return ConferenceParticipantIdentity(call, snapshot, name, number, photo, colorSeed)
}

