package dev.goodwy.rphone.controller.util

import android.app.Notification
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Explicit snapshot only: dismissed notifications cannot be recovered or fabricated. */
internal class ManualNotificationAvatarScan(
    private val contacts: WhatsAppAvatarContactStore, private val reviews: WhatsAppAvatarReviewStore,
    private val allowed: () -> Boolean,
    private val capture: (String, Notification, String?) -> PendingContactAvatar?,
    private val queue: (WhatsAppAvatarContactTarget, CapturedWhatsAppAvatar, String) -> Unit,
    private val ambiguous: (String, WhatsAppSenderIdentity, CapturedWhatsAppAvatar, String) -> Unit,
) {
    suspend fun run(account: String) {
        if (!allowed()) return
        reviews.beginSearch(account,"whatsapp")
        for (notification in WhatsAppAvatarNotificationListener.currentNotifications()) {
            currentCoroutineContext().ensureActive()
            if(!allowed())return
            val event=capture(notification.packageName,notification.notification,notification.tag) ?: continue
            val avatar=event.avatar ?: continue
            val identity=event.identity
            val target=if(identity.phoneNumber!=null)contacts.resolveByPhone(identity.phoneNumber,account)
                else if(identity.senderIdentity)identity.displayName?.let { contacts.resolveByUniqueName(it,account) } else null
            if(!allowed())return
            if(target!=null)queue(target,avatar,event.packageName) else ambiguous(account,identity,avatar,event.packageName)
        }
    }
}
