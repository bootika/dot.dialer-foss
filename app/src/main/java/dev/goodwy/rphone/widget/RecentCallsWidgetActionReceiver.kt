package dev.goodwy.rphone.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Handles the privileged action behind a widget PendingIntent without exporting it to apps. */
class RecentCallsWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DIAL) return
        val number = intent.getStringExtra(EXTRA_PHONE_NUMBER)?.takeIf(String::isNotBlank) ?: return
        val callUri = Uri.fromParts("tel", number, null)

        try {
            context.startActivity(
                Intent(Intent.ACTION_CALL, callUri).apply {
                    setClass(context, dev.goodwy.rphone.MainActivity::class.java)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
        } catch (error: Exception) {
            Log.w(TAG, "Direct widget call failed; opening the Dot dial pad", error)
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, callUri).apply {
                    setClass(context, dev.goodwy.rphone.MainActivity::class.java)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    },
                )
            }.onFailure { fallbackError ->
                Log.e(TAG, "Unable to open the Dot dial pad from the widget", fallbackError)
            }
        }
    }

    companion object {
        private const val TAG = "RecentCallsWidget"
        const val ACTION_DIAL = "dev.goodwy.rphone.widget.ACTION_DIAL"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
    }
}
