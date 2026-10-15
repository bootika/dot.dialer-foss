package dev.goodwy.rphone.view.screen

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallBackReminderScheduler
import java.util.Date

internal fun openQuickResponseComposer(
    context: Context,
    phoneNumber: String,
    message: String,
) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, "smsto:$phoneNumber".toUri()).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (error: Exception) {
        Log.w("CallScreen", "Unable to open the quick-response message composer", error)
    }
}

internal fun scheduleCallBackReminderAndNotify(
    context: Context,
    phoneNumber: String,
    displayName: String,
    triggerAtMillis: Long,
): Boolean {
    val scheduled = CallBackReminderScheduler.schedule(
        context = context,
        phoneNumber = phoneNumber,
        displayName = displayName,
        triggerAtMillis = triggerAtMillis,
    )
    if (!scheduled) {
        Toast.makeText(context, R.string.call_back_reminder_failed, Toast.LENGTH_LONG).show()
        return false
    }
    val formattedTime = DateFormat.getDateFormat(context).format(Date(triggerAtMillis)) +
        " " + DateFormat.getTimeFormat(context).format(Date(triggerAtMillis))
    Toast.makeText(
        context,
        context.getString(R.string.call_back_reminder_scheduled, formattedTime),
        Toast.LENGTH_SHORT,
    ).show()
    return true
}
