package dev.goodwy.rphone.view.screen

import android.util.Log
import android.provider.CallLog
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.view.components.RillExpressiveCard
import io.github.bootika.dotdialer.core.license.PlanFeature
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

internal fun matchingCallHistory(logs: List<CallLogEntry>, numbers: List<String>, region: String?): List<CallLogEntry> {
    fun key(number: String) = normalizedContactNumber(number, region) ?: selectedPhoneNumber(number)
    val keys = numbers.mapNotNull(::key).toSet()
    return logs.filter { key(it.number)?.let(keys::contains) == true }.distinctBy { it.id }.sortedByDescending { it.date }
}

@Composable
internal fun CallNoteHistoryPanel(name: String, number: String, note: String, onNoteChange: (String) -> Unit,
    enabled: Boolean, region: String? = null, onSave: () -> Unit) {
    val prefs = koinInject<PreferenceManager>()
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val allowed = remember(version) { prefs.allowsFeature(PlanFeature.CALL_PRODUCTIVITY) }
    val windowHeightPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val panelHeight = with(androidx.compose.ui.platform.LocalDensity.current) { (windowHeightPx * .4f).toDp() }.coerceAtMost(320.dp)
    RillExpressiveCard {
        MoreItem(headline = name, leadingIcon = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.StickyNote2,
            trailingIcon = androidx.compose.material.icons.Icons.Default.Check, enabled = enabled, onClick = onSave)
        Surface(color = dev.goodwy.rphone.view.theme.MyColors.cardColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = panelHeight).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = note, onValueChange = onNoteChange, modifier = Modifier.fillMaxWidth().height(184.dp),
                    placeholder = { Text(stringResource(R.string.type_your_note)) }, minLines = 3,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent))
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.call_history), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            .alpha(if (allowed) 1f else .45f).clickable(enabled = !allowed) { prefs.requireFeature(PlanFeature.CALL_PRODUCTIVITY) })
                    if (allowed) CallNoteHistoryRows(number, region)
                }
            }
        }
    }
}

@Composable
private fun CallNoteHistoryRows(number: String, region: String?) {
    val context = LocalContext.current
    val logsRepository = koinInject<ICallLogRepository>()
    val contactsRepository = koinInject<IContactsRepository>()
    var rows by remember(number, region) { mutableStateOf<List<CallLogEntry>?>(null) }
    var failed by remember(number, region) { mutableStateOf(false) }
    LaunchedEffect(number, region) {
        try {
            rows = withContext(Dispatchers.IO) {
                val lookupRegion = region ?: resolveContactLookupRegion(context)
                val contact = contactsRepository.getContactByNumberInRegion(number, lookupRegion)
                matchingCallHistory(logsRepository.getCallLogs(), listOf(number) + contact?.phoneNumbers.orEmpty(), lookupRegion).take(5)
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: RuntimeException) {
            Log.w("CallNoteHistory", "History unavailable: ${failure.javaClass.simpleName}")
            failed = true; rows = emptyList()
        }
    }
    val loadedRows = rows
    when {
        loadedRows == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
        failed -> Text(stringResource(R.string.call_history_unavailable), style = MaterialTheme.typography.bodyMedium)
        loadedRows.isEmpty() -> Text(stringResource(R.string.no_call_history_found), style = MaterialTheme.typography.bodyMedium)
        else -> loadedRows.forEach { row ->
            ListItem(headlineContent = { Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(row.date))) },
                supportingContent = { Text(stringResource(when (row.type) {
                    CallLog.Calls.OUTGOING_TYPE -> R.string.outgoing
                    CallLog.Calls.MISSED_TYPE -> R.string.missed
                    CallLog.Calls.REJECTED_TYPE -> R.string.rejected
                    else -> R.string.incoming
                })) }, trailingContent = { Text("${row.duration / 60}:${(row.duration % 60).toString().padStart(2, '0')}") })
        }
    }
}
