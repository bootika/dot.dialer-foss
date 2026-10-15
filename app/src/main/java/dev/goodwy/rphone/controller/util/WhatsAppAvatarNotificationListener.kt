package dev.goodwy.rphone.controller.util

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CancellationException
import org.koin.android.ext.android.inject

/** Queue contains only sanitized identity/photo proposals; never Notification/message objects. */
class WhatsAppAvatarNotificationListener : NotificationListenerService() {
    private val processor: WhatsAppAvatarSyncProcessor by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val candidates = Channel<PendingContactAvatar>(8, BufferOverflow.DROP_OLDEST)
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            for (candidate in candidates) {
                try { processor.processCaptured(candidate) }
                catch (e: CancellationException) { throw e }
                catch (error: Exception) { processor.reportFailure(candidate.account, "processing_failed", error) }
            }
        }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in WhatsAppNotificationAvatarExtractor.SUPPORTED_PACKAGES) return
        runCatching { processor.capture(sbn.packageName, sbn.notification, sbn.tag) }
            .getOrNull()?.let { candidates.trySend(it) }
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch { processor.reconcileAppliedPhotos() }
    }
    override fun onDestroy() {
        candidates.close(); scope.cancel(); super.onDestroy()
    }
}
