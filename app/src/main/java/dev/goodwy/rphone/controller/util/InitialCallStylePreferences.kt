package dev.goodwy.rphone.controller.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_RECENT_TAP_ACTION
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_SWIPE_LEFT_ACTION
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_SWIPE_RIGHT_ACTION
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_SWIPE_TO_CALL
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.SWIPE_ACTION_CALL
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.SWIPE_ACTION_CONTACT
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.SWIPE_ACTION_MESSAGE
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.SWIPE_ACTION_WHATSAPP

/** A single durable transaction; setup cannot finish with a partially changed preset. */
internal suspend fun saveInitialCallStylePreferences(dataStore: DataStore<Preferences>, swipe: Boolean): Preferences {
    PreferenceWriteBarrier.awaitPending()
    return dataStore.edit {
        it[intPreferencesKey(KEY_RECENT_TAP_ACTION)] = if (swipe) SWIPE_ACTION_CONTACT else SWIPE_ACTION_CALL
        it[intPreferencesKey(KEY_SWIPE_RIGHT_ACTION)] = if (swipe) SWIPE_ACTION_CALL else SWIPE_ACTION_MESSAGE
        it[intPreferencesKey(KEY_SWIPE_LEFT_ACTION)] = SWIPE_ACTION_WHATSAPP
        it[booleanPreferencesKey(KEY_SWIPE_TO_CALL)] = true
    }
}
