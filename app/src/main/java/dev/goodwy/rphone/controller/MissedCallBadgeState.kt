package dev.goodwy.rphone.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

internal data class MissedCallBadgeState(
    val count: Int, val names: List<String>, val event: String,
    val callStarts: List<Long> = emptyList(), val recoveredThrough: Long = 0L,
) {
    companion object {
        private const val FILE = "missed_call_badge"
        private const val STATE = "state"

        private fun preferences(context: Context) = context.createDeviceProtectedStorageContext()
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)

        fun read(context: Context): MissedCallBadgeState? {
            val raw = preferences(context).getString(STATE, null) ?: return null
            return try {
                val json = JSONObject(raw)
                val names = json.getJSONArray("names")
                val starts = json.optJSONArray("callStarts") ?: JSONArray()
                MissedCallBadgeState(json.getInt("count"), List(names.length()) { names.getString(it) }, json.getString("event"),
                    List(starts.length()) { starts.getLong(it) }, json.optLong("recoveredThrough"))
                    .takeIf { it.count > 0 }
            } catch (error: org.json.JSONException) {
                android.util.Log.w("CallNotification", "Invalid missed-call badge state", error)
                null
            }
        }

        // KTX edit returns Unit; retain the framework commit result to detect disk failures.
        @android.annotation.SuppressLint("UseKtx")
        fun write(context: Context, state: MissedCallBadgeState): Boolean = preferences(context).edit().putString(STATE,
            JSONObject().put("count", state.count).put("names", JSONArray(state.names)).put("event", state.event)
                .put("callStarts", JSONArray(state.callStarts)).put("recoveredThrough", state.recoveredThrough).toString(),
        ).commit()

        fun clear(context: Context) = preferences(context).edit(commit = true) { remove(STATE) }
    }
}

/** Explicit, non-exported receiver: swiping a badge only acknowledges that version. */
class MissedCallDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = intent.getStringExtra(MissedCallNotifications.EXTRA_EVENT) ?: return
        MissedCallNotifications.dismiss(context, event)
    }
}

/** Clearing our notification alone leaves Telecom's recovered count accumulating. */
// The documented default-dialer role grants access; the SDK annotation lists only the privileged permission.
@android.annotation.SuppressLint("MissingPermission")
internal fun resetTelecomMissedCalls(context: Context) {
    try {
        context.getSystemService(android.telecom.TelecomManager::class.java)?.cancelMissedCallsNotification()
    } catch (error: RuntimeException) {
        // Losing the default-dialer role or an unavailable OEM service must not block local acknowledgement.
        android.util.Log.w("CallNotification", "Unable to reset Telecom missed calls (${error.javaClass.simpleName})")
    }
}
