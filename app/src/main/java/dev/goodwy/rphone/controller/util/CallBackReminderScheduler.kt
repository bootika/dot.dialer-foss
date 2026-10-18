package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import org.json.JSONObject
import java.security.MessageDigest

private const val CHANNEL = "call_back_reminders"
internal data class SavedCallBack(val number: String, val name: String, val due: Long, val token: String)

/** Durable, replace-by-number, inexact callbacks. Notification taps only open the dialer. */
object CallBackReminderScheduler {
    private fun storage(context: Context) = context.getSharedPreferences("call_back_reminders_v2", Context.MODE_PRIVATE)
    /** KTX edit returns Unit; scheduling/cancellation must inspect commit's durable result. */
    @android.annotation.SuppressLint("UseKtx")
    private fun persist(context: Context, id: String, value: String?): Boolean {
        val preferences = storage(context)
        val editor = preferences.edit()
        if (value == null) editor.remove(id) else editor.putString(id, value)
        return editor.commit()
    }
    private fun key(number: String) = MessageDigest.getInstance("SHA-256")
        .digest(normalizePhoneNumber(number).toByteArray()).joinToString("") { "%02x".format(it) }
    internal fun pending(context: Context, number: String): SavedCallBack? = decode(storage(context).getString(key(number), null))
    private fun decode(value: String?): SavedCallBack? = runCatching {
        val json = JSONObject(requireNotNull(value))
        SavedCallBack(json.getString("number"), json.getString("name"), json.getLong("due"), json.getString("token"))
    }.getOrNull()
    private fun encode(value: SavedCallBack) = JSONObject().put("number", value.number).put("name", value.name)
        .put("due", value.due).put("token", value.token).toString()

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(
            CHANNEL, context.getString(R.string.call_back_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.call_back_reminder_channel_description)
        })
    }
    internal fun notificationsAvailable(context: Context): Boolean {
        ensureChannel(context)
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun operation(context: Context, value: SavedCallBack) = PendingIntent.getBroadcast(context, 0,
        Intent(context, CallBackReminderReceiver::class.java).setData("dotdialer://call-back/${key(value.number)}".toUri())
            .putExtra("number", value.number).putExtra("token", value.token),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun arm(context: Context, value: SavedCallBack) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            maxOf(value.due, System.currentTimeMillis() + 1000), operation(context, value))
    }

    @Synchronized
    fun schedule(context: Context, phoneNumber: String, displayName: String, triggerAtMillis: Long): Boolean {
        if (normalizePhoneNumber(phoneNumber).isBlank() || triggerAtMillis <= System.currentTimeMillis() || !notificationsAvailable(context)) return false
        val prefs = storage(context)
        val id = key(phoneNumber)
        val old = prefs.getString(id, null)
        val value = SavedCallBack(phoneNumber, displayName.take(200), triggerAtMillis, java.util.UUID.randomUUID().toString())
        if (!persist(context, id, encode(value))) return false
        return try {
            arm(context, value)
            NotificationManagerCompat.from(context).cancel(id, 0)
            true
        } catch (error: Exception) {
            if (!persist(context, id, old)) android.util.Log.w("CallBackReminder", "Unable to persist rollback")
            decode(old)?.let { runCatching { arm(context, it) } }
            android.util.Log.w("CallBackReminder", "Scheduling failed", error)
            false
        }
    }

    @Synchronized
    fun cancel(context: Context, number: String): Boolean {
        val value = pending(context, number) ?: return false
        if (!persist(context, key(number), null)) return false
        context.getSystemService(AlarmManager::class.java).cancel(operation(context, value))
        NotificationManagerCompat.from(context).cancel(key(number), 0)
        return true
    }

    @Synchronized
    internal fun deliverLegacy(context: Context, intent: Intent) {
        val number = intent.getStringExtra("phone_number").orEmpty()
        if (number.isBlank() || intent.data?.host != "call-back" || pending(context, number) != null) return
        val value = SavedCallBack(number, intent.getStringExtra("display_name").orEmpty(), System.currentTimeMillis(), "legacy")
        if (persist(context, key(number), encode(value))) deliver(context, number, value.token)
    }

    @Synchronized
    internal fun recover(context: Context) {
        storage(context).all.values.filterIsInstance<String>().mapNotNull(::decode).forEach { value ->
            runCatching { arm(context, value) }.onFailure { android.util.Log.w("CallBackReminder", "Recovery failed", it) }
        }
    }

    @Synchronized
    internal fun deliver(context: Context, number: String, token: String, nowMillis: Long = System.currentTimeMillis()) {
        val value = pending(context, number) ?: return
        if (value.token != token || value.due > nowMillis || !notificationsAvailable(context)) return
        val dial = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", value.number, null))
            .setClass(context, dev.goodwy.rphone.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(context, key(number).hashCode(), dial, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_phone)
            .setContentTitle(context.getString(R.string.call_back_notification_title, value.name.ifBlank { value.number }))
            .setContentText(context.getString(R.string.call_back_notification_text, value.number))
            .setContentIntent(tap).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        try {
            NotificationManagerCompat.from(context).notify(key(number), 0, notification)
            if (!persist(context, key(number), null)) android.util.Log.w("CallBackReminder", "Unable to acknowledge notification")
        } catch (error: SecurityException) { android.util.Log.w("CallBackReminder", "Notifications unavailable", error) }
    }

    internal fun reminderId(phoneNumber: String, triggerAtMillis: Long): Int =
        (31 * phoneNumber.hashCode() + triggerAtMillis.hashCode()) and Int.MAX_VALUE
}

class CallBackReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            CallBackReminderScheduler.recover(context)
        } else if (intent.hasExtra("phone_number")) {
            CallBackReminderScheduler.deliverLegacy(context, intent)
        } else {
            CallBackReminderScheduler.deliver(context, intent.getStringExtra("number").orEmpty(), intent.getStringExtra("token").orEmpty())
        }
    }
}
