package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.goodwy.rphone.R

private const val CALL_BACK_CHANNEL_ID = "call_back_reminders"
private const val EXTRA_PHONE_NUMBER = "phone_number"
private const val EXTRA_DISPLAY_NAME = "display_name"

object CallBackReminderScheduler {
    fun schedule(
        context: Context,
        phoneNumber: String,
        displayName: String,
        triggerAtMillis: Long,
    ): Boolean {
        if (phoneNumber.isBlank() || triggerAtMillis <= System.currentTimeMillis()) return false
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        return try {
            val requestCode = reminderId(phoneNumber, triggerAtMillis)
            val intent = Intent(context, CallBackReminderReceiver::class.java).apply {
                data = "dotdialer://call-back/$requestCode".toUri()
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
            true
        } catch (error: Exception) {
            Log.e("CallBackReminder", "Unable to schedule the call-back reminder", error)
            false
        }
    }

    internal fun reminderId(phoneNumber: String, triggerAtMillis: Long): Int =
        (31 * phoneNumber.hashCode() + triggerAtMillis.hashCode()) and Int.MAX_VALUE
}

class CallBackReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
        if (phoneNumber.isBlank()) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty().ifBlank { phoneNumber }
        ensureChannel(context)

        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phoneNumber, null)).apply {
                    setClass(context, dev.goodwy.rphone.MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            phoneNumber.hashCode() and Int.MAX_VALUE,
            dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CALL_BACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle(context.getString(R.string.call_back_notification_title, displayName))
            .setContentText(context.getString(R.string.call_back_notification_text, phoneNumber))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context)
            .notify(phoneNumber.hashCode() and Int.MAX_VALUE, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CALL_BACK_CHANNEL_ID,
                context.getString(R.string.call_back_reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.call_back_reminder_channel_description)
            },
        )
    }
}
