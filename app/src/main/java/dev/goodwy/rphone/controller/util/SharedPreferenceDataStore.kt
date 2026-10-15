package dev.goodwy.rphone.controller.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
private var _sharedDataStore: DataStore<Preferences>? = null
private val dataStoreLock = Any()

internal fun getSharedDataStore(context: Context): DataStore<Preferences> {
    return _sharedDataStore ?: synchronized(dataStoreLock) {
        _sharedDataStore ?: run {
            val appContext = context.applicationContext
            val deviceContext = appContext.createDeviceProtectedStorageContext()
            try {
                deviceContext.moveSharedPreferencesFrom(appContext, "rill_prefs")
            } catch (error: Exception) { android.util.Log.w("PreferenceManager", "Unable to move legacy preferences to device storage", error) }

            val settingsFile = DevicePreferenceStorage.file(appContext)
            val freshInstallation = isFreshDefaultsInstallation(appContext, settingsFile.isFile,
                deviceContext.getSharedPreferences("rill_prefs", Context.MODE_PRIVATE).all.isNotEmpty())
            PreferenceDataStoreFactory.create(
                migrations = listOf(
                    SharedPreferencesMigration(
                        context = deviceContext,
                        sharedPreferencesName = "rill_prefs"
                    ),
                    FreshInstallationDefaults(freshInstallation),
                ),
                produceFile = { settingsFile },
                scope = dataStoreScope
            )
        }.also { _sharedDataStore = it }
    }
}

