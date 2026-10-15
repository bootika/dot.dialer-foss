package dev.goodwy.rphone.controller.util

import android.app.NotificationManager
import android.content.Context

/** Results remain available for manual editing, never as notifications or launcher badges. */
internal object ContactOptimizationNotifications {
    const val ACCOUNT_EXTRA = "contact_optimization_account"
    fun cancelLegacy(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications.filter { it.id == 84104 && it.notification.channelId == "whatsapp_avatar_review" }
            .forEach { manager.cancel(it.tag, it.id) }
    }
    @Suppress("UNUSED_PARAMETER")
    fun update(context: Context, account: String, count: Int) = cancelLegacy(context)
}
