package dev.goodwy.rphone.controller.util

import androidx.datastore.preferences.core.*
import io.github.bootika.dotdialer.core.preferences.PreferenceBackupPolicy

/** Shared importer supports every DataStore scalar type, without bypassing protected keys. */
internal fun MutablePreferences.applyRestoredPreferences(values: Map<String, Any>, replacePortable: Boolean = false) {
    if (replacePortable) asMap().keys.filter { PreferenceBackupPolicy.isPortable(it.name) }
        .forEach { remove(it) }
    values.forEach { (key, value) ->
        if (!PreferenceBackupPolicy.isPortable(key)) return@forEach
        when (value) {
            is Boolean -> this[booleanPreferencesKey(key)] = value
            is Int -> this[intPreferencesKey(key)] = value
            is Long -> this[longPreferencesKey(key)] = value
            is Float -> { require(value.isFinite()); this[floatPreferencesKey(key)] = value }
            is Double -> { require(value.isFinite()); this[doublePreferencesKey(key)] = value }
            is String -> this[stringPreferencesKey(key)] = value
            is Set<*> -> {
                require(value.all { it is String })
                @Suppress("UNCHECKED_CAST")
                this[stringSetPreferencesKey(key)] = value as Set<String>
            }
            else -> error("Unsupported preference type")
        }
    }
}
