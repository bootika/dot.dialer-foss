package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.view.theme.accentText

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CallLogFullScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.expandedListDetails
import dev.goodwy.rphone.controller.util.getRecentTapAction
import dev.goodwy.rphone.controller.util.getSwipeRightAction
import dev.goodwy.rphone.controller.util.getSwipeLeftAction
import dev.goodwy.rphone.controller.util.copyToClipboard
import dev.goodwy.rphone.controller.util.forceLtr
import dev.goodwy.rphone.controller.util.formatRecentCallTimestamp
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.controller.util.getCallingAccounts
import dev.goodwy.rphone.controller.util.shareContactsAsVCard
import dev.goodwy.rphone.domain.search.DotDialerHomeDirectory
import dev.goodwy.rphone.domain.search.DotDialerHomeSections
import dev.goodwy.rphone.domain.search.DotDialerSearchIndex
import dev.goodwy.rphone.domain.search.DotDialerSearchPayload
import dev.goodwy.rphone.modal.data.CallLogFilter
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.getDisplayContactInfo
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.IPhoneFavoritesRow
import dev.goodwy.rphone.view.components.BatchCallLogActionBar
import dev.goodwy.rphone.view.components.ManageContactGroupsDialog
import dev.goodwy.rphone.view.components.NumberPickerDialog
import dev.goodwy.rphone.view.components.FAVORITES_AVATAR_BOOST_DP
import dev.goodwy.rphone.view.components.LocalRillAvatarAppearance
import dev.goodwy.rphone.view.components.PermissionDeniedView
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.SimPickerDialog
import dev.goodwy.rphone.view.components.SwipeableHomeContainer
import dev.goodwy.rphone.view.components.callStatusColor
import dev.goodwy.rphone.view.components.contactAvatarSizeDp
import dev.goodwy.rphone.view.components.resolveCallLogVisual
import dev.goodwy.rphone.view.components.rillAvatarAppearance
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import dev.goodwy.rphone.view.components.DialpadFloatingActionButton
import dev.goodwy.rphone.view.theme.callLogSelectionColor
import io.github.bootika.dotdialer.core.search.LocalSearchIndex
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalMaterial3Api::class,
    coil.annotation.ExperimentalCoilApi::class,
)
@Composable
fun UnifiedHomeScreen(navigator: DestinationsNavigator) {
    val permissionState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS),
    )
    if (!permissionState.allPermissionsGranted) {
        PermissionDeniedView(
            icon = Icons.Default.Person,
            title = stringResource(R.string.contacts_permission),
            description = stringResource(R.string.search_contacts_permission_description),
            onGrantClick = permissionState::launchMultiplePermissionRequest,
        )
        return
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val keyboard = LocalSoftwareKeyboardController.current
    val shareContactLabel = stringResource(R.string.share_contact)
    val shareLabel = stringResource(R.string.share)
    val prefs = koinInject<PreferenceManager>()
    val statistics = remember(context, prefs) { io.github.bootika.dotdialer.observability.ProductStatistics.get(context, prefs) }
    val contactsViewModel: ContactsViewModel = koinActivityViewModel()
    val callLogViewModel: CallLogViewModel = koinActivityViewModel()
    val settingsVersion by prefs.settingsChanged.collectAsStateWithLifecycle()
    val showAllRecentCalls = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS, false)
    }
    LaunchedEffect(settingsVersion) { statistics.homeVisible() }
    val allContacts by contactsViewModel.allContacts.collectAsStateWithLifecycle()
    val contacts by contactsViewModel.filteredContacts.collectAsStateWithLifecycle()
    val callLogs by callLogViewModel.allCallLogs.collectAsStateWithLifecycle()
    val contactsLoading by contactsViewModel.isLoading.collectAsStateWithLifecycle()
    val callLogsLoading by callLogViewModel.isLoading.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    AcknowledgeMissedCallsEffect(callLogViewModel)
    // Unlike call logs, ContactsViewModel does not load its provider snapshot in init.
    // Home is now the primary surface, so it must request that snapshot itself instead of
    // depending on the removed Contacts tab to have been opened first.
    LaunchedEffect(contactsViewModel) {
        contactsViewModel.fetchContacts()
    }

    val displayOrder = remember(settingsVersion) {
        prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0)
    }
    val normalizeDiacritics = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_SEARCH_DIACRITIC_NORMALIZATION, true)
    }
    val repeatContactsInSearchResults = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_REPEAT_CONTACTS_IN_SEARCH_RESULTS, false)
    }
    val showFavorites = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_SHOW_RECENTS_FAVORITES, true)
    }
    val recentTapAction = remember(settingsVersion) { prefs.getRecentTapAction() }
    val clearSearchAfterCall = remember(settingsVersion) {
        prefs.getBoolean(
            PreferenceManager.KEY_CLEAR_SEARCH_AFTER_CALL,
            PreferenceManager.DEFAULT_CLEAR_SEARCH_AFTER_CALL,
        )
    }
    val swipeEnabled = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_SWIPE_TO_CALL, PreferenceManager.DEFAULT_SWIPE_ENABLED)
    }
    val swipeRightAction = remember(settingsVersion) { prefs.getSwipeRightAction() }
    val swipeLeftAction = remember(settingsVersion) { prefs.getSwipeLeftAction() }
    val haptics = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)
    }
    val hapticsStrength = remember(settingsVersion) {
        prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light"
    }
    val hapticsIntensity = remember(settingsVersion) {
        prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
    }
    val simPreference = remember(settingsVersion) {
        prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, prefs.getDefaultSimIndexDefault())
    }
    val avatarAppearance = remember(settingsVersion) { prefs.rillAvatarAppearance() }
    val contactAvatarSize = remember(settingsVersion) { prefs.contactAvatarSizeDp() }
    val contactNameSize = remember(settingsVersion) {
        prefs.getInt(PreferenceManager.KEY_CONTACT_NAME_SIZE_SP, 18).coerceIn(16, 22)
    }
    val expandedListDetails = remember(settingsVersion) { prefs.expandedListDetails() }

    var query by remember { mutableStateOf("") }
    val actionableNumber = remember(query) { actionableDialQuery(query) }
    var browseContacts by rememberSaveable { mutableStateOf(false) }
    var showDialpad by remember { mutableStateOf(false) }
    var showCallFilter by remember { mutableStateOf(false) }
    var selectedCallFilter by remember { mutableStateOf(CallLogFilter.All) }
    var showFavoritePicker by remember { mutableStateOf(false) }
    var editFavorites by remember { mutableStateOf(false) }
    var showSimPicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf<String?>(null) }
    var pendingContactId by remember { mutableStateOf<String?>(null) }
    var callbackTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var contextTarget by remember { mutableStateOf<HomeContextTarget?>(null) }
    var pendingContactNumberAction by remember { mutableStateOf<PendingContactNumberAction?>(null) }
    var favoriteNumberTarget by remember { mutableStateOf<Contact?>(null) }
    var groupsContact by remember { mutableStateOf<Contact?>(null) }
    var deleteContactTarget by remember { mutableStateOf<Contact?>(null) }
    var deleteCallTarget by remember { mutableStateOf<PendingCallDeletion?>(null) }
    var selectedContactIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedRecentIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(showAllRecentCalls) { selectedRecentIds = emptySet() }
    var isRefreshing by remember { mutableStateOf(false) }
    val tourTargets = remember { mutableStateMapOf<HomeTourTarget, Rect>() }
    var tourFavoritesHeader by remember { mutableStateOf<Rect?>(null) }
    var tourFavoritesRow by remember { mutableStateOf<Rect?>(null) }
    var showHomeTour by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = browseContacts && selectedContactIds.isEmpty() && selectedRecentIds.isEmpty()) {
        browseContacts = false
        query = ""
        keyboard?.hide()
    }
    BackHandler(enabled = selectedContactIds.isNotEmpty() || selectedRecentIds.isNotEmpty()) {
        selectedContactIds = emptySet()
        selectedRecentIds = emptySet()
    }

    fun refreshHome() {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.imageLoader.memoryCache?.clear()
                        context.imageLoader.diskCache?.clear()
                    }
                }.onFailure { error ->
                    Log.w("UnifiedHomeScreen", "Unable to clear the avatar image cache", error)
                }
                supervisorScope {
                    listOf(
                        async { runCatching { contactsViewModel.refreshContactsAndWait() } },
                        async { runCatching { callLogViewModel.refreshLogsAndWait() } },
                    ).awaitAll().forEach { result ->
                        result.exceptionOrNull()?.let { error ->
                            Log.w("UnifiedHomeScreen", "Unable to refresh a home data source", error)
                        }
                    }
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    val favorites = remember(allContacts, settingsVersion) {
        val order = prefs.getFavoritesOrder()
        allContacts.filter(Contact::isFavorite).sortedWith(
            compareBy<Contact> {
                order.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            }.thenBy { getDisplayName(it, displayOrder) },
        )
    }
    val favoriteSuggestions = remember(allContacts, callLogs, displayOrder) {
        suggestedFavoriteContacts(
            contacts = allContacts,
            callLogs = callLogs,
            nowMillis = System.currentTimeMillis(),
            displayOrder = displayOrder,
        )
    }
    // Recents and search must resolve identity from the same source-filtered snapshot shown in
    // Contacts. Otherwise a hidden provider row can resurrect an older avatar for the same number.
    val contactsById = remember(contacts) { contacts.associateBy(Contact::id) }
    val contactsByPhone = remember(contacts) { buildContactsByPhone(contacts) }
    LaunchedEffect(settingsVersion, contactsLoading, callLogsLoading, tourTargets.size) {
        val coreTargetsReady = HomeTourTarget.Search in tourTargets && HomeTourTarget.Dialpad in tourTargets
        if (!contactsLoading && !callLogsLoading && coreTargetsReady &&
            !prefs.getBoolean(PreferenceManager.KEY_HOME_UI_TOUR_SHOWN, true)
        ) {
            delay(450)
            showHomeTour = true
        }
    }

    val emptyIndex = remember { DotDialerSearchIndex.build(emptyList(), emptyList()) }
    val searchIndex by produceState<LocalSearchIndex<DotDialerSearchPayload>>(
        initialValue = emptyIndex,
        contacts,
        callLogs,
        showAllRecentCalls,
    ) {
        value = withContext(Dispatchers.Default) {
            DotDialerSearchIndex.build(contacts, callLogs, showAllCalls = showAllRecentCalls)
        }
    }
    val sections by produceState(
        initialValue = DotDialerHomeSections(emptyList(), emptyList()),
        query,
        searchIndex,
        normalizeDiacritics,
        repeatContactsInSearchResults,
        contacts,
    ) {
        if (query.isNotBlank()) delay(HOME_SEARCH_DEBOUNCE_MILLIS)
        value = withContext(Dispatchers.Default) {
            DotDialerHomeDirectory.sections(
                index = searchIndex,
                query = query,
                foldDiacritics = normalizeDiacritics,
                repeatContactsInSearchResults = repeatContactsInSearchResults,
                contactOrder = contacts.withIndex().associate { (index, contact) ->
                    contact.id to index
                },
                visibleContactIds = contacts.mapTo(mutableSetOf(), Contact::id),
            )
        }
    }
    val filteredRecents = remember(sections.recents, selectedCallFilter) {
        sections.recents.filter { document ->
            val call = (document.payload as DotDialerSearchPayload.CallResult).call
            callMatchesFilter(call, selectedCallFilter)
        }
    }
    val recentRows = remember(filteredRecents, contactsById, contactsByPhone, configuration, displayOrder) {
        filteredRecents.map { document ->
            val call = (document.payload as DotDialerSearchPayload.CallResult).call
            val currentContact = resolveCurrentContact(call, contactsById, contactsByPhone)
            val currentCall = if (currentContact != null) {
                call.copy(
                    contactId = currentContact.id,
                    photoUri = currentContact.photoUri,
                )
            } else {
                call
            }
            HomeRecentRow(
                document = document,
                call = currentCall,
                contact = currentContact,
                visual = resolveCallLogVisual(
                    type = currentCall.type,
                    durationSeconds = currentCall.duration,
                    isBlocked = currentCall.isBlocked,
                ),
                title = currentContact?.let { getDisplayName(it, displayOrder) }
                    ?: currentCall.name?.takeUnless { it == currentCall.number }
                    ?: currentCall.number,
                formattedTime = context.formatRecentCallTimestamp(currentCall.date),
            )
        }
    }
    val visibleContacts = sections.contacts
    val avatarColorPlan = remember(recentRows, visibleContacts, displayOrder) { buildHomeAvatarColorPlan(recentRows.map { (it.contact?.displayName ?: it.title) to it.call.number }, visibleContacts.map { (it.payload as DotDialerSearchPayload.ContactResult).contact.displayName }) }
    fun call(number: String) {
        val normalized = number.filter(Char::isDigit).takeLast(10)
        val contactId = allContacts.firstOrNull { contact ->
            contact.phoneNumbers.any { it.filter(Char::isDigit).takeLast(10) == normalized }
        }?.id
        val contactAccount = contactId?.let(prefs::getFavoriteSim)
            ?: prefs.getFavoriteSimForNumber(number)
        val appAccount = prefs.getString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, null)
        val shouldAsk = getCallingAccounts(context).size > 1 &&
            contactAccount == null && appAccount == null && simPreference == 0
        if (shouldAsk) {
            pendingNumber = number
            pendingContactId = contactId
            showSimPicker = true
        } else {
            makeCall(context, number, contactId = contactId)
        }
        if (!shouldAsk && clearSearchAfterCall) {
            query = ""
            keyboard?.hide()
        }
    }

    fun runContactNumberAction(action: ContactNumberMenuAction, number: String, name: String? = null) {
        when (action) {
            ContactNumberMenuAction.WhatsApp -> performHomeAction(
                PreferenceManager.SWIPE_ACTION_WHATSAPP, context, number, onCall = ::call, onContact = {})
            ContactNumberMenuAction.Call -> call(number)
            ContactNumberMenuAction.Message -> context.startActivity(
                Intent(Intent.ACTION_VIEW, "sms:$number".toUri())
            )
            ContactNumberMenuAction.EditBeforeCall ->
                navigator.navigate(DialPadScreenDestination(initialNumber = number))
            ContactNumberMenuAction.Copy -> context.copyToClipboard(number)
            ContactNumberMenuAction.CopyIdentity -> context.copyToClipboard(dev.goodwy.rphone.controller.util.contactIdentityText(name, number))
        }
    }

    fun chooseContactNumber(contact: Contact, action: ContactNumberMenuAction) {
        val numbers = contact.phoneNumbers.distinct()
        when (numbers.size) {
            0 -> Unit
            1 -> runContactNumberAction(action, numbers.first(), contact.displayName)
            else -> pendingContactNumberAction = PendingContactNumberAction(contact, action)
        }
    }

    pendingContactNumberAction?.let { pending ->
        NumberPickerDialog(
            numbers = pending.contact.phoneNumbers.distinct(),
            onDismissRequest = { pendingContactNumberAction = null },
            onNumberSelected = { number ->
                pendingContactNumberAction = null
                runContactNumberAction(pending.action, number, pending.contact.displayName)
            },
        )
    }

    favoriteNumberTarget?.let { contact ->
        val selectedNumber = favoriteCallNumber(contact, prefs.getFavoriteNumber(contact.id))
        NumberPickerDialog(
            numbers = contact.phoneNumbers.filter(String::isNotBlank).distinct(),
            selectedNumber = selectedNumber,
            title = stringResource(R.string.favorite_call_number),
            onDismissRequest = { favoriteNumberTarget = null },
            onNumberSelected = { number ->
                prefs.setFavoriteNumber(contact.id, number)
                favoriteNumberTarget = null
            },
        )
    }

    groupsContact?.let { contact ->
        ManageContactGroupsDialog(
            contact = contact,
            contactsViewModel = contactsViewModel,
            onDismissRequest = { groupsContact = null },
        )
    }

    deleteContactTarget?.let { contact ->
        AlertDialog(
            onDismissRequest = { deleteContactTarget = null },
            title = { Text(stringResource(R.string.delete_contact)) },
            text = { Text(stringResource(R.string.delete_contact_subtitle)) },
            confirmButton = {
                TextButton(onClick = {
                    contactsViewModel.deleteContact(contact.id)
                    deleteContactTarget = null
                }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteContactTarget = null }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    deleteCallTarget?.let { deletion ->
        AlertDialog(
            onDismissRequest = { deleteCallTarget = null },
            title = {
                Text(stringResource(if (deletion.group) R.string.delete_call_group else R.string.delete_call))
            },
            text = {
                Text(stringResource(if (deletion.group) R.string.delete_call_group_subtitle else R.string.delete_call_subtitle))
            },
            confirmButton = {
                TextButton(onClick = {
                    callLogViewModel.deleteCallLogsByIds(
                        if (deletion.group) deletion.call.ids.distinct() else listOf(deletion.call.id)
                    )
                    deleteCallTarget = null
                }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCallTarget = null }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showSimPicker && pendingNumber != null) {
        SimPickerDialog(
            onDismissRequest = {
                showSimPicker = false
                pendingNumber = null
                pendingContactId = null
            },
            onSimSelected = { handle ->
                pendingNumber?.let { makeCall(context, it, handle, pendingContactId) }
                showSimPicker = false
                pendingNumber = null
                pendingContactId = null
                if (clearSearchAfterCall) {
                    query = ""
                    keyboard?.hide()
                }
            },
        )
    }

    if (showFavoritePicker) {
        FavoritePickerDialog(
            contacts = allContacts,
            suggestions = favoriteSuggestions,
            displayOrder = displayOrder,
            foldDiacritics = normalizeDiacritics,
            onAdd = contactsViewModel::addFavorites,
            onDismiss = { showFavoritePicker = false },
        )
    }

    if (showCallFilter) {
        ModalBottomSheet(
            onDismissRequest = { showCallFilter = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.filter_results),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                CallLogFilter.entries.filterNot { it == CallLogFilter.Contacts }.forEach { filter ->
                    Surface(
                        onClick = {
                            selectedCallFilter = filter
                            showCallFilter = false
                        },
                        color = Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedCallFilter == filter,
                                onClick = null,
                            )
                            Text(
                                text = stringResource(filter.stringRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    callbackTarget?.let { (number, name) ->
        RecentCallBackReminder(number, name, prefs) { callbackTarget = null }
    }
    contextTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { contextTarget = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 28.dp),
            ) {
                when (target) {
                    is HomeContextTarget.Recent -> {
                        val log = target.call
                        if (log.type == android.provider.CallLog.Calls.MISSED_TYPE && log.number.isNotBlank()) {
                            item { HomeMenuAction(stringResource(R.string.remind_to_call), Icons.Default.History,
                                enabled = true, locked = !prefs.allowsFeature(io.github.bootika.dotdialer.core.license.PlanFeature.CALL_PRODUCTIVITY), subtitle = if (prefs.allowsFeature(io.github.bootika.dotdialer.core.license.PlanFeature.CALL_PRODUCTIVITY)) null else "Power Up") {
                                if (dev.goodwy.rphone.controller.util.CallBackReminderScheduler.pending(context, log.number) != null ||
                                    prefs.requireFeature(io.github.bootika.dotdialer.core.license.PlanFeature.CALL_PRODUCTIVITY)) {
                                    callbackTarget = log.number to (target.contact?.displayName ?: log.name.orEmpty())
                                    contextTarget = null
                                }
                            } }
                        }
                        item { HomeMenuAction(stringResource(R.string.select), Icons.Default.CheckBox) {
                            selectedContactIds = emptySet()
                            selectedRecentIds = selectedRecentIds + log.id
                            contextTarget = null
                        } }
                        item { HomeMenuAction(stringResource(R.string.call), DotDialerHandsetIcon, enabled = log.number.isNotBlank()) {
                            contextTarget = null
                            call(log.number)
                        } }
                        item { HomeMenuAction(stringResource(R.string.message), Icons.AutoMirrored.Filled.Message, enabled = log.number.isNotBlank()) {
                            contextTarget = null
                            context.startActivity(Intent(Intent.ACTION_VIEW, "sms:${log.number}".toUri()))
                        } }
                        item { HomeMenuAction(stringResource(R.string.edit_number_before_call), Icons.Default.Dialpad, enabled = log.number.isNotBlank()) {
                            contextTarget = null
                            navigator.navigate(DialPadScreenDestination(initialNumber = log.number))
                        } }
                        item { HomeMenuAction(stringResource(R.string.copy_name_number), Icons.Default.ContentCopy, enabled = log.number.isNotBlank()) {
                            contextTarget = null
                            context.copyToClipboard(dev.goodwy.rphone.controller.util.contactIdentityText(target.contact?.displayName ?: log.name, log.number))
                        } }
                        item { HomeMenuAction(stringResource(R.string.copy_number), Icons.Default.ContentCopy, enabled = log.number.isNotBlank()) {
                            contextTarget = null
                            context.copyToClipboard(log.number)
                        } }
                        item {
                            HomeMenuAction(
                                stringResource(if (target.contact == null) R.string.create_contact else R.string.view_contact),
                                Icons.Default.Person,
                            ) {
                                contextTarget = null
                                if (target.contact == null) {
                                    navigator.navigateToNewContact(log.number, avatarColorPlan.recentIndexByNumber[log.number])
                                } else {
                                    navigator.navigate(
                                        ContactDetailsScreenDestination(
                                            contactId = target.contact.id,
                                            phoneNumber = log.number,
                                        )
                                    )
                                }
                            }
                        }
                        item { HomeMenuAction(stringResource(R.string.show_full_history), Icons.Default.History) {
                            contextTarget = null
                            navigator.navigate(
                                CallLogFullScreenDestination(
                                    contactId = target.contact?.id,
                                    phoneNumber = log.number,
                                )
                            )
                        } }
                        item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                        item { HomeMenuAction(stringResource(R.string.delete_call), Icons.Default.Delete, destructive = true) {
                            contextTarget = null
                            deleteCallTarget = PendingCallDeletion(log, group = false)
                        } }
                        if (log.ids.distinct().size > 1) {
                            item { HomeMenuAction(stringResource(R.string.delete_call_group), Icons.Default.Delete, destructive = true) {
                                contextTarget = null
                                deleteCallTarget = PendingCallDeletion(log, group = true)
                            } }
                        }
                    }
                    is HomeContextTarget.ContactTarget -> {
                        val contact = target.contact
                        item { HomeMenuAction(stringResource(R.string.select), Icons.Default.CheckBox) {
                            selectedRecentIds = emptySet()
                            selectedContactIds = selectedContactIds + contact.id
                            contextTarget = null
                        } }
                        item { HomeMenuAction(stringResource(R.string.call), DotDialerHandsetIcon, enabled = contact.phoneNumbers.isNotEmpty()) {
                            contextTarget = null
                            chooseContactNumber(contact, ContactNumberMenuAction.Call)
                        } }
                        item { HomeMenuAction(stringResource(R.string.message), Icons.AutoMirrored.Filled.Message, enabled = contact.phoneNumbers.isNotEmpty()) {
                            contextTarget = null
                            chooseContactNumber(contact, ContactNumberMenuAction.Message)
                        } }
                        item { HomeMenuAction(stringResource(R.string.edit_number_before_call), Icons.Default.Dialpad, enabled = contact.phoneNumbers.isNotEmpty()) {
                            contextTarget = null
                            chooseContactNumber(contact, ContactNumberMenuAction.EditBeforeCall)
                        } }
                        item { HomeMenuAction(stringResource(R.string.copy_name_number), Icons.Default.ContentCopy, enabled = contact.phoneNumbers.isNotEmpty()) {
                            contextTarget = null
                            chooseContactNumber(contact, ContactNumberMenuAction.CopyIdentity)
                        } }
                        item { HomeMenuAction(stringResource(R.string.copy_number), Icons.Default.ContentCopy, enabled = contact.phoneNumbers.isNotEmpty()) {
                            contextTarget = null
                            chooseContactNumber(contact, ContactNumberMenuAction.Copy)
                        } }
                        item {
                            HomeMenuAction(
                                stringResource(if (contact.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites),
                                Icons.Default.Star,
                            ) {
                                contextTarget = null
                                contactsViewModel.toggleFavorite(contact)
                            }
                        }
                        item { HomeMenuAction(stringResource(R.string.manage_groups), Icons.Default.Group, enabled = !contact.isPrivate) {
                            contextTarget = null
                            groupsContact = contact
                        } }
                        item { HomeMenuAction(shareContactLabel, Icons.Default.Share) {
                            contextTarget = null
                            shareContactsAsVCard(context, listOf(contact), shareContactLabel)
                        } }
                        item { HomeMenuAction(stringResource(R.string.edit_contact), Icons.Default.Edit) {
                            contextTarget = null
                            navigator.navigate(ContactEditScreenDestination(contactId = contact.id))
                        } }
                        item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                        item { HomeMenuAction(stringResource(R.string.delete_contact), Icons.Default.Delete, destructive = true) {
                            contextTarget = null
                            deleteContactTarget = contact
                        } }
                    }
                    is HomeContextTarget.Favorite -> {
                        val contact = target.contact
                        item { HomeMenuAction(stringResource(R.string.call), DotDialerHandsetIcon, enabled = contact.phoneNumbers.isNotEmpty()) {
                            contextTarget = null
                            chooseContactNumber(contact, ContactNumberMenuAction.Call)
                        } }
                        item { HomeMenuAction(stringResource(R.string.message), Icons.AutoMirrored.Filled.Message, enabled = contact.phoneNumbers.isNotEmpty()) {
                            contextTarget = null
                            chooseContactNumber(contact, ContactNumberMenuAction.Message)
                        } }
                        if (contact.phoneNumbers.filter(String::isNotBlank).distinct().size > 1) {
                            item {
                                val number = favoriteCallNumber(contact, prefs.getFavoriteNumber(contact.id)).orEmpty()
                                HomeMenuAction(
                                    label = stringResource(R.string.favorite_call_number),
                                    icon = Icons.Default.CheckCircle,
                                    subtitle = stringResource(R.string.favorite_call_number_value, number.forceLtr()),
                                ) {
                                    contextTarget = null
                                    favoriteNumberTarget = contact
                                }
                            }
                        }
                        item { HomeMenuAction(stringResource(R.string.view_contact), Icons.Default.Person) {
                            contextTarget = null
                            navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                        } }
                        item { HomeMenuAction(stringResource(R.string.edit_contact), Icons.Default.Edit) {
                            contextTarget = null
                            navigator.navigate(ContactEditScreenDestination(contactId = contact.id))
                        } }
                        item { HomeMenuAction(shareContactLabel, Icons.Default.Share) {
                            contextTarget = null
                            shareContactsAsVCard(context, listOf(contact), shareContactLabel)
                        } }
                        item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                        item { HomeMenuAction(stringResource(R.string.remove_from_favorites), Icons.Default.Star) {
                            contextTarget = null
                            contactsViewModel.toggleFavorite(contact)
                        } }
                        item { HomeMenuAction(stringResource(R.string.delete_contact), Icons.Default.Delete, destructive = true) {
                            contextTarget = null
                            deleteContactTarget = contact
                        } }
                    }
                }
            }
        }
    }

    if (showDialpad) {
        ModalBottomSheet(
            onDismissRequest = { showDialpad = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            sheetGesturesEnabled = false,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            scrimColor = Color.Transparent,
            dragHandle = null,
            contentWindowInsets = {
                if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    WindowInsets(0)
                } else {
                    BottomSheetDefaults.modalWindowInsets
                }
            },
        ) {
            DialPadContent(
                navigator = navigator,
                onDismiss = { showDialpad = false },
                isBottomSheet = true,
            )
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            when {
                selectedRecentIds.isNotEmpty() -> {
                    val selectedRows = recentRows.filter { it.call.id in selectedRecentIds }
                    BatchCallLogActionBar(
                        selectedCount = selectedRows.size,
                        onClearSelection = { selectedRecentIds = emptySet() },
                        onDelete = {
                            callLogViewModel.deleteCallLogsByIds(selectedRows.flatMap { it.call.ids }.distinct())
                            selectedRecentIds = emptySet()
                        },
                        onBlock = {
                            selectedRows.forEach { row ->
                                dev.goodwy.rphone.controller.util.BlockedNumbersManager.block(context, row.call.number)
                            }
                            selectedRecentIds = emptySet()
                        },
                        onDeselect = { selectedRecentIds = emptySet() },
                        onSelectAll = { selectedRecentIds = recentRows.mapTo(mutableSetOf()) { it.call.id } },
                        isAllSelected = selectedRows.size == recentRows.size,
                    )
                }
                selectedContactIds.isNotEmpty() -> {
                    val selectedContacts = allContacts.filter { it.id in selectedContactIds }
                    BatchActionBar(
                        selectedCount = selectedContacts.size,
                        onClear = { selectedContactIds = emptySet() },
                        onDelete = {
                            contactsViewModel.deleteContacts(selectedContactIds.toList())
                            selectedContactIds = emptySet()
                        },
                        availableAccounts = emptyList(),
                        onShare = {
                            shareContactsAsVCard(context, selectedContacts, shareLabel)
                        },
                        onAddToFav = {
                            selectedContacts.forEach { contactsViewModel.toggleFavorite(it, true) }
                            selectedContactIds = emptySet()
                        },
                        onRemoveFromFav = {
                            selectedContacts.filter(Contact::isFavorite).forEach(contactsViewModel::toggleFavorite)
                            selectedContactIds = emptySet()
                        },
                        onDeselect = { selectedContactIds = emptySet() },
                        onSelectAll = { selectedContactIds = contacts.mapTo(mutableSetOf(), Contact::id) },
                        isAllSelected = selectedContacts.size == contacts.size,
                    )
                }
                browseContacts -> Spacer(Modifier.statusBarsPadding())
                else -> UnifiedHomeTopBar(
                    query = query,
                    onQueryChange = { query = it },
                    filterActive = selectedCallFilter != CallLogFilter.All,
                    onFilter = {
                        showCallFilter = true
                    },
                    browseContacts = browseContacts,
                    onExitBrowseContacts = {
                        browseContacts = false
                        query = ""
                        keyboard?.hide()
                    },
                    onSettings = { navigator.navigate(SettingsScreenDestination) },
                    onSearchBoundsChanged = { tourTargets[HomeTourTarget.Search] = it },
                )
            }
        },
        floatingActionButton = {
            DialpadFloatingActionButton(
                onClick = {
                    query = ""
                    keyboard?.hide()
                    showDialpad = true
                    statistics.record(io.github.bootika.dotdialer.observability.ProductStatistics.Event.Dialpad)
                },
                modifier = Modifier.homeTourBounds {
                    tourTargets[HomeTourTarget.Dialpad] = it
                },
            )
        },
    ) { padding ->
        if (actionableNumber == null && contacts.isEmpty() && callLogs.isEmpty() &&
            (contactsLoading || callLogsLoading)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (browseContacts) {
            CompositionLocalProvider(LocalRillAvatarAppearance provides avatarAppearance) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing, onRefresh = ::refreshHome,
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    dev.goodwy.rphone.view.components.ContactDirectoryContent(
                        contacts = contacts, query = query, displayOrder = displayOrder,
                        navigator = navigator, listState = listState,
                        headerContent = {
                            dev.goodwy.rphone.view.components.ContactDirectoryHeader(
                                query = query, onQueryChange = { query = it },
                            )
                        },
                    )
                }
            }
        } else {
            CompositionLocalProvider(LocalRillAvatarAppearance provides avatarAppearance) {
                ScrollHapticsEffect(listState)
                dev.goodwy.rphone.view.components.HideKeyboardOnListDrag(listState)
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = ::refreshHome,
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = if (actionableNumber == null) 12.dp else 94.dp,
                            bottom = 112.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                if (showFavorites && query.isBlank() && !browseContacts) {
                    item(key = "favorites-header") {
                        FavoritesHeader(
                            editing = editFavorites,
                            hasFavorites = favorites.isNotEmpty(),
                            onAdd = { showFavoritePicker = true },
                            onEdit = { editFavorites = !editFavorites },
                            modifier = Modifier.homeTourBounds {
                                tourFavoritesHeader = it
                                tourFavoritesRow?.let { row -> tourTargets[HomeTourTarget.Favorites] = homeTourFavoritesBounds(it, row) }
                            },
                        )
                    }
                    item(key = "favorites-row") {
                        IPhoneFavoritesRow(
                            favorites = favorites,
                            isEditing = editFavorites,
                            onAddFirst = { showFavoritePicker = true },
                            onUnfavorite = { contactsViewModel.toggleFavorite(it) },
                            onSaveOrder = prefs::setFavoritesOrder,
                            onClick = { contact ->
                                statistics.record(io.github.bootika.dotdialer.observability.ProductStatistics.Event.Favorite)
                                favoriteCallNumber(contact, prefs.getFavoriteNumber(contact.id))?.let(::call)
                                    ?: navigator.navigate(
                                        ContactDetailsScreenDestination(contactId = contact.id),
                                    )
                            },
                            onLongClick = { contact ->
                                contextTarget = HomeContextTarget.Favorite(contact)
                            },
                            displayOrder = displayOrder,
                            avatarSize = (contactAvatarSize + FAVORITES_AVATAR_BOOST_DP).dp,
                            modifier = Modifier.homeTourBounds {
                                tourFavoritesRow = it
                                tourTargets[HomeTourTarget.Favorites] = homeTourFavoritesBounds(tourFavoritesHeader, it)
                            },
                        )
                    }
                }

                if (!browseContacts && filteredRecents.isNotEmpty()) {
                    item(key = "recents-title") {
                        HomeSectionHeader(
                            title = stringResource(R.string.recents),
                        )
                    }
                    itemsIndexed(
                        recentRows,
                        key = { _, item -> item.document.key },
                        contentType = { _, _ -> "recent" },
                    ) { index, row ->
                        val callEntry = row.call
                        val contact = row.contact
                        val toggleSelection = {
                            selectedRecentIds = if (callEntry.id in selectedRecentIds) {
                                selectedRecentIds - callEntry.id
                            } else selectedRecentIds + callEntry.id
                        }
                        HomeResultRow(
                            quickActionsOnTap = recentTapAction == dev.goodwy.rphone.controller.util.HOME_TAP_QUICK_ACTIONS && selectedRecentIds.isEmpty() && selectedContactIds.isEmpty() && callEntry.number.isNotBlank(),
                            onQuickActionsOpen = { prefs.requireFeature(io.github.bootika.dotdialer.core.license.PlanFeature.CALL_PRODUCTIVITY) },
                            quickActionsEnabled = prefs.getBoolean(dev.goodwy.rphone.controller.util.HOME_QUICK_ACTION_GESTURE, false) && selectedRecentIds.isEmpty() && selectedContactIds.isEmpty(),
                            onQuickAction = { action ->
                                if (prefs.requireFeature(io.github.bootika.dotdialer.core.license.PlanFeature.CALL_PRODUCTIVITY)) {
                                    runContactNumberAction(listOf(ContactNumberMenuAction.Call, ContactNumberMenuAction.Message,
                                        ContactNumberMenuAction.WhatsApp, ContactNumberMenuAction.CopyIdentity)[action], callEntry.number, row.title)
                                }
                            },
                            title = row.title,
                            subtitle = callEntry.number.takeUnless { it == row.title },
                            photoUri = callEntry.photoUri,
                            phoneNumber = callEntry.number,
                            avatarColorIndex = avatarColorPlan.recentIndices[index],
                            avatarColorSeed = row.contact?.displayName ?: row.title,
                            callVisual = row.visual,
                            callTime = row.formattedTime,
                            swipeEnabled = swipeEnabled && selectedRecentIds.isEmpty() && selectedContactIds.isEmpty(),
                            swipeRightAction = swipeRightAction,
                            swipeLeftAction = swipeLeftAction,
                            haptics = haptics,
                            hapticsStrength = hapticsStrength,
                            hapticsIntensity = hapticsIntensity,
                            onAction = { action ->
                                statistics.record(io.github.bootika.dotdialer.observability.ProductStatistics.Event.Swipe)
                                performHomeAction(
                                    action = action,
                                    context = context,
                                    number = callEntry.number,
                                    onDeleteEntry = { callLogViewModel.deleteCallLogsByIds(listOf(callEntry.id)) },
                                    onCall = ::call,
                                    onContact = {
                                        navigator.navigate(ContactDetailsScreenDestination(
                                            contactId = contact?.id ?: "null",
                                            phoneNumber = callEntry.number,
                                        ))
                                    },
                                )
                            },
                            onAvatarClick = {
                                if (selectedRecentIds.isNotEmpty()) toggleSelection()
                                else if (contact != null) {
                                    navigator.navigate(
                                        ContactDetailsScreenDestination(
                                            contactId = contact.id,
                                            phoneNumber = callEntry.number,
                                        ),
                                    )
                                } else {
                                    navigator.navigateToNewContact(callEntry.number, avatarColorPlan.recentIndices[index])
                                }
                            },
                            onAvatarLongClick = toggleSelection,
                            onLongClick = {
                                contextTarget = HomeContextTarget.Recent(callEntry, contact)
                            },
                            onClick = {
                                if (selectedRecentIds.isNotEmpty()) toggleSelection()
                                else performHomeAction(
                                    action = recentTapAction,
                                    context = context,
                                    number = callEntry.number,
                                    onCall = ::call,
                                    onContact = {
                                        navigator.navigate(ContactDetailsScreenDestination(
                                            contactId = contact?.id ?: "null",
                                            phoneNumber = callEntry.number,
                                        ))
                                    },
                                )
                            },
                            selected = callEntry.id in selectedRecentIds,
                            isLastInSection = index == recentRows.lastIndex,
                            contactNameSize = contactNameSize,
                            expandedListDetails = expandedListDetails,
                            avatarSize = contactAvatarSize,
                            onRowBoundsChanged = if (index == 0) {
                                { tourTargets[HomeTourTarget.RecentRow] = it }
                            } else null,
                            onAvatarBoundsChanged = if (index == 0) {
                                { tourTargets[HomeTourTarget.RecentAvatar] = it }
                            } else null,
                        )
                    }
                    if (query.isBlank()) {
                        item(key = "view-all-contacts") {
                            HomeViewContactsButton(
                                onClick = {
                                    query = ""
                                    keyboard?.hide()
                                    showDialpad = false
                                    selectedRecentIds = emptySet()
                                    selectedContactIds = emptySet()
                                    browseContacts = true
                                    scope.launch { listState.scrollToItem(0) }
                                },
                            )
                        }
                    }
                }

                if (query.isNotBlank() && sections.contacts.isNotEmpty()) {
                    item(key = "contacts-title") {
                        Spacer(Modifier.height(14.dp))
                        HomeSectionHeader(title = stringResource(R.string.contacts))
                    }
                    itemsIndexed(
                        visibleContacts,
                        key = { _, item -> item.key },
                        contentType = { _, _ -> "contact" },
                    ) { index, document ->
                        val contact = (document.payload as DotDialerSearchPayload.ContactResult).contact
                        val phoneNumber = primaryNumber(contact)
                        HomeResultRow(
                            quickActionsOnTap = recentTapAction == dev.goodwy.rphone.controller.util.HOME_TAP_QUICK_ACTIONS && selectedRecentIds.isEmpty() && selectedContactIds.isEmpty() && phoneNumber != null,
                            onQuickActionsOpen = { prefs.requireFeature(io.github.bootika.dotdialer.core.license.PlanFeature.CALL_PRODUCTIVITY) },
                            quickActionsEnabled = prefs.getBoolean(dev.goodwy.rphone.controller.util.HOME_QUICK_ACTION_GESTURE, false) && selectedRecentIds.isEmpty() && selectedContactIds.isEmpty() && phoneNumber != null,
                            onQuickAction = { action ->
                                if (prefs.requireFeature(io.github.bootika.dotdialer.core.license.PlanFeature.CALL_PRODUCTIVITY))
                                    chooseContactNumber(contact, listOf(ContactNumberMenuAction.Call, ContactNumberMenuAction.Message,
                                        ContactNumberMenuAction.WhatsApp, ContactNumberMenuAction.CopyIdentity)[action])
                            },
                            title = getDisplayName(contact, displayOrder),
                            subtitle = getDisplayContactInfo(contact),
                            photoUri = canonicalContactPhotoUri(contact, contactsByPhone),
                            phoneNumber = phoneNumber,
                            avatarColorIndex = avatarColorPlan.contactIndices[index],
                            avatarColorSeed = contact.displayName,
                            swipeEnabled = swipeEnabled && selectedRecentIds.isEmpty() && selectedContactIds.isEmpty(),
                            swipeRightAction = dev.goodwy.rphone.controller.util.contactSwipeAction(swipeRightAction, phoneNumber != null),
                            swipeLeftAction = dev.goodwy.rphone.controller.util.contactSwipeAction(swipeLeftAction, phoneNumber != null),
                            haptics = haptics,
                            hapticsStrength = hapticsStrength,
                            hapticsIntensity = hapticsIntensity,
                            onAction = { action ->
                                statistics.record(io.github.bootika.dotdialer.observability.ProductStatistics.Event.Swipe)
                                if (action == PreferenceManager.SWIPE_ACTION_DELETE_ENTRY) deleteContactTarget = contact
                                else phoneNumber?.let { number ->
                                    performHomeAction(
                                        action = action,
                                        context = context,
                                        number = number,
                                        onCall = ::call,
                                        onContact = {
                                            navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                                        },
                                    )
                                }
                            },
                            onAvatarClick = {
                                navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                            },
                            onLongClick = {
                                contextTarget = HomeContextTarget.ContactTarget(contact)
                            },
                            onClick = {
                                if (selectedContactIds.isNotEmpty()) {
                                    selectedContactIds = if (contact.id in selectedContactIds) {
                                        selectedContactIds - contact.id
                                    } else {
                                        selectedContactIds + contact.id
                                    }
                                } else if (phoneNumber != null) performHomeAction(
                                    action = recentTapAction,
                                    context = context,
                                    number = phoneNumber,
                                    onCall = ::call,
                                    onContact = {
                                        navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                                    },
                                ) else if (recentTapAction != PreferenceManager.SWIPE_ACTION_NONE) {
                                    navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                                }
                            },
                            selected = contact.id in selectedContactIds,
                            isFirstInSection = false,
                            isLastInSection = index == visibleContacts.lastIndex,
                            contactNameSize = contactNameSize,
                            expandedListDetails = expandedListDetails,
                            avatarSize = contactAvatarSize,
                        )
                    }
                }

                val hasVisibleRecents = !browseContacts && filteredRecents.isNotEmpty()
                val hasVisibleContacts = query.isNotBlank() && sections.contacts.isNotEmpty()
                if (!hasVisibleRecents && !hasVisibleContacts && actionableNumber == null) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
                        }
                    actionableNumber?.let { number ->
                        PhoneSearchQuickActions(
                            onCall = { call(number) },
                            onMessage = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_SENDTO,
                                        android.net.Uri.fromParts("smsto", number, null),
                                    ),
                                )
                            },
                            onAddToContacts = {
                                keyboard?.hide()
                                navigator.navigateToNewContact(number, null)
                            },
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                    }
                }
            }
        }
    }
    GooglePhotoOffer(navigator, blocked = showHomeTour || !prefs.getBoolean(PreferenceManager.KEY_HOME_UI_TOUR_SHOWN, false))
    if (showHomeTour) {
        HomeCoachMarkTour(targets = tourTargets.toMap()) {
            prefs.setBoolean(PreferenceManager.KEY_HOME_UI_TOUR_SHOWN, true)
            showHomeTour = false
        }
    }
}

@Composable
internal fun UnifiedHomeTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    filterActive: Boolean,
    onFilter: () -> Unit,
    browseContacts: Boolean,
    onExitBrowseContacts: () -> Unit,
    onSettings: () -> Unit,
    onSearchBoundsChanged: (Rect) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .homeTourBounds(onSearchBoundsChanged),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxSize(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (browseContacts) {
                                IconButton(
                                    onClick = onExitBrowseContacts,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            } else {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(
                                            if (browseContacts) R.string.search_contacts
                                            else R.string.search_contacts_and_calls,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                innerTextField()
                            }
                            AnimatedVisibility(query.isNotEmpty()) {
                                IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                                }
                            }
                            dev.goodwy.rphone.view.components.VoiceSearchButton(onResult = onQueryChange)
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                IconButton(onClick = onFilter, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Default.FilterList,
                                        contentDescription = stringResource(R.string.filter_results),
                                    )
                                }
                                if (filterActive) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 3.dp).size(7.dp),
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.primary,
                                    ) {}
                                }
                            }
                        }
                    },
                )
            }
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(17.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeResultRow(
    title: String,
    subtitle: String?,
    photoUri: String?,
    phoneNumber: String?,
    avatarColorIndex: Int? = null,
    avatarColorSeed: String? = null,
    callVisual: dev.goodwy.rphone.view.components.CallLogVisual? = null,
    callTime: String? = null,
    swipeEnabled: Boolean,
    swipeRightAction: Int,
    swipeLeftAction: Int,
    haptics: Boolean,
    hapticsStrength: String,
    hapticsIntensity: Float,
    onAction: (Int) -> Unit,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    isFirstInSection: Boolean = false,
    isLastInSection: Boolean,
    contactNameSize: Int,
    avatarSize: Int,
    expandedListDetails: Boolean = PreferenceManager.DEFAULT_EXPANDED_LIST_DETAILS,
    onRowBoundsChanged: ((Rect) -> Unit)? = null,
    onAvatarBoundsChanged: ((Rect) -> Unit)? = null,
    quickActionsOnTap: Boolean = false,
    onQuickActionsOpen: () -> Boolean = { true },
    quickActionsEnabled: Boolean = false,
    onQuickAction: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val quickGesture = remember(phoneNumber, title) { HomeQuickGestureState() }
    LaunchedEffect(quickActionsEnabled, quickActionsOnTap) { if (!quickActionsEnabled && !quickActionsOnTap) quickGesture.expanded = false }
    val quickAction by rememberUpdatedState(onQuickAction)
    val rowTextColor = if (callVisual?.accentMetadata == true) {
        callStatusColor(callVisual.status)
    } else {
        null
    }
    SwipeableHomeContainer(
        enabled = swipeEnabled,
        swipeRightAction = swipeRightAction,
        swipeLeftAction = swipeLeftAction,
        haptics = haptics,
        hapticsStrength = hapticsStrength,
        hapticsIntensity = hapticsIntensity,
        onAction = onAction,
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .then(
                if (onRowBoundsChanged == null) Modifier else Modifier.homeTourBounds(onRowBoundsChanged),
            ),
    ) {
        Surface(
            color = if (selected) callLogSelectionColor else MaterialTheme.colorScheme.surfaceBright,
            shape = when {
                quickGesture.expanded -> if (isFirstInSection) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(0.dp)
                isFirstInSection && isLastInSection -> RoundedCornerShape(22.dp)
                isFirstInSection -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                isLastInSection -> RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                else -> RoundedCornerShape(0.dp)
            },
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .homeCombinedClickable(
                            context, haptics, hapticsStrength, hapticsIntensity, if (quickActionsOnTap) ({ quickGesture.toggleForTap(onQuickActionsOpen) }) else onClick, if (quickActionsEnabled) null else onLongClick,
                        )
                        .then(if (quickActionsEnabled) Modifier.quickActionGesture(quickGesture) { quickAction(it) } else Modifier)
                        .padding(
                            horizontal = 14.dp,
                            vertical = HOME_RESULT_VERTICAL_PADDING_DP.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeSelectableAvatar(
                        title = title,
                        photoUri = photoUri,
                        phoneNumber = phoneNumber,
                        avatarColorIndex = avatarColorIndex,
                        colorSeed = avatarColorSeed,
                        avatarSize = avatarSize,
                        selected = selected,
                        haptics = haptics,
                        hapticsStrength = hapticsStrength,
                        hapticsIntensity = hapticsIntensity,
                        onClick = onAvatarClick,
                        onLongClick = onAvatarLongClick ?: onAvatarClick,
                        modifier = Modifier
                            .size(avatarSize.dp)
                            .then(
                                if (onAvatarBoundsChanged == null) Modifier else Modifier.homeTourBounds(onAvatarBoundsChanged),
                            ),
                    )
                    HomeResultText(
                        title = title,
                        subtitle = subtitle,
                        callVisual = callVisual,
                        callTime = callTime,
                        contactNameSize = contactNameSize,
                        metadataColor = rowTextColor,
                        expandedDetails = expandedListDetails,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
                HomeQuickActionTray(quickGesture) { quickAction(it) }
                if (!isLastInSection) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp, end = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}
