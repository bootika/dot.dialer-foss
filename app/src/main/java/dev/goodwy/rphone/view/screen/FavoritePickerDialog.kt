package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.RillAvatar
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer

@Composable
internal fun FavoritePickerDialog(
    contacts: List<Contact>,
    suggestions: List<Contact>,
    displayOrder: Int,
    foldDiacritics: Boolean,
    onAdd: (List<Contact>) -> Unit,
    onDismiss: () -> Unit,
) {
    val expandedDetails = dev.goodwy.rphone.view.components.rememberExpandedListDetails()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val available = remember(contacts) { contacts.filterNot(Contact::isFavorite) }
    val candidates = remember(available, suggestions, query, displayOrder, foldDiacritics) {
        if (query.isBlank()) {
            suggestions
        } else {
            available.asSequence()
                .filter {
                    SearchTextNormalizer.contains(
                        getDisplayName(it, displayOrder),
                        query,
                        foldDiacritics,
                    )
                }
                .sortedBy { SearchTextNormalizer.normalize(getDisplayName(it, displayOrder), true) }
                .toList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_favorites)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_contacts)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { dev.goodwy.rphone.view.components.VoiceSearchButton(onResult = { query = it }) },
                    singleLine = true,
                )
                if (query.isBlank()) {
                    Text(
                        text = stringResource(R.string.frequent_contacts_last_30_days),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    if (candidates.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(
                                    if (query.isBlank()) R.string.no_recent_contact_suggestions
                                    else R.string.no_contacts_found,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(candidates, key = Contact::id) { contact ->
                        val selected = contact.id in selectedIds
                        Surface(
                            onClick = {
                                selectedIds = if (selected) selectedIds - contact.id
                                else selectedIds + contact.id
                            },
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RillAvatar(
                                    name = contact.displayName,
                                    photoUri = contact.photoUri,
                                    modifier = Modifier.size(40.dp),
                                )
                                Text(
                                    text = getDisplayName(contact, displayOrder),
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                    maxLines = if (expandedDetails) Int.MAX_VALUE else 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Checkbox(checked = selected, onCheckedChange = null)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(available.filter { it.id in selectedIds })
                    onDismiss()
                },
                enabled = selectedIds.isNotEmpty(), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text(stringResource(R.string.add_selected_favorites))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.cancel)) }
        },
    )
}
