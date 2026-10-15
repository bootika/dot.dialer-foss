package dev.goodwy.rphone.view.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.getDisplayName
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer

/** Both Contacts entry points use the same query, rows, menus, grouping and alphabet rail. */
@Composable
internal fun ContactDirectoryContent(
    contacts: List<Contact>,
    query: String,
    displayOrder: Int,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    descending: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: ((String) -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
) {
    val visible = remember(contacts, query, displayOrder) {
        filterContactDirectory(contacts, query, displayOrder)
    }
    AZListContent(
        contacts = visible, navigator = navigator, modifier = modifier, listState = listState,
        selectedIds = selectedIds, onToggleSelection = onToggleSelection,
        descending = descending, headerContent = headerContent,
    )
}

internal fun filterContactDirectory(contacts: List<Contact>, query: String, displayOrder: Int): List<Contact> {
    val normalized = SearchTextNormalizer.normalize(query.trim(), foldDiacritics = true)
    if (normalized.isBlank()) return contacts
    return contacts.filter { contact ->
        SearchTextNormalizer.normalize(getDisplayName(contact, displayOrder), foldDiacritics = true)
            .contains(normalized) || contact.phoneNumbers.any { number ->
            SearchTextNormalizer.normalize(number, foldDiacritics = true).contains(normalized)
        }
    }
}
