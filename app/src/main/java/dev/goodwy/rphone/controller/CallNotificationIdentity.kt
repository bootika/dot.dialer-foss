package dev.goodwy.rphone.controller

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.telecom.Call
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.core.graphics.withScale
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import dev.goodwy.rphone.MainActivity
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.isTelecomConference
import java.security.MessageDigest
import androidx.compose.ui.graphics.toArgb
import dev.goodwy.rphone.view.components.DOT_UNKNOWN_CALLER_PATH
import dev.goodwy.rphone.view.components.modernPastelAvatarColor

/** Identity shared by the call template, missed-call conversation and its shortcut. */
internal data class CallNotificationIdentity(
    val name: String,
    val number: String,
    val avatar: Bitmap,
    val person: Person,
) {
    fun publishConversation(context: Context): String? {
        if (number.isBlank()) return null
        val id = person.key ?: return null
        // Opening a conversation must never place a call without another deliberate tap.
        val intent = Intent(Intent.ACTION_VIEW, "tel:${android.net.Uri.encode(number)}".toUri()).apply {
            setClass(context, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return try {
            val shortcut = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(name.take(40))
                .setLongLabel(name)
                .setIcon(IconCompat.createWithAdaptiveBitmap(avatar))
                .setPerson(person)
                .setLongLived(true)
                .setLocusId(LocusIdCompat(id))
                .setIntent(intent)
                .build()
            if (ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)) id else null
        } catch (error: RuntimeException) {
            Log.w("CallNotification", "Unable to publish call conversation shortcut", error)
            null
        }
    }
}

internal fun notificationCallerNumber(call: Call): String = call.details?.handle
    ?.schemeSpecificPart.orEmpty().takeIf {
        call.details?.handlePresentation == TelecomManager.PRESENTATION_ALLOWED && !call.isTelecomConference()
    }.orEmpty()

internal fun notificationCallerIdentity(
    context: Context,
    call: Call,
    contactName: String,
    contactPhoto: Bitmap?,
): CallNotificationIdentity {
    val conference = call.isTelecomConference()
    val allowed = call.details?.handlePresentation == TelecomManager.PRESENTATION_ALLOWED && !conference
    val number = notificationCallerNumber(call)
    val name = when {
        conference -> context.getString(R.string.conference_call)
        !allowed -> context.getString(R.string.label_unknown_number)
        else -> contactName.trim().ifBlank { number.ifBlank { context.getString(R.string.label_unknown_number) } }
    }
    val initial = if (conference) "∴" else if (!allowed || name == number) "?" else name.take(1).uppercase()
    val avatar = notificationAvatar(if (allowed) contactPhoto else null, initial, name)
    val person = Person.Builder().setName(name).setIcon(IconCompat.createWithBitmap(avatar)).apply {
        if (number.isNotBlank()) {
            setKey(callConversationKey(number))
            setUri("tel:$number")
        }
    }.build()
    return CallNotificationIdentity(name, number, avatar, person)
}

internal fun callConversationKey(number: String): String {
    val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(number).ifBlank { number }
    val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
    return "call-contact-" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** A bounded, centered avatar; a missing photo never leaves an empty shield. */
internal fun notificationAvatar(photo: Bitmap?, label: String, colorSeed: String = label): Bitmap {
    val size = 192
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (photo != null && !photo.isRecycled && photo.width > 0 && photo.height > 0) {
        canvas.drawColor(android.graphics.Color.WHITE)
        val side = minOf(photo.width, photo.height)
        val left = (photo.width - side) / 2
        val top = (photo.height - side) / 2
        canvas.drawBitmap(photo, android.graphics.Rect(left, top, left + side, top + side), android.graphics.Rect(0, 0, size, size), paint)
    } else {
        val (background, foreground) = modernPastelAvatarColor(colorSeed)
        paint.color = background.toArgb()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = foreground.toArgb()
        paint.typeface = Typeface.DEFAULT
        paint.textSize = size * 0.46f
        paint.textAlign = Paint.Align.CENTER
        if (label.isBlank() || label == "?") {
            val path = requireNotNull(androidx.core.graphics.PathParser.createPathFromPathData(DOT_UNKNOWN_CALLER_PATH))
            canvas.withTranslation(size * 0.23f, size * 0.23f) {
                withScale(size * 0.54f / 24f, size * 0.54f / 24f) {
                    drawPath(path, paint)
                }
            }
            return bitmap
        }
        val text = label
        while (paint.measureText(text) > size * 0.75f && paint.textSize > 12f) paint.textSize -= 2f
        canvas.drawText(text, size / 2f, (size - paint.descent() - paint.ascent()) / 2f, paint)
    }
    return bitmap
}
