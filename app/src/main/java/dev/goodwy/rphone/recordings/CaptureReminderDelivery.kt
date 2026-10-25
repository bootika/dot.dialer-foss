package dev.goodwy.rphone.recordings

import android.content.SharedPreferences

/** Dismissing a reminder suppresses repeats for that activation, including after app recreation. */
internal class CaptureReminderDelivery(private val preferences: SharedPreferences) {
    @Synchronized fun postOnce(session: String, post: () -> Boolean) {
        if(preferences.getString("last_activation",null)==session)return
        if(post())check(preferences.edit().putString("last_activation",session).commit())
    }
}
