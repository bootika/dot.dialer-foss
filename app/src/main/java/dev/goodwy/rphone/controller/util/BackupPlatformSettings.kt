package dev.goodwy.rphone.controller.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import dev.goodwy.rphone.auto.AndroidAutoIntegration
import dev.goodwy.rphone.localization.AppLanguageCatalog
import org.json.JSONObject

/** Android-owned app language is not in DataStore. Other system settings remain Android's. */
internal object BackupPlatformSettings {
    fun capture(context: Context): String = JSONObject().apply {
        if (Build.VERSION.SDK_INT >= 33) put("application_locales",
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
    }.toString()

    fun validate(json: String?): String? {
        if (json == null) return null // older archive must not reset current language
        val raw = JSONObject(json)
        if (!raw.has("application_locales")) return null
        val tag = raw.get("application_locales")
        require(tag is String)
        val canonical = AppLanguageCatalog.canonicalTag(tag)
        require(tag.isBlank() || canonical.isNotBlank()) { "Unsupported app language" }
        return canonical
    }

    fun apply(context: Context, language: String?, preferences: PreferenceManager) {
        if (AndroidAutoIntegration.isAvailable()) AndroidAutoIntegration.setEnabled(context,
            preferences.getBoolean(PreferenceManager.KEY_ANDROID_AUTO_ENABLED, false))
        if (language != null && Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
        }
    }
}
