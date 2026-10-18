package dev.goodwy.rphone.controller.util

import android.app.Application
import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.datastore.preferences.core.Preferences
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion as P
import dev.goodwy.rphone.view.screen.settings.components.settingsOptions
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackupCompletenessTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @org.junit.After fun clearPortableFixture() = runBlocking<Unit> {
        PreferenceManager(context).restoreAllPreferences(emptyMap(), replacePortable = true)
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
    }
    private data class Field(val id: String, val key: String, val values: List<Any>)
    private fun field(id: String, key: String, vararg values: Any) = Field(id, key, values.toList())
    private val fields = listOf(
        field("K12", P.KEY_SELECTED_TEXT_DIALER, false, true),
        field("K11", P.KEY_CALL_MESSAGE_CHANNEL, 0, 1, 2),
        field("T11", WhatsAppAvatarSyncPreferences.ENABLED, false, true),
        field("T12", WhatsAppAvatarSyncPreferences.NORMALIZE_NUMBERS, false, true),
        field("T13", WhatsAppAvatarSyncPreferences.EXISTING_PHOTO_POLICY, "ask", "always", "never"),
        field("I02", P.KEY_THEME_MODE, "auto", "light", "dark"),
        field("I03", P.KEY_ACCENT_COLOR, "jade", "blue", "violet", "coral"),
        field("I06", P.KEY_CONTACT_NAME_SIZE_SP, 16, 18, 24),
        field("I07", P.KEY_EXPANDED_LIST_DETAILS, false, true),
        field("I08", P.KEY_INTERFACE_SCALE_PERCENT, 100, 150),
        field("I09", P.KEY_SCROLL_ANIMATION, false, true),
        field("I11", P.KEY_HIGH_CONTRAST_TEXT, false, true),
        field("I12", P.KEY_VOICE_SEARCH_ENABLED, false, true),
        field("I05", P.KEY_CONTACT_AVATAR_SIZE, "small", "medium", "large"),
        field("I10", P.KEY_IGNORE_SYSTEM_FONT_SCALE, false, true),
        field("A01", P.KEY_SHOW_FIRST_LETTER, false, true),
        field("A03", P.KEY_SHOW_PICTURE, false, true),
        field("A04", P.KEY_HIDE_AVATAR_WITH_BACKGROUND, false, true),
        field("A05", P.KEY_LG_CALL_SCREEN, false, true),
        field("A06", P.KEY_LG_DIALPAD, false, true),
        field("A07", P.KEY_BLUR_CALL_SCREEN, false, true),
        field("A09", P.KEY_DIALPAD_GLASS_STRENGTH, 0, 30, 100),
        field("H01", P.KEY_RECENT_TAP_ACTION, 0, 1, 2, 4, 5, 7),
        field("H02", P.KEY_SWIPE_RIGHT_ACTION, 0, 1, 2, 4, 5, 7),
        field("H03", P.KEY_SWIPE_LEFT_ACTION, 0, 1, 2, 4, 5, 7),
        field("H04", P.KEY_SHOW_RECENTS_FAVORITES, false, true),
        field("H05", P.KEY_CLEAR_SEARCH_AFTER_CALL, false, true),
        field("H06", P.KEY_REPEAT_CONTACTS_IN_SEARCH_RESULTS, false, true),
        field("H07", P.KEY_SEARCH_DIACRITIC_NORMALIZATION, false, true),
        field("H08", P.KEY_RECENT_HISTORY_PERIOD_DAYS, 0, 30, 90, 180, 365),
        field("H09", P.KEY_RECENT_HISTORY_MAX_ENTRIES, 100, 250, 500, 1000),
        field("H10", P.KEY_SHOW_ALL_RECENT_CALLS, false, true),
        field("H11", HOME_QUICK_ACTION_GESTURE, false, true),
        field("C01", P.KEY_CALL_SCREEN_APPEARANCE, "circular_avatar", "full_photo"),
        field("C09", SmallCallPhotoPreference.KEY, false, true),
        field("C02", P.KEY_FROSTED_CALL_BACKGROUND, false, true),
        field("C03", AmbientDropletPreference.ENABLED_KEY, false, true),
        field("C04", AmbientDropletPreference.BRIGHTNESS_KEY, 10, 100, 200),
        field("C05", P.KEY_INCOMING_CALL_UI_MODE, 0, 1, 2, 3, 4, 10),
        field("C06", P.KEY_INCOMING_ANSWER_PLACEMENT, "left_top", "right_bottom"),
        field("C08", P.KEY_HANGUP_WIDTH, 0.1f, 0.4f, 1f),
        field("K01", P.KEY_DEFAULT_CALLING_ACCOUNT, "provider/Calls|SIM1", "provider/Calls|SIM2"),
        field("K02", P.KEY_OUTGOING_NUMBER_NORMALIZATION, false, true),
        field("K03", P.KEY_OUTGOING_NUMBER_REGION_ISO, "", "RO", "US", "GB"),
        field("K04", P.KEY_ANDROID_AUTO_ENABLED, false, true),
        field("K05", P.KEY_QUICK_RESPONSES, "Test Alexandru Butica|||Revin cu un apel.", "Sun mai târziu."),
        field("K06", P.KEY_PROXIMITY_SENSOR, false, true),
        field("K07", P.KEY_POCKET_MODE, false, true),
        field("K08", P.KEY_FLOATING_CALL, false, true),
        field("K09", P.KEY_ALWAYS_FULLSCREEN_CALLS, false, true),
        field("V02", P.KEY_DTMF_TONE, false, true),
        field("V03", P.KEY_VIBRATE_ON_ANSWER, false, true),
        field("V04", P.KEY_VIBRATE_ON_HANGUP, false, true),
        field("V05", P.KEY_APP_HAPTICS, false, true),
        field("V05", P.KEY_HAPTICS_STRENGTH, "light", "medium", "strong", "custom"),
        field("V06", P.KEY_HAPTICS_CUSTOM_INTENSITY, 0f, 0.5f, 1f),
        field("V07", P.KEY_SCROLL_HAPTICS, false, true),
        field("V08", P.KEY_SCROLL_CM_PER_HAPTIC, 0.5f, 1.5f, 5f),
        field("V09", P.KEY_SCROLL_HAPTIC_STRENGTH, 1, 60, 255),
        field("T01", P.KEY_VISIBLE_ACCOUNTS, "", "com.google:alex@dotdesign.ro"),
        field("T02", P.KEY_CONTACT_SORT_ORDER, 0, 1),
        field("T03", P.KEY_CONTACT_DISPLAY_ORDER, 0, 1),
        field("T09", "high_res_call_photos_enabled", false, true),
        field("B01", P.KEY_BLOCK_LOG_VISIBILITY, 0, 1, 2),
        field("O01", "diagnostics_reporting_mode", "automatic", "on_demand"),
    )
    private val outsideDataStore = mapOf(
        "O02" to "Test-only temporary log level in noBackupFilesDir; expires24h, intentionally not cloned to another phone",
        "I01" to "platform.json / LocaleManager; Android-owned during phone transfer",
        "C07" to "Owned background bytes plus rebased default pointer",
        "K10" to "Android calling-account administration, not an app parameter",
        "V01" to "Android sound settings, not an app parameter",
        "T10" to "Scan action; no parameter or OAuth token to clone",
        "T14" to "Per-account noBackup opt-in; explicit consent on destination device",
        "T15" to "Selected account aliases T09 ACCOUNT field; grants/provider data are external",
        "T16" to "Per-account noBackup opt-in; official-site lookup consent is not cloned",
        "T17" to "Per-account noBackup automatic-update choice; notification access is granted separately on the destination device",
        "T18" to "Legacy hidden per-account size flag, ignored by no-downgrade automation; not cloned",
        "T19" to "Per-account noBackup notification photo rule (Newest/Optimal); explicit source access remains per device",
        "B02" to "Authentication excluded from editable ZIP; reconfigure on new device",
        "B03" to "Authentication excluded from editable ZIP; reconfigure on new device",
        "B04" to "Authentication excluded from editable ZIP; reconfigure on new device",
        "B05" to "Authentication excluded from editable ZIP; reconfigure on new device",
        "B06" to "Authentication excluded from editable ZIP; reconfigure on new device",
        "B07" to "Authentication excluded from editable ZIP; reconfigure on new device",
        "F01" to "Entitlement deliberately excluded; store/provider owns it",
        "T04" to "Stored calling-account key; destination Telecom validates availability",
        "T05" to "Dynamic favorite number/email/last-used keys; provider ID must match",
        "T06" to "Order preference portable; starred contact belongs to Contacts Provider",
        "T07" to "Contact ringtone belongs to Contacts Provider / Android media",
        "T08" to "Owned background bytes plus contact/number pointers; provider ID may change",
        "I04" to "Retired; legacy key preserved but not effective",
        "A08" to "Retired; legacy key preserved but not effective",
        "B08" to "Dormant legacy control",
    )

    @Test fun `every documented option has an explicit backup owner and path`() {
        assertEquals(settingsOptions.map { it.id }.toSet(), fields.map { it.id }.toSet() + outsideDataStore.keys)
        assertTrue((fields.map { it.id }.toSet() intersect outsideDataStore.keys).isEmpty())
        val report = JSONObject().put("options", org.json.JSONArray(settingsOptions.map { option ->
            JSONObject().put("id", option.id).put("keys", org.json.JSONArray(fields.filter { it.id == option.id }.map { it.key }))
                .put("mechanism", outsideDataStore[option.id] ?: "Typed complete DataStore snapshot / Android device_file")
        }))
        val directory = File(System.getProperty("user.dir"), "build/reports/backup-audit").apply { mkdirs() }
        File(directory, "${dev.goodwy.rphone.BuildConfig.FLAVOR}-settings-coverage.json").writeText(report.toString(2))
    }

    @Test fun `every configurable field round trips every listed value with exact type`() = runBlocking<Unit> {
        val manager = PreferenceManager(context)
        for (pass in 0 until fields.maxOf { it.values.size }) {
            val source = fields.associate { it.key to it.values[pass % it.values.size] }
            manager.restoreAllPreferences(source, replacePortable = true)
            val archive = requireNotNull(BackupManager.createBackup(context))
            try {
                manager.restoreAllPreferences(emptyMap(), replacePortable = true)
                assertTrue(BackupManager.restoreBackup(context, archive))
                val actual = PreferenceManager(context).getPersistedPreferences()
                source.forEach { (key, expected) ->
                    assertEquals("Value $key pass$pass", expected, actual[key])
                    assertEquals("Type $key pass$pass", expected.javaClass, actual[key]!!.javaClass)
                }
            } finally { archive.delete() }
        }
    }

    @Test fun `new complete snapshots preserve absent defaults and old archives still merge`() = runBlocking<Unit> {
        val manager = PreferenceManager(context)
        manager.restoreAllPreferences(emptyMap(), replacePortable = true)
        val archive = requireNotNull(BackupManager.createBackup(context))
        try {
            manager.setString(P.KEY_ACCENT_COLOR, "coral")
            manager.setBoolean(P.KEY_LG_DIALPAD, true)
            manager.setInt(P.KEY_DIALPAD_GLASS_STRENGTH, 100)
            assertTrue(BackupManager.restoreBackup(context, archive))
            val actual = manager.getPersistedPreferences()
            assertFalse(actual.containsKey(P.KEY_ACCENT_COLOR))
            assertFalse(actual.containsKey(P.KEY_DIALPAD_GLASS_STRENGTH))
            assertEquals(30, manager.dialpadGlassStrengthPercent())
            manager.restoreAllPreferences(mapOf(P.KEY_ACCENT_COLOR to "blue"))
            val legacy = File.createTempFile("legacy_", ".rphone", context.cacheDir)
            java.util.zip.ZipOutputStream(legacy.outputStream()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("prefs.json"))
                zip.write("""{"theme_mode":"dark"}""".toByteArray()); zip.closeEntry()
            }
            assertTrue(BackupManager.restoreBackup(context, legacy))
            assertEquals("blue", manager.getString(P.KEY_ACCENT_COLOR, null))
            legacy.delete()
        } finally { archive.delete() }
    }

    @Test fun `backup fences immediate writes from multiple managers and preserves all DataStore types`() = runBlocking<Unit> {
        val manager = PreferenceManager(context)
        for (i in 0..80) PreferenceManager(context).setInt(P.KEY_INTERFACE_SCALE_PERCENT, 100 + i)
        manager.setFloat(P.KEY_HAPTICS_CUSTOM_INTENSITY, 0f)
        val extra = mapOf("integer" to Int.MIN_VALUE, "small_long" to 1L, "large_long" to Long.MAX_VALUE,
            "zero_float" to 0f, "whole_float" to 1f, "double" to 0.125, "set" to setOf("RO", "US"))
        manager.restoreAllPreferences(extra)
        val archive = requireNotNull(BackupManager.createBackup(context))
        try {
            manager.restoreAllPreferences(emptyMap(), replacePortable = true)
            assertTrue(BackupManager.restoreBackup(context, archive))
            val actual = manager.getPersistedPreferences()
            assertEquals(180, actual[P.KEY_INTERFACE_SCALE_PERCENT])
            extra.forEach { (key, expected) ->
                assertEquals(expected, actual[key])
                if (expected !is Set<*>) assertEquals(expected.javaClass, actual[key]!!.javaClass)
            }
            assertEquals(0f, actual[P.KEY_HAPTICS_CUSTOM_INTENSITY])
        } finally { archive.delete() }
    }

    @Test fun `language and owned image restore after source asset is no longer available`() = runBlocking<Unit> {
        val manager = PreferenceManager(context)
        manager.restoreAllPreferences(emptyMap(), replacePortable = true)
        val locale = context.getSystemService(LocaleManager::class.java)
        locale.applicationLocales = LocaleList.forLanguageTags("ro")
        val directory = requireNotNull(CallBackgroundStorage.directory(context, "call_backgrounds"))
        val original = File(directory, "bg_${java.util.UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val bytes = original.readBytes()
        manager.restoreAllPreferences(mapOf("contact_background_default" to original.absolutePath,
            "contact_background_num_7700900123" to original.absolutePath))
        val archive = requireNotNull(BackupManager.createBackup(context))
        try {
            ZipFile(archive).use { zip -> assertEquals(1, zip.entries().asSequence().count { it.name.startsWith("backgrounds/") }) }
            original.delete()
            manager.restoreAllPreferences(emptyMap(), replacePortable = true)
            locale.applicationLocales = LocaleList.forLanguageTags("en")
            assertTrue(BackupManager.restoreBackup(context, archive))
            assertEquals("ro", locale.applicationLocales.toLanguageTags())
            val persisted = manager.getPersistedPreferences()
            val restored = requireNotNull(persisted["contact_background_default"] as? String)
            assertNotEquals(original.absolutePath, restored)
            assertArrayEquals(bytes, File(restored).readBytes())
            assertEquals(restored, persisted["contact_background_num_7700900123"])
            File(restored).delete()
        } finally { archive.delete(); locale.applicationLocales = LocaleList.getEmptyLocaleList() }
    }
}
