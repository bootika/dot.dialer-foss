package dev.goodwy.rphone.controller.util

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.telephony.PhoneNumberUtils
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.drawable.toDrawable
import java.io.ByteArrayOutputStream

internal data class WhatsAppSenderIdentity(val phoneNumber: String?, val displayName: String?, val senderIdentity: Boolean)
internal data class CapturedWhatsAppAvatar(
    val bytes: ByteArray, val width: Int, val height: Int,
    val phoneNumber: String?, val displayName: String?,
    val originalWidth: Int = width, val originalHeight: Int = height,
    val source: String = "WhatsApp", val senderIdentity: Boolean = true,
    val companyDomain: String? = null, val companyName: String? = null, val companyDiscovery: Boolean = false,
)
internal object WhatsAppNotificationAvatarExtractor {
    val SUPPORTED_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val jid = Regex("^(\\d{8,15})@(s\\.whatsapp\\.net|c\\.us)$")
    private val phone = Regex("^\\+?[0-9 ()-]{8,24}$")

    fun identity(packageName: String, notification: Notification, tag: String?): WhatsAppSenderIdentity? {
        if (packageName !in SUPPORTED_PACKAGES ||
            (notification.category != null && notification.category != Notification.CATEGORY_MESSAGE) ||
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        // Conversation title/group JID are conservative group signals; do not save any message text.
        if (style?.isGroupConversation == true || !style?.conversationTitle.isNullOrBlank()) return null
        val people = style?.messages?.mapNotNull { it.person }.orEmpty()
        val sender = people.lastOrNull()
        val structured = listOfNotNull(sender?.uri, sender?.key, notification.shortcutId, tag)
        if (structured.any { it.contains("@g.us") || it.contains("@broadcast") }) return null
        val numbers = structured.mapNotNull(::extractPhone).distinct()
        if (numbers.size > 1) return null
        // Different senders in an otherwise unmarked conversation can be a group.
        if (people.map { it.uri ?: it.key ?: it.name?.toString() }.distinct().size > 1) return null
        val name = sender?.name?.toString()?.trim()?.takeIf { it.isNotBlank() }
        // Notification titles can contain message previews. Never retain them as an unknown recipient.
        if (sender == null && numbers.isEmpty()) return null
        return WhatsAppSenderIdentity(numbers.singleOrNull(), name, sender != null)
    }
    @Suppress("DEPRECATION")
    fun extract(context: Context, packageName: String, notification: Notification, tag: String?): CapturedWhatsAppAvatar? {
        val identity = identity(packageName, notification, tag) ?: return null
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val person = style?.messages?.lastOrNull()?.person
        // Priority matters: never choose a larger icon over the sender's own identity icon.
        val drawables = listOf<() -> Drawable?>(
            { person?.icon?.loadDrawable(context) },
            { notification.getLargeIcon()?.loadDrawable(context) },
            { extraDrawable(context, notification, Notification.EXTRA_LARGE_ICON) },
            { extraDrawable(context, notification, Notification.EXTRA_LARGE_ICON_BIG) },
        )
        val bitmap = drawables.firstNotNullOfOrNull { source ->
            runCatching { source()?.let(::drawableToBitmap)?.takeIf { it.width >= 48 && it.height >= 48 } }.getOrNull()
        } ?: return null
        val normalized = if (maxOf(bitmap.width, bitmap.height) > 1024) {
            val scale = 1024f / maxOf(bitmap.width, bitmap.height)
            bitmap.scale((bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else bitmap
        val bytes = ByteArrayOutputStream().use {
            if (!normalized.compress(Bitmap.CompressFormat.PNG, 100, it)) return null
            it.toByteArray()
        }
        return CapturedWhatsAppAvatar(bytes, normalized.width, normalized.height, identity.phoneNumber,
            identity.displayName, bitmap.width, bitmap.height, senderIdentity = identity.senderIdentity)
    }
    internal fun extractPhone(value: String): String? {
        val jidMatch = jid.matchEntire(value)
        if (jidMatch != null) return "+" + jidMatch.groupValues[1]
        val tel = value.removePrefix("tel:")
        if (!phone.matches(tel)) return null
        val normalized = PhoneNumberUtils.normalizeNumber(tel)
        return normalized.takeIf { it.matches(Regex("\\+[1-9]\\d{7,14}")) }
    }
    @Suppress("DEPRECATION")
    private fun extraDrawable(context: Context, n: Notification, key: String): Drawable? =
        when (val value = n.extras.getParcelable<android.os.Parcelable>(key)) {
            is Bitmap -> value.toDrawable(context.resources)
            is android.graphics.drawable.Icon -> value.loadDrawable(context)
            else -> null
        }
    internal fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) return drawable.bitmap
        // Adaptive icons apply a launcher/system mask at draw time. Preserve the supplied
        // square bitmap instead of burning that mask into a contact's stored photograph.
        if (drawable is AdaptiveIconDrawable && drawable.foreground is BitmapDrawable)
            return (drawable.foreground as BitmapDrawable).bitmap
        val width = drawable.intrinsicWidth; val height = drawable.intrinsicHeight
        if (width !in 1..4096 || height !in 1..4096) return null
        return createBitmap(width, height).also {
            drawable.setBounds(0, 0, width, height); drawable.draw(Canvas(it))
        }
    }
}
