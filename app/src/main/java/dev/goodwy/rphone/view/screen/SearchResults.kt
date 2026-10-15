package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.forceLtr
import dev.goodwy.rphone.domain.search.DotDialerSearchPayload
import dev.goodwy.rphone.modal.data.getDisplayContactInfo
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import dev.goodwy.rphone.view.components.DotUnknownCallerIcon
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillSectionHeader
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.SingleTile
import dev.goodwy.rphone.view.components.performAppHaptic
import io.github.bootika.dotdialer.core.search.SearchHit
import org.koin.compose.koinInject
import kotlin.text.ifEmpty

@Composable
internal fun SearchResultsList(
    results: List<SearchHit<DotDialerSearchPayload>>,
    listState: LazyListState,
    displayOrder: Int,
    navigator: DestinationsNavigator,
    onCall: (String) -> Unit,
    onNoteUpdated: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()

    fun performConfiguredHaptic() {
        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
            performAppHaptic(
                context,
                prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f),
            )
        }
    }

    ScrollHapticsEffect(listState = listState)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            RillSectionHeader(
                title = stringResource(R.string.search_results),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            RillExpressiveCard {
                results.forEach { result ->
                    when (val payload = result.document.payload) {
                        is DotDialerSearchPayload.ContactResult -> {
                            val contact = payload.contact
                            val phoneNumber = contact.phoneDetails.firstOrNull { it.isPrimary }?.number
                                ?: contact.phoneNumbers.firstOrNull()
                            SingleTile(
                                title = getDisplayName(contact, displayOrder),
                                subtitle = getDisplayContactInfo(contact),
                                photoUri = contact.photoUri,
                                                avatarColorSeed = contact.displayName,
                                phoneNumber = phoneNumber,
                                trailingContent = phoneNumber?.let { number ->
                                    {
                                        IconButton(onClick = { onCall(number) }) {
                                            Icon(
                                                DotDialerHandsetIcon,
                                                contentDescription = stringResource(R.string.call),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                                onCall = { phoneNumber?.let(onCall) },
                                onClick = {
                                    performConfiguredHaptic()
                                    navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                                },
                            )
                        }

                        is DotDialerSearchPayload.CallResult -> {
                            val entry = payload.call
                            val isUnknown = entry.name.isNullOrEmpty() || entry.name == entry.number
                            val displayName = if (entry.name == entry.number) {
                                entry.number.forceLtr()
                            } else {
                                entry.name?.ifEmpty { entry.number }
                                    ?: entry.number.ifEmpty {
                                        stringResource(R.string.unknown_caller_actions)
                                    }
                            }
                            SingleTile(
                                title = displayName,
                                subtitle = entry.number.takeUnless { isUnknown },
                                icon = DotUnknownCallerIcon.takeIf { isUnknown },
                                phoneNumber = entry.number,
                                trailingContent = {
                                    IconButton(onClick = { onCall(entry.number) }) {
                                        Icon(
                                            DotDialerHandsetIcon,
                                            contentDescription = stringResource(R.string.call),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                onCall = { onCall(entry.number) },
                                onClick = {
                                    performConfiguredHaptic()
                                    navigator.navigate(
                                        ContactDetailsScreenDestination(phoneNumber = entry.number),
                                    )
                                },
                                showCreateContact = entry.contactId == null,
                            )
                        }

                        is DotDialerSearchPayload.NoteResult -> key(result.document.key) {
                            val note = payload.note
                            var showNoteEditor by remember { mutableStateOf(false) }
                            SingleTile(
                                title = note.contactName.ifBlank {
                                    note.phoneNumber.ifBlank {
                                        stringResource(R.string.unknown_caller_actions)
                                    }
                                },
                                subtitle = note.content,
                                icon = Icons.AutoMirrored.Outlined.StickyNote2,
                                phoneNumber = note.phoneNumber,
                                supportingContent = {
                                    Text(
                                        note.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                },
                                onClick = { showNoteEditor = true },
                                useLongClick = false,
                            )
                            if (showNoteEditor) {
                                NoteEditorDialog(
                                    contactName = note.contactName,
                                    phoneNumber = note.phoneNumber,
                                    onDismiss = {
                                        showNoteEditor = false
                                        onNoteUpdated()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(300.dp)) }
    }
}

