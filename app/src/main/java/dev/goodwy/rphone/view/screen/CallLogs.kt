package dev.goodwy.rphone.view.screen

import android.content.Intent
import android.provider.CallLog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.util.formatDateHeader
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.controller.util.placeCallWithSimPreference
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.formatPhoneNumber
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.CallLogFilter
import dev.goodwy.rphone.view.components.*
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.util.BlockedNumbersManager
import dev.goodwy.rphone.controller.util.forceLtr
import dev.goodwy.rphone.controller.util.hasDualSim
import kotlinx.coroutines.launch
import io.github.bootika.dotdialer.core.call.CallHistoryWindow
import io.github.bootika.dotdialer.core.call.CallHistoryWindowPolicy
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.koinInject

@Destination<RootGraph>
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogFullScreen(
    navigator: DestinationsNavigator,
    contactId: String? = null,
    phoneNumber: String? = null,
    numbersList: Array<String>? = null
) {
    val viewModel: CallLogViewModel = koinActivityViewModel()
    val allLogs by viewModel.allCallLogs.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val historyPaging = rememberContactHistoryPaging(viewModel, allLogs, contactId, phoneNumber, numbersList)

    AcknowledgeMissedCallsEffect(viewModel)

    var selectedEntries by remember { mutableStateOf(setOf<CallLogEntry>()) }

    BackHandler(enabled = selectedEntries.isNotEmpty()) {
        selectedEntries = emptySet()
    }

    val prefs = koinInject<PreferenceManager>()
    val simPref = remember { prefs.getInt("default_sim", 0) }

    var showSimPicker by remember { mutableStateOf(false) }
    var showNumberPicker by remember { mutableStateOf(false) }
    var showMessagePicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf<String?>(null) }

    // Entrance animation
    var screenVisible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (screenVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "logScreenAlpha"
    )
    val screenScale by animateFloatAsState(
        targetValue = if (screenVisible) 1f else 0.96f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "logScreenScale"
    )
    LaunchedEffect(Unit) { screenVisible = true }

    val filteredLogsByContact = remember(historyPaging.completeLogs, contactId, phoneNumber, numbersList) {
        if (contactId == null && phoneNumber == null && numbersList == null) {
            historyPaging.completeLogs
        } else {
            historyPaging.completeLogs.filter { log ->
                val logNumber = log.number.replace(" ", "")

                val matchesContactId = contactId != null && contactId != "null" && log.contactId == contactId
                val matchesPhoneNumber = phoneNumber != null && logNumber == phoneNumber.replace(" ", "")
                val matchesNumbersList = numbersList != null && numbersList.any {
                    logNumber == it.replace(" ", "")
                }

                matchesContactId || matchesPhoneNumber || matchesNumbersList
            }
        }
    }

    val contactName = remember(filteredLogsByContact) {
        filteredLogsByContact.firstOrNull { it.name != null && it.name != it.number }?.name ?: (phoneNumber?.forceLtr())
    }

    val logsForSelectedFilter = remember(filteredLogsByContact, selectedFilter) {
        when (selectedFilter) {
            CallLogFilter.All -> filteredLogsByContact
            CallLogFilter.Missed -> filteredLogsByContact.filter { it.type == CallLog.Calls.MISSED_TYPE }
            CallLogFilter.Incoming -> filteredLogsByContact.filter { it.type == CallLog.Calls.INCOMING_TYPE }
            CallLogFilter.Outgoing -> filteredLogsByContact.filter { it.type == CallLog.Calls.OUTGOING_TYPE }
            CallLogFilter.Rejected -> filteredLogsByContact.filter { it.type == CallLog.Calls.REJECTED_TYPE }
            CallLogFilter.Contacts -> filteredLogsByContact.filter { it.contactId != null }
        }
    }
    val historyNow = remember(contactId, phoneNumber, numbersList?.contentHashCode()) { System.currentTimeMillis() }
    val historyWindow = remember(logsForSelectedFilter, historyPaging.visibleDays, historyNow) {
        if (historyPaging.isContactHistory) {
            CallHistoryWindowPolicy.apply(
                entries = logsForSelectedFilter,
                visibleDays = historyPaging.visibleDays,
                nowMillis = historyNow,
                dateMillis = CallLogEntry::date,
            )
        } else {
            CallHistoryWindow(logsForSelectedFilter, false)
        }
    }
    val finalFilteredLogs = historyWindow.entries

    if (showSimPicker && pendingNumber != null) {
        SimPickerDialog(
            onDismissRequest = { showSimPicker = false },
            onSimSelected = { handle ->
                pendingNumber?.let { makeCall(context, it, handle) }
                showSimPicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = selectedEntries.isNotEmpty(),
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                },
                label = "TopBarTransition"
            ) { isSelecting ->
                if (!isSelecting) {
                    Column {
                        TopAppBar(
                            title = {
                                val name =
                                    if ((contactId == null || contactId == "null") && numbersList != null) numbersList.joinToString().forceLtr()
                                    else contactName
                                Text(
                                    text = if (name != null) stringResource(R.string.history_with, name)
                                            else stringResource(R.string.call_history),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                NavigationIcon(onClick = { navigator.navigateUp() })
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val filters = CallLogFilter.entries - CallLogFilter.Contacts
                            items(filters) { filter ->
                                RillFilterChip(stringResource(filter.stringRes), selectedFilter == filter, { _ ->
                                    viewModel.setFilter(filter)
                                })
                            }
                        }
                    }
                } else {
                    BatchCallLogActionBar(
                        selectedCount = selectedEntries.size,
                        onClearSelection = { selectedEntries = emptySet() },
                        onDelete = {
                            val allIdsToDelete = selectedEntries.flatMap { it.ids }
                            viewModel.deleteCallLogsByIds(allIdsToDelete)
                            selectedEntries = emptySet()
                        },
                        onBlock = {
                            selectedEntries.forEach { entry ->
                                BlockedNumbersManager.block(context, entry.number)
                            }
                            selectedEntries = emptySet()
                        },
                        onDeselect = {
                            selectedEntries = emptySet()
                        },
                        onSelectAll = {
                            selectedEntries = finalFilteredLogs.toSet()
                        },
                        isAllSelected = selectedEntries == finalFilteredLogs.toSet()
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha)
            .scale(screenScale)) {
            val filteredNumbersList = filteredLogsByContact.distinctBy { it.number }
            val hasMultipleNumbers = filteredNumbersList.size > 1 || (numbersList != null && numbersList.size > 1)
            Box(
                modifier = Modifier
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        start = 0.dp,
                        end = 0.dp,
                        bottom = 0.dp
                    )
                    .fillMaxSize()) {
                if (filteredLogsByContact.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_call_history_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val showSimLabel = hasDualSim(context)
                    val groupedLogs = remember(finalFilteredLogs) {
                        finalFilteredLogs.groupBy { context.formatDateHeader(it.date) }
                    }

                    ScrollHapticsEffect(listState = listState)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 168.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedLogs.forEach { (header, logsInGroup) ->
                            item(key = "group_$header", contentType = "logGroup") {
                                RillSectionHeader(
                                    title = header,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 8.dp))
                                RillExpressiveCard {
                                    logsInGroup.forEachIndexed { index, lg ->
                                        CallLogTileSimple(
                                            log = lg,
                                            onClick = {
                                                if (selectedEntries.isNotEmpty()) {
                                                    selectedEntries = if (selectedEntries.any { it.id == lg.id }) {
                                                        selectedEntries.filter { it.id != lg.id }.toSet()
                                                    } else {
                                                        selectedEntries + lg
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                if (selectedEntries.none { it.id == lg.id }) {
                                                    selectedEntries = selectedEntries + lg
                                                }
                                            },
                                            onCallClick = {
                                                placeCallWithSimPreference(context, lg.number, simPref) {
                                                    pendingNumber = lg.number
                                                    showSimPicker = true
                                                }
                                            },
                                            selected = selectedEntries.any { it.id == lg.id },
                                            showNumber = hasMultipleNumbers,
                                            showSimLabel = showSimLabel,
                                        )
                                    }
                                }
                            }
                        }
                        if (historyWindow.hasOlderEntries) {
                            item(key = "showMoreHistory", contentType = "historyAction") {
                                ShowMoreCallHistoryButton(historyPaging.showMore)
                            }
                        }
                    }
                }
            }

            // Bottom bar
            val hideBottomBar = contactId == null && phoneNumber == null
            if (!hideBottomBar) {
                val initiateCall = { number: String ->
                    placeCallWithSimPreference(context, number, simPref) {
                        pendingNumber = number; showSimPicker = true
                    }
                }

                val initiateMessage = { number: String ->
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "sms:$number".toUri()
                        )
                    )
                }
                ContactHistoryBottomActions(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth(),
                    onCall = {
                        if (phoneNumber != null) {
                            if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                performAppHaptic(
                                    context,
                                    prefs.getString(
                                        PreferenceManager.KEY_APP_HAPTICS_STRENGTH,
                                        "light"
                                    ) ?: "light",
                                    prefs.getFloat(
                                        PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY,
                                        0.5f
                                    )
                                )
                            }
                            if (hasMultipleNumbers) {
                                showNumberPicker = true
                            } else {
                                initiateCall(phoneNumber)
                            }
                        }
                    },
                    onMessage = {
                        if (phoneNumber != null) {
                            if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                performAppHaptic(
                                    context,
                                    prefs.getString(
                                        PreferenceManager.KEY_APP_HAPTICS_STRENGTH,
                                        "light"
                                    ) ?: "light",
                                    prefs.getFloat(
                                        PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY,
                                        0.5f
                                    )
                                )
                            }
                            if (hasMultipleNumbers) {
                                showMessagePicker = true
                            } else {
                                initiateMessage(phoneNumber)
                            }
                        }
                    },
                )

                if (showNumberPicker) {
                    NumberPickerDialog(
                        numbers = numbersList?.toList()
                            ?: filteredNumbersList.map { it.number },
                        onDismissRequest = { showNumberPicker = false },
                        onNumberSelected = {
                            showNumberPicker = false
                            initiateCall(it)
                        }
                    )
                }
                if (showMessagePicker) {
                    NumberPickerDialog(
                        numbers = numbersList?.toList()
                            ?: filteredNumbersList.map { it.number },
                        onDismissRequest = { showMessagePicker = false },
                        onNumberSelected = {
                            showMessagePicker = false
                            initiateMessage(it)
                        }
                    )
                }
            }

        }
    }
}
