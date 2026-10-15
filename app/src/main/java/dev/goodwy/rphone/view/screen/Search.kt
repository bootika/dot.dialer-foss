package dev.goodwy.rphone.view.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.VoiceSearchContract
import dev.goodwy.rphone.view.components.*
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.util.NoteManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.controller.util.placeCallWithSimPreference
import dev.goodwy.rphone.domain.search.DotDialerSearchIndex
import dev.goodwy.rphone.domain.search.DotDialerSearchPayload
import io.github.bootika.dotdialer.core.search.SearchDocumentKind
import io.github.bootika.dotdialer.core.search.SearchOptions
import io.github.bootika.dotdialer.core.search.SearchHit
import io.github.bootika.dotdialer.core.search.SearchSessionAction
import io.github.bootika.dotdialer.core.search.SearchSessionReducer
import io.github.bootika.dotdialer.core.search.SearchSessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Destination<RootGraph>
@Composable
fun SearchScreen(navigator: DestinationsNavigator, autoStartVoiceSearch: Boolean = false, initialQuery: String = "") {
    val permState = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = cardColor //MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(
                top = innerPadding.calculateTopPadding() + 8.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = 0.dp
            )
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize()
        ) {
            ContactSearchContent(
                navigator = navigator,
                isGranted = permState.status == PermissionStatus.Granted,
                onRequestPermission = { permState.launchPermissionRequest() },
                listState = listState,
                initialQuery = initialQuery,
                autoStartVoiceSearch = autoStartVoiceSearch
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ContactSearchContent(
    navigator: DestinationsNavigator,
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    listState: LazyListState,
    autoStartVoiceSearch: Boolean,
    initialQuery: String = "",
) {
    if (!isGranted) {
        PermissionDeniedView(
            icon = Icons.Rounded.People,
            title = stringResource(R.string.contacts_permission),
            description = stringResource(R.string.search_contacts_permission_description),
            onGrantClick = onRequestPermission
        )
        return
    }

    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val contactsVM: ContactsViewModel = koinActivityViewModel()
    val callLogVM: CallLogViewModel = koinActivityViewModel()
    val settingsVer by prefs.settingsChanged.collectAsStateWithLifecycle()

    LaunchedEffect(settingsVer) {
        contactsVM.fetchContactsFull()
    }

    val contacts by contactsVM.allContactsFull.collectAsStateWithLifecycle()
    val callLogs by callLogVM.allCallLogs.collectAsStateWithLifecycle()

    val filterState = remember(settingsVer) { prefs.getSearchFilterState() }
    val displayOrder = remember(settingsVer) { prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0) }
    val normalizeSearchDiacritics = remember(settingsVer) {
        prefs.getBoolean(PreferenceManager.KEY_SEARCH_DIACRITIC_NORMALIZATION, true)
    }
    val simPref = remember(settingsVer) { prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, prefs.getDefaultSimIndexDefault()) }

    var showSimPicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf<String?>(null) }

    var queryFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialQuery))
    }
    val query = queryFieldValue.text
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isInitialized by remember { mutableStateOf(false) }

    fun clearSearchAfterCallInitiated() {
        val nextState = SearchSessionReducer.reduce(
            SearchSessionState(query = query, keyboardVisible = true),
            SearchSessionAction.CallInitiated,
        )
        queryFieldValue = TextFieldValue(nextState.query, selection = TextRange(0))
        if (!nextState.keyboardVisible) keyboardController?.hide()
    }

    fun initiateSearchCall(number: String) {
        val callPlaced = placeCallWithSimPreference(context, number, simPref) {
            pendingNumber = number
            showSimPicker = true
        }
        if (callPlaced) clearSearchAfterCallInitiated()
    }

    if (showSimPicker && pendingNumber != null) {
        SimPickerDialog(
            onDismissRequest = {
                showSimPicker = false
                pendingNumber = null
            },
            onSimSelected = { handle ->
                pendingNumber?.let { makeCall(context, it, handle) }
                showSimPicker = false
                pendingNumber = null
                clearSearchAfterCallInitiated()
            },
        )
    }

    LaunchedEffect(Unit) {
        if (!isInitialized) {
            focusRequester.requestFocus()
            queryFieldValue =
                queryFieldValue.copy(selection = TextRange(queryFieldValue.text.length))
            keyboardController?.show()
            isInitialized = true
        }
    }

    var refreshNoteTrigger by remember { mutableIntStateOf(0) }
    val allNotes = remember(refreshNoteTrigger) { NoteManager.getAllNotes(context) }
    val emptySearchIndex = remember { DotDialerSearchIndex.build(emptyList(), emptyList()) }
    val searchIndex by produceState(
        initialValue = emptySearchIndex,
        contacts,
        callLogs,
        allNotes,
    ) {
        value = withContext(Dispatchers.Default) {
            DotDialerSearchIndex.build(contacts, callLogs, allNotes)
        }
    }
    val enabledResultKinds = remember(
        filterState.contacts,
        filterState.nonContacts,
        filterState.contactNotes,
    ) {
        buildSet {
            if (filterState.contacts) add(SearchDocumentKind.CONTACT)
            if (filterState.nonContacts) add(SearchDocumentKind.UNSAVED_CALL)
            if (filterState.contactNotes) add(SearchDocumentKind.NOTE)
        }
    }
    val unifiedResults by produceState(
        initialValue = emptyList<SearchHit<DotDialerSearchPayload>>(),
        query,
        searchIndex,
        normalizeSearchDiacritics,
        enabledResultKinds,
    ) {
        if (query.isBlank()) {
            value = emptyList()
        } else {
            delay(SEARCH_DEBOUNCE_MILLIS)
            value = withContext(Dispatchers.Default) {
                searchIndex.search(
                    query,
                    SearchOptions(
                        foldDiacritics = normalizeSearchDiacritics,
                        enabledKinds = enabledResultKinds,
                        limit = SEARCH_RESULT_LIMIT,
                    ),
                )
            }
        }
    }
    val hasAnyResults = unifiedResults.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = cardColor,
            shadowElevation = 0.dp
        ) {
            TextField(
                value = queryFieldValue,
                onValueChange = { queryFieldValue = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                trailingIcon = {
                    Row {
                        AnimatedVisibility(
                            visible = query.isNotEmpty(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(onClick = {
                                queryFieldValue = TextFieldValue("", selection = TextRange(0))
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                            }
                        }

                        dev.goodwy.rphone.view.components.VoiceSearchButton(
                            onResult = { queryFieldValue = TextFieldValue(it, selection = TextRange(it.length)); keyboardController?.hide() },
                            autoStart = autoStartVoiceSearch,
                        )

                        SearchFilterButton()
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
        }
        HorizontalDivider()

        AnimatedVisibility(
            visible = query.isNotEmpty() && query.all { it.isDigit() || it == '+' || it == '-' || it == ' ' },
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(DotDialerHandsetIcon, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.call_query, query),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        navigator.navigate(DialPadScreenDestination(initialNumber = query))
                    }) {
                        Icon(Icons.Default.Dialpad, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        when {
            query.isBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            stringResource(R.string.search),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            !hasAnyResults -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SearchOff,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            stringResource(R.string.no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            else -> {
                SearchResultsList(
                    results = unifiedResults,
                    listState = listState,
                    displayOrder = displayOrder,
                    navigator = navigator,
                    onCall = ::initiateSearchCall,
                    onNoteUpdated = { refreshNoteTrigger++ },
                )
            }
        }
    }
}

private const val SEARCH_RESULT_LIMIT = 100
private const val SEARCH_DEBOUNCE_MILLIS = 120L
