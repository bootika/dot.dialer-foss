package dev.goodwy.rphone.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.controller.util.loadPhoneAccount
import io.github.bootika.dotdialer.core.call.CallPresentationPolicy

class CallNotificationManager(
    private val context: Context,
    private val preferenceManager: PreferenceManager
) {
    companion object {
        const val CHANNEL_ID_LOW = "call_channel_low"
        const val CHANNEL_ID_HIGH = "call_channel_high"
        const val INCOMING_CHANNEL_ID = "incoming_call_channel_v3"
        const val FULLSCREEN_INCOMING_CHANNEL_ID = "fullscreen_incoming_call_channel_v4"
        const val MISSED_CHANNEL_ID = "missed_call_channel_v3"
        const val NOTIFICATION_ID = 101
    }

    private val missedCalls = MissedCallNotifications(context)
    // A missed-call notification must finish even after Telecom destroys the last-call service.
    private val missedDeliveryScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )
    private val createdChannels = mutableSetOf<String>()

    fun clearAllMissedCallNotifications() = missedCalls.clear()

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun buildCallNotification(
        call: Call,
        contactName: String,
        contactPhoto: Bitmap?,
        audioState: CallAudioState?,
        high: Boolean = false,
        suppressIncomingAlert: Boolean = false,
    ): Notification {
        val fullscreenCalls = preferenceManager.getBoolean(
            PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS,
            CallPresentationPolicy.DEFAULT_ALWAYS_FULL_SCREEN,
        )
        val isRinging = call.currentTelecomState() == Call.STATE_RINGING
        val shouldAlertIncoming = CallPresentationPolicy.shouldAlertIncomingNotification(
            isIncomingRinging = isRinging,
            suppressIncomingAlert = suppressIncomingAlert,
        )
        val shouldPresentFullScreen = CallPresentationPolicy.shouldAttachFullScreenIntent(
            isIncomingRinging = isRinging,
            alwaysFullScreen = fullscreenCalls,
            suppressIncomingAlert = suppressIncomingAlert,
        )
        val shouldUseHighPriorityChannel = CallPresentationPolicy.shouldUseHighPriorityCallChannel(
            isIncomingRinging = isRinging,
            suppressIncomingAlert = suppressIncomingAlert,
            requestedHighPriority = high,
        )
        val channelId = when {
            shouldPresentFullScreen -> FULLSCREEN_INCOMING_CHANNEL_ID
            shouldAlertIncoming -> INCOMING_CHANNEL_ID
            shouldUseHighPriorityChannel -> CHANNEL_ID_HIGH
            else -> CHANNEL_ID_LOW
        }

        if (!createdChannels.contains(channelId)) {
//            notificationManager.deleteNotificationChannel(channelId)
            val isIncomingChannel = channelId == FULLSCREEN_INCOMING_CHANNEL_ID ||
                channelId == INCOMING_CHANNEL_ID
            val channel = NotificationChannel(
                channelId,
                when (channelId) {
                    FULLSCREEN_INCOMING_CHANNEL_ID -> context.getString(R.string.notif_channel_fullscreen_incoming_calls)
                    INCOMING_CHANNEL_ID -> context.getString(R.string.notif_channel_incoming_calls)
                    CHANNEL_ID_HIGH -> "Call Notification on a Locked Screen"
                    else -> context.getString(R.string.notif_channel_outgoing_calls)
                },
                if (isIncomingChannel || channelId == CHANNEL_ID_HIGH) {
                    NotificationManager.IMPORTANCE_HIGH
                } else {
                    NotificationManager.IMPORTANCE_LOW
                },
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(isIncomingChannel)
                setShowBadge(false)
                if (isIncomingChannel) setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
            createdChannels.add(channelId)
        }

        // Removed contactPhoto = getContactBitmap(photoUri) - handled via parameter

        val accountHandle = call.details.accountHandle
        val simLabel = accountHandle?.let { loadPhoneAccount(context, it)?.label?.toString() }

        val fullScreenIntent = Intent(context, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            if (isRinging) call.hashCode() else 0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(context, CallService::class.java).apply { action = "ANSWER_CALL" }
        val answerPendingIntent = PendingIntent.getService(context, 1, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val declineIntent = Intent(context, CallService::class.java).apply { action = "DECLINE_CALL" }
        val declinePendingIntent = PendingIntent.getService(context, 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val speakerIntent = Intent(context, CallService::class.java).apply { action = "TOGGLE_SPEAKER" }
        val speakerPendingIntent = PendingIntent.getService(context, 4, speakerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val muteIntent = Intent(context, CallService::class.java).apply { action = "TOGGLE_MUTE" }
        val mutePendingIntent = PendingIntent.getService(context, 5, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val isMuted = audioState?.isMuted ?: false
        val audioRoute = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
        val audioLabel = when (audioRoute) {
            CallAudioState.ROUTE_SPEAKER -> context.getString(R.string.audio_route_speaker)
            CallAudioState.ROUTE_BLUETOOTH -> {
                try {
                    audioState?.activeBluetoothDevice?.name ?: context.getString(R.string.audio_route_bluetooth)
                } catch (error: SecurityException) {
                    Log.w("CallNotification", "Permission denied while reading Bluetooth route name", error)
                    context.getString(R.string.audio_route_bluetooth)
                }
            }
            CallAudioState.ROUTE_WIRED_HEADSET -> context.getString(R.string.audio_route_headset)
            else -> context.getString(R.string.audio_route_handset)
        }

        val contentText = buildString {
            if (call.currentTelecomState() == Call.STATE_RINGING) append(context.getString(R.string.call_status_incoming)) else append(context.getString(R.string.notif_active_call))
            if (!simLabel.isNullOrEmpty()) {
                append(" ")
                append(context.getString(R.string.notif_via_sim, simLabel))
            }
        }

        val caller = notificationCallerIdentity(context, call, contactName, contactPhoto)
        val shortcutId = caller.publishConversation(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = android.app.Person.Builder()
                .setName(caller.name)
                .setIcon(Icon.createWithBitmap(caller.avatar))
                .setKey(caller.person.key)
                .setUri(caller.person.uri)
                .build()

            val notificationColor = ContextCompat.getColor(context, R.color.notification_color)
            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_dot_dialer_notification)
                .setContentTitle(caller.name)
                .setLargeIcon(caller.avatar)
                .setBadgeIconType(Notification.BADGE_ICON_LARGE)
                .setNumber(0)
                .setContentText(contentText)
                .setCategory(Notification.CATEGORY_CALL)
                .setContentIntent(fullScreenPendingIntent)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(!shouldAlertIncoming)
                .setStyle(
                    if (isRinging) {
                        Notification.CallStyle.forIncomingCall(person, declinePendingIntent, answerPendingIntent)
                    } else {
                        Notification.CallStyle.forOngoingCall(person, declinePendingIntent)
                    }
                )
                .setColorized(true)
                .setColor(notificationColor)
                .addPerson(person)
                .apply { shortcutId?.let { setShortcutId(it) } }

            if (isRinging) {
                if (shouldPresentFullScreen) {
                    builder.setFullScreenIntent(fullScreenPendingIntent, true)
                }
                builder.setUsesChronometer(false)
                builder.setShowWhen(false)
            } else {
                val connectTime = call.details.connectTimeMillis
                if (call.currentTelecomState() == Call.STATE_ACTIVE && connectTime > 0) {
                    builder.setWhen(connectTime)
                    builder.setUsesChronometer(true)
                    builder.setShowWhen(true)
                } else {
                    builder.setUsesChronometer(false)
                    builder.setShowWhen(false)
                }
            }

            if (call.currentTelecomState() != Call.STATE_RINGING) {
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_notif_speaker),
                        audioLabel,
                        speakerPendingIntent
                    ).build()
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, if (isMuted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic_on),
                        if (isMuted) context.getString(R.string.unmute) else context.getString(R.string.mute),
                        mutePendingIntent
                    ).build()
                )
            }
            builder.build()
        } else {
            val person = caller.person

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_dot_dialer_notification)
                .setContentTitle(caller.name)
                .setLargeIcon(caller.avatar)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_LARGE)
                .setNumber(0)
                .addPerson(person)
                .apply { shortcutId?.let { setShortcutId(it) } }
                .setContentText(contentText)
                .setPriority(
                    if (shouldAlertIncoming) NotificationCompat.PRIORITY_MAX
                    else NotificationCompat.PRIORITY_LOW
                )
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(fullScreenPendingIntent)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setSilent(!shouldAlertIncoming)
                .setOnlyAlertOnce(!shouldAlertIncoming)
                .setDefaults(if (shouldAlertIncoming) NotificationCompat.DEFAULT_ALL else 0)
                .setStyle(
                    if (isRinging) {
                        NotificationCompat.CallStyle.forIncomingCall(
                            person,
                            declinePendingIntent,
                            answerPendingIntent
                        )
                    } else {
                        NotificationCompat.CallStyle.forOngoingCall(person, declinePendingIntent)
                    }
                )
                .setColorized(false)

            if (shouldPresentFullScreen) {
                builder.setFullScreenIntent(fullScreenPendingIntent, true)
            }

            if (call.currentTelecomState() == Call.STATE_ACTIVE) {
                val connectTime = call.details.connectTimeMillis
                if (connectTime > 0) {
                    builder.setWhen(connectTime)
                    builder.setUsesChronometer(true)
                    builder.setShowWhen(true)
                } else {
                    builder.setUsesChronometer(false)
                    builder.setShowWhen(false)
                }
            } else {
                builder.setUsesChronometer(false)
                builder.setShowWhen(false)
            }

            if (call.currentTelecomState() != Call.STATE_RINGING) {
                builder.addAction(
                    R.drawable.ic_notif_speaker,
                    audioLabel,
                    speakerPendingIntent
                )
                builder.addAction(
                    if (isMuted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic_on,
                    if (isMuted) context.getString(R.string.unmute) else context.getString(R.string.mute),
                    mutePendingIntent
                )
            }
            builder.build()
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun showBlockedNotification(number: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_LOW)
            .setSmallIcon(R.drawable.ic_close)
            .setContentTitle(context.getString(R.string.notif_blocked_call_title))
            .setContentText(context.getString(R.string.notif_blocked_call_text, number))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        notificationManager.notify(number.hashCode(), builder.build())
    }

    suspend fun getContactBitmap(photoUri: String?): Bitmap? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (photoUri == null) return@withContext null
        try {
            val uri = photoUri.toUri()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w("CallNotification", "Unable to decode the contact photo for a notification", error)
            null
        }
    }

    suspend fun showMissedCallNotification(call: Call, contactName: String, photoUri: String?) {
        missedCalls.show(call, contactName, photoUri, ::getContactBitmap)
    }

    fun enqueueMissedCallNotification(
        call: Call,
        contactName: String,
        photoUri: String?,
        shouldShow: suspend () -> Boolean,
    ): kotlinx.coroutines.Job {
        val generation = missedCalls.generationToken()
        return missedDeliveryScope.launch {
            if (shouldShow()) missedCalls.showFromGeneration(generation, call, contactName, photoUri, ::getContactBitmap)
        }
    }
}
