package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.view.theme.color_call_button
import io.github.bootika.dotdialer.core.call.CallHistoryWindowPolicy

internal data class ContactHistoryPagingState(
    val completeLogs: List<CallLogEntry>,
    val isContactHistory: Boolean,
    val visibleDays: Int,
    val showMore: () -> Unit,
)

@Composable
internal fun rememberContactHistoryPaging(
    viewModel: CallLogViewModel,
    currentLogs: List<CallLogEntry>,
    contactId: String?,
    phoneNumber: String?,
    numbersList: Array<String>?,
): ContactHistoryPagingState {
    val isContactHistory = contactId != null || phoneNumber != null || numbersList != null
    val historyKey = numbersList?.contentHashCode()
    var visibleDays by rememberSaveable(contactId, phoneNumber, historyKey) {
        mutableIntStateOf(CallHistoryWindowPolicy.INITIAL_DAYS)
    }
    val completeLogs by produceState(
        initialValue = currentLogs,
        isContactHistory,
        contactId,
        phoneNumber,
        historyKey,
        currentLogs,
    ) {
        value = if (isContactHistory) viewModel.loadCompleteCallHistory() else currentLogs
    }
    return ContactHistoryPagingState(
        completeLogs = completeLogs,
        isContactHistory = isContactHistory,
        visibleDays = visibleDays,
        showMore = { visibleDays = CallHistoryWindowPolicy.expand(visibleDays) },
    )
}

@Composable
internal fun ShowMoreCallHistoryButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
            .heightIn(min = 58.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = color_call_button,
            contentColor = androidx.compose.ui.graphics.Color.White,
        ),
    ) {
        Text(
            text = stringResource(R.string.show_more),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}
