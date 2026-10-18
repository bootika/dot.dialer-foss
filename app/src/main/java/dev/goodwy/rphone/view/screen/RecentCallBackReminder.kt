package dev.goodwy.rphone.view.screen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallBackReminderScheduler
import dev.goodwy.rphone.controller.util.PreferenceManager
import io.github.bootika.dotdialer.core.license.PlanFeature
import java.util.Calendar

@Composable
internal fun RecentCallBackReminder(number: String, name: String, prefs: PreferenceManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var changing by remember { mutableStateOf(false) }
    val existing = remember(number) { CallBackReminderScheduler.pending(context, number) }
    fun schedule(time: Long) {
        if (time <= System.currentTimeMillis()) {
            android.widget.Toast.makeText(context, R.string.reminder_future_time, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (prefs.requireFeature(PlanFeature.CALL_PRODUCTIVITY) && scheduleCallBackReminderAndNotify(context, number, name, time)) onDismiss()
    }
    if (existing != null && !changing) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.call_back_reminder)) },
            text = { Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(existing.due))) },
            confirmButton = { TextButton(onClick = { if (prefs.requireFeature(PlanFeature.CALL_PRODUCTIVITY)) changing = true }) { Text(stringResource(R.string.edit)) } },
            dismissButton = { TextButton(onClick = { if (CallBackReminderScheduler.cancel(context, number) || CallBackReminderScheduler.pending(context, number) == null) onDismiss()
                else android.widget.Toast.makeText(context, R.string.reminder_update_failed, android.widget.Toast.LENGTH_LONG).show() }) { Text(stringResource(R.string.cancel_reminder)) } })
    } else CallBackReminderBottomSheet(onDismiss, ::schedule, {
        val now = Calendar.getInstance()
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                val selected = Calendar.getInstance().apply { set(year, month, day, hour, minute, 0); set(Calendar.MILLISECOND, 0) }
                schedule(selected.timeInMillis)
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    })
}
