package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.view.theme.accentText

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.BlockedNumbersManager
import java.util.Calendar

private const val TRANSIENT_DIALOG_LOG_TAG = "CallScreen"

@Composable
internal fun IncomingCallTransientDialogs(
    showReminderSheet: Boolean,
    showUnknownCallerActions: Boolean,
    showBlockAndReportConfirmation: Boolean,
    phoneNumber: String,
    call: Call,
    context: Context,
    onDismissReminder: () -> Unit,
    onDismissUnknownActions: () -> Unit,
    onShowBlockConfirmation: () -> Unit,
    onDismissBlockConfirmation: () -> Unit,
    onScheduleReminder: (Long) -> Unit,
    onReject: () -> Unit,
    onDisconnect: () -> Unit,
) {
    if (showReminderSheet) {
        CallBackReminderBottomSheet(
            onDismiss = onDismissReminder,
            onSchedule = onScheduleReminder,
            onChooseTime = {
                onDismissReminder()
                val now = Calendar.getInstance()
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val selected = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        onScheduleReminder(selected.timeInMillis)
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(context),
                ).show()
            },
        )
    }

    if (showUnknownCallerActions) {
        UnknownCallerActionsBottomSheet(
            onDismiss = onDismissUnknownActions,
            onAddToContacts = {
                onDismissUnknownActions()
                onReject()
                try {
                    context.startActivity(
                        Intent(ContactsContract.Intents.Insert.ACTION).apply {
                            type = ContactsContract.Contacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                        },
                    )
                } catch (error: Exception) {
                    Log.w(TRANSIENT_DIALOG_LOG_TAG, "Unable to open the new-contact screen", error)
                }
            },
            onBlockAndReport = onShowBlockConfirmation,
        )
    }

    if (showBlockAndReportConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissBlockConfirmation,
            icon = { Icon(Icons.Rounded.Report, contentDescription = null) },
            title = { Text(stringResource(R.string.block_and_report_spam_title)) },
            text = { Text(stringResource(R.string.block_and_report_spam_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismissBlockConfirmation()
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                call.reject(Call.REJECT_REASON_UNWANTED)
                            } else {
                                call.disconnect()
                            }
                        } catch (error: Exception) {
                            Log.w(TRANSIENT_DIALOG_LOG_TAG, "Telecom could not reject the reported call", error)
                            onDisconnect()
                        }
                        val blocked = BlockedNumbersManager.canBlockNumbers(context) &&
                            BlockedNumbersManager.block(context, phoneNumber)
                        Toast.makeText(
                            context,
                            if (blocked) R.string.number_blocked else R.string.number_could_not_be_blocked,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)
                ) {
                    Text(
                        stringResource(R.string.block_and_report),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissBlockConfirmation, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
