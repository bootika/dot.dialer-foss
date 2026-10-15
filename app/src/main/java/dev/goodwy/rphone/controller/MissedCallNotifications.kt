package dev.goodwy.rphone.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.telecom.Call
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import dev.goodwy.rphone.MainActivity
import dev.goodwy.rphone.R
import java.util.WeakHashMap
import java.util.UUID

/** Serialized, persistent count; posting a system notification is asynchronous on real devices. */
internal class MissedCallNotifications(
    private val context: Context,
    private val callLogSnapshot: () -> MissedCallLogSnapshot? = { readMissedCallLog(context) },
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    companion object {
        internal const val TAG = "missed-calls"
        internal const val ID = 102
        internal const val EXTRA_EVENT = "missed_call_event"
        private const val EXTRA_NAMES = "missed_call_names"
        private const val MAX_NAMES = 8
        private val lock = Any()
        private var generation = 0L
        private val publishedCalls = WeakHashMap<Call, Boolean>()

        fun dismissSingle(context: Context, event: String) = synchronized(lock) {
            val state = MissedCallBadgeState.read(context)
            if (state?.count == 1 && state.event == event) dismiss(context, event)
        }

        fun dismiss(context: Context, event: String) = synchronized(lock) {
            if (MissedCallBadgeState.read(context)?.event == event) {
                generation++
                MissedCallBadgeState.clear(context)
                context.getSystemService(NotificationManager::class.java).cancel(TAG, ID)
                resetTelecomMissedCalls(context)
            }
        }
    }

    fun clear() = synchronized(lock) {
        // Also invalidates a photo decode started before the user acknowledged these calls.
        generation++
        MissedCallBadgeState.clear(context)
        resetTelecomMissedCalls(context)
        try {
            manager.activeNotifications
                .filter { it.notification.channelId == CallNotificationManager.MISSED_CHANNEL_ID }
                .forEach { manager.cancel(it.tag, it.id) }
        } catch (error: RuntimeException) {
            Log.w("CallNotification", "Unable to clear missed-call notifications", error)
        }
    }

    /** Telecom replays this count after process death; it is not another missed call. */
    fun recover(count: Int, number: String, receivedAt: Long = System.currentTimeMillis(),
                expectedGeneration: Long = generationToken()): Boolean = synchronized(lock) {
        if (expectedGeneration != generation) return false
        if (count <= 0 || !manager.areNotificationsEnabled()) return false
        try {
            manager.createNotificationChannel(NotificationChannel(
                CallNotificationManager.MISSED_CHANNEL_ID,
                context.getString(R.string.notif_channel_missed_calls),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { setShowBadge(true) })
            if (manager.getNotificationChannel(CallNotificationManager.MISSED_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
            val saved = MissedCallBadgeState.read(context)
            val active = manager.activeNotifications.any { it.tag == TAG && it.id == ID }
            val through = receivedAt
            val newer = saved?.callStarts.orEmpty().filter { it > through }
            val total = (count.toLong() + newer.size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (active && saved != null && saved.count == total) {
                // Preserve the locally resolved photo/actions and do not re-alert a replay.
                MissedCallBadgeState.write(context, saved.copy(callStarts = newer,
                    recoveredThrough = maxOf(saved.recoveredThrough, through)))
                return true
            }
            val name = number.ifBlank { context.getString(R.string.label_unknown_number) }
            val avatar = notificationAvatar(null, "?")
            val person = Person.Builder().setName(name).apply {
                if (number.isNotBlank()) { setUri("tel:$number"); setKey(callConversationKey(number)) }
            }.build()
            val identity = CallNotificationIdentity(name, if (total == 1) number else "", avatar, person)
            val names = if (total == 1) listOf(name) else listOf(name) + saved?.names.orEmpty().takeLast(newer.size)
            val event = saved?.event?.takeIf { saved.count == total } ?: UUID.randomUUID().toString()
            manager.notify(TAG, ID, build(identity, total, names, event))
            MissedCallBadgeState.write(context, MissedCallBadgeState(total, names, event, newer, through))
            true
        } catch (error: RuntimeException) {
            Log.w("CallNotification", "Unable to recover missed-call notification", error)
            false
        }
    }

    fun generationToken(): Long = synchronized(lock) { generation }

    suspend fun show(call: Call, name: String, photoUri: String?, loadPhoto: suspend (String?) -> Bitmap?) =
        showFromGeneration(generationToken(), call, name, photoUri, loadPhoto)

    suspend fun showFromGeneration(startedAt: Long, call: Call, name: String, photoUri: String?, loadPhoto: suspend (String?) -> Bitmap?) {
        synchronized(lock) { if (startedAt != generation || publishedCalls.containsKey(call)) return }
        val allowedNumber = notificationCallerNumber(call)
        val photo = if (allowedNumber.isNotBlank()) loadPhoto(photoUri) else null
        val identity = notificationCallerIdentity(context, call, name, photo)
        val snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { callLogSnapshot() }
        synchronized(lock) {
            if (startedAt != generation || publishedCalls.containsKey(call)) return
            try {
                manager.createNotificationChannel(NotificationChannel(
                    CallNotificationManager.MISSED_CHANNEL_ID,
                    context.getString(R.string.notif_channel_missed_calls),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    setShowBadge(true)
                })
                val previous = manager.activeNotifications
                    .filter { it.notification.channelId == CallNotificationManager.MISSED_CHANNEL_ID }
                // Never read back our just-posted count from NotificationManager: its queue
                // may not yet expose that notification when the next call arrives.
                val saved = MissedCallBadgeState.read(context)
                val start = call.details.creationTimeMillis
                if (start > 0 && (start in saved?.callStarts.orEmpty() ||
                        (snapshot != null && start in snapshot.allStarts && start !in snapshot.unreadStarts))) return
                val previousCount = saved?.count?.toLong()
                    ?: previous.sumOf { it.notification.number.coerceAtLeast(1).toLong() }
                val covered = start > 0 && start <= (saved?.recoveredThrough ?: 0L)
                val pending = (saved?.callStarts.orEmpty() + if (start > 0 && !covered) listOf(start) else emptyList())
                    .distinct().filter { snapshot == null || it !in snapshot.allStarts }
                val reconciled = if (snapshot != null && start > 0 &&
                        (start in snapshot.allStarts || (saved?.recoveredThrough ?: 0L) == 0L))
                    snapshot.unreadStarts.size.toLong() + pending.size else null
                val count = (reconciled ?: (previousCount + if (covered) 0 else 1))
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (count <= 0) return
                val names = (saved?.names ?: previous.flatMap { notification ->
                    notification.notification.extras.getStringArrayList(EXTRA_NAMES)
                        ?: listOf(notification.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                            ?: context.getString(R.string.label_unknown_number))
                }).plus(identity.name).takeLast(minOf(MAX_NAMES, count))
                val event = UUID.randomUUID().toString()
                val notification = build(identity, count, names, event)
                val starts = (pending + if (start > 0) listOf(start) else emptyList()).distinct()
                val state = MissedCallBadgeState(count, names, event, starts,
                    if (reconciled != null) 0L else saved?.recoveredThrough ?: 0L)
                if (!MissedCallBadgeState.write(context, state)) {
                    Log.w("CallNotification", "Unable to persist missed-call badge count")
                }
                try {
                    manager.notify(TAG, ID, notification)
                } catch (error: RuntimeException) {
                    if (saved != null) MissedCallBadgeState.write(context, saved) else MissedCallBadgeState.clear(context)
                    throw error
                }
                // Only replace legacy per-number notifications after the new one was posted.
                previous.filterNot { it.tag == TAG && it.id == ID }.forEach { manager.cancel(it.tag, it.id) }
                publishedCalls[call] = true
            } catch (error: RuntimeException) {
                Log.w("CallNotification", "Unable to publish missed-call notification", error)
            }
        }
    }

    private fun build(identity: CallNotificationIdentity, count: Int, names: List<String>, event: String): Notification {
        val recents = Intent(context, MainActivity::class.java).apply {
            action = "dev.goodwy.rphone.ACTION_VIEW_RECENTS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(context, 10, recents, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val deleted = Intent(context, MissedCallDismissReceiver::class.java).apply {
            data = android.net.Uri.fromParts("missed-call", event, null)
            putExtra(EXTRA_EVENT, event)
        }
        val builder = NotificationCompat.Builder(context, CallNotificationManager.MISSED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dot_dialer_notification)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(contentIntent)
            .setDeleteIntent(PendingIntent.getBroadcast(context, 13, deleted, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setNumber(count)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_LARGE)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .addExtras(Bundle().apply {
                putStringArrayList(EXTRA_NAMES, ArrayList(names))
                putString(EXTRA_EVENT, event)
            })
        if (count == 1) {
            val localUser = Person.Builder().setName(context.getString(R.string.notification_local_user)).build()
            builder.setLargeIcon(identity.avatar)
                .addPerson(identity.person)
                .setStyle(NotificationCompat.MessagingStyle(localUser)
                    .addMessage(context.getString(R.string.notif_missed_call_title), System.currentTimeMillis(), identity.person)
                    .setGroupConversation(false))
            identity.publishConversation(context)?.let { id ->
                builder.setShortcutId(id).setLocusId(LocusIdCompat(id))
            }
            if (identity.number.isNotBlank()) {
                builder.addAction(action(identity.number, event, NotificationActivity.ACTION_CALL, R.drawable.ic_widget_call, R.string.notif_call_back, 11))
                builder.addAction(action(identity.number, event, NotificationActivity.ACTION_SMS, R.drawable.ic_message_filled, R.string.message, 12))
            }
        } else {
            val title = context.resources.getQuantityString(R.plurals.notification_missed_calls_count, count, count)
            val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
            names.forEach(inbox::addLine)
            builder.setContentTitle(title)
                .setContentText(names.distinct().takeLast(3).joinToString(" · "))
                .setLargeIcon(notificationAvatar(null, count.toString(), "missed_calls"))
                .setStyle(inbox)
            // No Person or shortcut: a multi-call aggregate must not impersonate one contact.
        }
        return builder.build()
    }

    private fun action(number: String, event: String, action: String, icon: Int, label: Int, requestCode: Int): NotificationCompat.Action {
        val intent = Intent(context, NotificationActivity::class.java).apply {
            this.action = action
            data = android.net.Uri.fromParts("missed-call", event, action)
            putExtra(NotificationActivity.EXTRA_NUMBER, number)
            putExtra(EXTRA_EVENT, event)
        }
        return NotificationCompat.Action.Builder(icon, context.getString(label), PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )).build()
    }
}

internal fun shouldNotifyMissedCall(details: Call.Details): Boolean =
    details.callDirection == Call.Details.DIRECTION_INCOMING && details.connectTimeMillis == 0L &&
        (details.disconnectCause?.code == DisconnectCause.MISSED || details.disconnectCause?.code == DisconnectCause.REMOTE)
