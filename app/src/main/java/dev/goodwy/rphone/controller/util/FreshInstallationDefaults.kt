package dev.goodwy.rphone.controller.util

import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.bootika.dotdialer.core.preferences.DotDialerPreferenceDefaults

/** Fresh-package evidence is captured before DataStore creates its file. Unknown evidence never seeds. */
@Suppress("DEPRECATION")
internal fun isFreshDefaultsInstallation(context: Context, existingFile: Boolean, legacyPreferences: Boolean): Boolean {
    if (existingFile || legacyPreferences) return false
    return try {
        val info = if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(context.packageName,
            PackageManager.PackageInfoFlags.of(0)) else context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    } catch (failure: PackageManager.NameNotFoundException) { false }
}

internal class FreshInstallationDefaults(private val freshInstallation: Boolean) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = freshInstallation && currentData.asMap().isEmpty()
    override suspend fun migrate(currentData: Preferences): Preferences {
        if (!shouldMigrate(currentData)) return currentData
        return currentData.toMutablePreferences().apply {
            applyRestoredPreferences(DotDialerPreferenceDefaults.freshInstallPreset())
            this[intPreferencesKey(DotDialerPreferenceDefaults.INSTALLATION_PROFILE_KEY)] =
                DotDialerPreferenceDefaults.OWNER_PROFILE_REVISION
        }
    }
    override suspend fun cleanUp() = Unit
}
