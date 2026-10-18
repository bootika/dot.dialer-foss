package dev.goodwy.rphone.view.screen

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactFilterInputs
import dev.goodwy.rphone.controller.filterAndSortContacts
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.RillAvatar
import io.github.bootika.dotdialer.core.license.PlanFeature
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

internal fun companyMatchKey(company: String?): String = company.orEmpty().trim()
    .replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

internal fun companyColleagues(contacts: List<Contact>, company: String?, excludeId: String?): List<Contact> {
    val key = companyMatchKey(company)
    if (key.isEmpty()) return emptyList()
    return contacts.filter { !it.isPrivate && !it.isHidden && it.id != excludeId && companyMatchKey(it.company) == key }
        .distinctBy { it.id }.sortedBy { it.displayName.lowercase(Locale.ROOT) }
}

internal data class CompanyColleagueAction(val available: Boolean, val active: Boolean, val open: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberCompanyColleagues(company: String?, excludeId: String?, onSelect: (Contact) -> Unit): CompanyColleagueAction {
    val prefs = koinInject<PreferenceManager>()
    val repository = koinInject<IContactsRepository>()
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val allowed = prefs.allowsFeature(PlanFeature.CALL_PRODUCTIVITY)
    var revision by remember { mutableIntStateOf(0) }
    var contacts by remember(company, excludeId) { mutableStateOf<List<Contact>>(emptyList()) }
    var expanded by remember(company, excludeId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onSelect)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    LaunchedEffect(Unit) { repository.changes.collect { revision++ } }
    LaunchedEffect(company, excludeId, revision, version, allowed) {
        if (!allowed) { contacts = emptyList(); expanded = false; return@LaunchedEffect }
        if (companyMatchKey(company).isBlank() || excludeId == null) { contacts = emptyList(); return@LaunchedEffect }
        contacts = withContext(Dispatchers.IO) {
            try {
                val visible = filterAndSortContacts(ContactFilterInputs(repository.getContactsFull(), null, null,
                    false, false, prefs.getVisibleAccounts()), prefs.getInt(PreferenceManager.KEY_CONTACT_SORT_ORDER, 0))
                companyColleagues(visible, company, excludeId)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: RuntimeException) {
                Log.w("CompanyColleagues", "Contacts unavailable: ${failure.javaClass.simpleName}")
                emptyList()
            }
        }
    }
    if (expanded && allowed) ModalBottomSheet(onDismissRequest = { expanded = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(company.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(contacts, key = { it.id }) { person ->
                ListItem(headlineContent = { Text(person.displayName, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(person.jobTitle.ifBlank { person.phoneNumbers.firstOrNull().orEmpty() }) },
                    leadingContent = { RillAvatar(person.displayName, Modifier.size(44.dp), photoUri = person.photoUri) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    modifier = Modifier.clickable {
                        scope.launch {
                            if (!prefs.requireFeature(PlanFeature.CALL_PRODUCTIVITY)) return@launch
                            val current = try { withContext(Dispatchers.IO) { repository.getContactById(person.id) } }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: RuntimeException) {
                                    Log.w("CompanyColleagues", "Contact unavailable: ${failure.javaClass.simpleName}")
                                    null
                                }
                            expanded = false
                            val visible = current?.let { filterAndSortContacts(ContactFilterInputs(listOf(it), null, null,
                                false, false, prefs.getVisibleAccounts()), 0).isNotEmpty() } == true
                            if (current != null && visible && !current.isHidden && !current.isPrivate && companyMatchKey(current.company) == companyMatchKey(company)) selected(current)
                        }
                    })
            }
        }
    }
    return CompanyColleagueAction(companyMatchKey(company).isNotEmpty() && (!allowed || contacts.isNotEmpty()), allowed) {
        if (prefs.requireFeature(PlanFeature.CALL_PRODUCTIVITY) && contacts.isNotEmpty()) expanded = true
    }
}

@Composable
internal fun ContactCompanyColleagues(company: String?, id: String?, label: String, onSelect: (Contact) -> Unit) {
    val menu = rememberCompanyColleagues(company, id, onSelect)
    Text(label, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.alpha(if (menu.active) 1f else .45f)
            .then(if (menu.available) Modifier.clickable(onClickLabel = stringResource(R.string.company_colleagues), onClick = menu.open) else Modifier))
}
