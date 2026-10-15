package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import android.accounts.Account
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.repository.ContactsRepository.RawContactInfo
import dev.goodwy.rphone.view.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContactMergeDuplicatesScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val vm: ContactsViewModel = koinActivityViewModel()
    val revision by vm.contactRevision.collectAsStateWithLifecycle()
    val busy by vm.maintenance.busy.collectAsStateWithLifecycle()
    val displayOrder by vm.displayOrder.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<List<Contact>>>(emptyList()) }
    var sources by remember { mutableStateOf<Map<String,List<RawContactInfo>>>(emptyMap()) }
    var selected by remember { mutableStateOf<List<Contact>?>(null) }
    var copying by remember { mutableStateOf<List<Contact>?>(null) }
    var region by remember { mutableStateOf<String?>(null) }
    val labels = remember(refresh) { loadContactAccountProviderLabels(context) }
    val failure = stringResource(R.string.contact_maintenance_error)
    val success = stringResource(R.string.contact_maintenance_done)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh, revision, busy) {
        if (busy) return@LaunchedEffect
        loading = true; failed = false
        try {
            region = resolveMergeRegion(context)
            val loaded = vm.maintenance.duplicateGroups()
            val raw = mutableMapOf<String,List<RawContactInfo>>()
            for (contact in loaded.flatten()) raw[contact.id] = vm.maintenance.sources(contact.id)
            groups = loaded; sources = raw
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) {
            android.util.Log.w("ContactMaintenance", "Duplicate load failed: ${error.javaClass.simpleName}")
            failed = true
        } finally { loading = false }
    }
    selected?.let { group ->
        AlertDialog(onDismissRequest={ if(!busy) selected=null },
            title={ Text(stringResource(R.string.merge_duplicates,group.size)) },
            text={ Text(stringResource(R.string.contact_merge_confirm)) },
            confirmButton={ TextButton(enabled=!busy,onClick={
                selected=null
                scope.launch {
                    val result=vm.maintenance.execute { it.mergeContacts(group.first().id,group.drop(1).map(Contact::id)) }
                    refresh++
                    snackbar.showSnackbar(if(result.isSuccess) success else failure)
                }
            }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.merge_duplicates,group.size)) } },
            dismissButton={ TextButton(enabled=!busy,onClick={selected=null}, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.cancel)) } })
    }
    copying?.let { group ->
        AlertDialog(onDismissRequest={copying=null},
            title={Text(stringResource(R.string.copy_diagnostics))},
            text={Text(stringResource(R.string.contact_merge_copy_notice))},
            confirmButton={TextButton(onClick={
                val report=buildString {
                    appendLine("CONTACT MERGE REPORT"); appendLine("region=${region ?: "unknown/ambiguous"}")
                    for(c in group) {
                        appendLine("contact_id=${c.id}")
                        c.phoneNumbers.forEach { appendLine("number=$it; e164=${canonicalMergeNumber(it,region) ?: "unresolved"}") }
                        sources[c.id].orEmpty().forEach { appendLine("raw_id=${it.rawContactId}; account_type=${it.accountType}; account_name=${it.accountName}; read_only=${it.isReadOnly}") }
                    }
                }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Contact merge report",report))
                copying=null
            }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {Text(stringResource(R.string.copy_diagnostics))}},
            dismissButton={TextButton(onClick={copying=null}, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {Text(stringResource(R.string.cancel))}})
    }
    Scaffold(topBar={TopAppBar(title={Title(stringResource(R.string.merging_contacts))},
        navigationIcon={NavigationIcon(onClick=navigator::navigateUp)},
        actions={RillIconButton(onClick={refresh++},imageVector=Icons.Rounded.Refresh,
            contentDescription=stringResource(R.string.refresh))})},
        snackbarHost={SnackbarHost(snackbar)}) { padding ->
        LazyColumn(modifier=Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.contact_merge_description),style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(8.dp)) }
            if(loading || busy) item { RillLoadingIndicatorView() }
            else if(failed) item { Text(failure); TextButton(onClick={refresh++}, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {Text(stringResource(R.string.refresh))} }
            else if(groups.isEmpty()) item { Text(stringResource(R.string.no_duplicates_found),modifier=Modifier.padding(16.dp)) }
            else items(groups,key={it.map(Contact::id).sorted().joinToString("|")}) { group ->
                RillExpressiveCard {
                    Row(Modifier.fillMaxWidth().padding(start=16.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text(stringResource(R.string.contact_merge_same_number),modifier=Modifier.weight(1f),
                            style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        RillIconButton(onClick={copying=group},imageVector=Icons.Rounded.ContentCopy,
                            contentDescription=stringResource(R.string.copy_diagnostics))
                    }
                    for(contact in group) {
                        ContactListItem(contact=contact,navigator=navigator,displayOrder=displayOrder)
                        for(source in sources[contact.id].orEmpty()) {
                            val provider=source.accountType?.let { type ->
                                contactAccountProviderName(Account(source.accountName?.takeIf(String::isNotBlank) ?: type,type),labels[type])
                            } ?: stringResource(R.string.contact_source_local)
                            Text(listOfNotNull(provider,source.accountName?.takeIf(String::isNotBlank)).joinToString(" · "),
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier=Modifier.padding(start=72.dp,end=16.dp,bottom=8.dp))
                        }
                    }
                    Button(onClick={selected=group},enabled=!busy,modifier=Modifier.fillMaxWidth().padding(8.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primaryContainer,
                            contentColor=MaterialTheme.colorScheme.onPrimaryContainer)) {
                        Text(stringResource(R.string.merge_duplicates,group.size))
                    }
                }
            }
            item { SettingsBottomPadding() }
        }
    }
}
