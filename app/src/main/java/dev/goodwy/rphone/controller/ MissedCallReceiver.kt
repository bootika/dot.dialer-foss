package dev.goodwy.rphone.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import kotlinx.coroutines.launch

class MissedCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION -> {
                if (!dev.goodwy.rphone.controller.util.isAlreadyDefaultDialer(context)) return
                val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
                val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER).orEmpty()
                val receivedAt = System.currentTimeMillis()
                val notifications = MissedCallNotifications(context)
                val generation = notifications.generationToken()
                // Suppress the platform fallback only after Dot actually has a notification.
                val pending = goAsync()
                val ordered = isOrderedBroadcast
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        if (notifications.recover(count, number, receivedAt, generation) && ordered) pending.abortBroadcast()
                    } finally { pending.finish() }
                }
            }
        }
    }
}
