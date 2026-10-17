package dev.goodwy.rphone.controller.util

import android.content.Context
import android.content.Intent
import android.net.Uri

internal enum class CallMessageChannel(val stored: Int) {
    SMS(0), WHATSAPP(1), ASK(2);
    companion object {
        fun fromStored(value: Int) = entries.firstOrNull { it.stored == value } ?: SMS
    }
}

internal fun callMessageChoices(whatsAppAvailable: Boolean): List<CallMessageChannel> =
    if (whatsAppAvailable) listOf(CallMessageChannel.SMS, CallMessageChannel.WHATSAPP)
    else listOf(CallMessageChannel.SMS)

internal fun callMessageWhatsAppPackage(context: Context): String? =
    listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { context.isPackageInstalled(it) }

/** Explicit package only: an absent app must never turn a message into a browser launch. */
internal fun callMessageWhatsAppIntent(context: Context, number: String, message: String?): Intent? {
    val app = callMessageWhatsAppPackage(context) ?: return null
    val digits = SocialUtils.cleanDigitsOnly(context, number)
    if (digits.isBlank()) return null
    val uri = Uri.Builder().scheme("https").authority("api.whatsapp.com").path("send")
        .appendQueryParameter("phone", digits).apply {
            message?.let { appendQueryParameter("text", it) }
        }.build()
    return Intent(Intent.ACTION_VIEW, uri).setPackage(app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
