package dev.goodwy.rphone.controller.util

import android.content.Context
import android.media.RingtoneManager
import android.util.Log
import androidx.core.net.toUri

fun ringtoneTitleOrFallback(context: Context, uri: String, fallback: String): String = try {
    RingtoneManager.getRingtone(context, uri.toUri())?.getTitle(context) ?: fallback
} catch (error: RuntimeException) {
    Log.w("RingtoneUtils", "Unable to resolve the saved contact ringtone", error)
    fallback
}
