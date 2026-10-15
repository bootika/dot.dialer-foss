package dev.goodwy.rphone.backup

import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.edit
import dev.goodwy.rphone.auto.AndroidAutoIntegration
import dev.goodwy.rphone.controller.util.*
import io.github.bootika.dotdialer.core.preferences.DotDialerPreferenceDefaults
import io.github.bootika.dotdialer.core.preferences.PreferenceBackupPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class BackupPhoneDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "dot.dialer.backupqa.test") { "Never run on user's application" }
    }

    private fun fixture(): Map<String, Any> = buildMap {
        PreferenceManager::class.java.fields.filter { it.name.startsWith("KEY_") && it.type == String::class.java }
            .map { it.get(null) as String }.distinct().filter(PreferenceBackupPolicy::isPortable).forEachIndexed { index, key ->
                val value = DotDialerPreferenceDefaults.booleanFor(key)?.not()
                    ?: DotDialerPreferenceDefaults.intFor(key)?.let { if (it == 30) 100 else it }
                    ?: DotDialerPreferenceDefaults.floatFor(key)?.let { 1f }
                    ?: DotDialerPreferenceDefaults.stringFor(key)
                    ?: when (index % 5) { 0 -> true; 1 -> 7; 2 -> 0f; 3 -> 1L; else -> "Test Alexandru Butica" }
                put(key, value)
            }
        put(PreferenceManager.KEY_ANDROID_AUTO_ENABLED, true)
        put(PreferenceManager.KEY_THEME_MODE, "dark")
        put(PreferenceManager.KEY_ACCENT_COLOR, "blue")
        put(PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH, 100)
        put(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0f)
        put("favorite_number_contact-1", "+447700900123")
        put("favorite_email_contact-1", "alex@dotdesign.ro")
        put("favorite_sim_contact-1", "provider/Calls|SIM1")
        put("last_used_number_contact-1", "+447700900123")
        put("favorite_sim_number_447700900123", "provider/Calls|SIM1")
        put("high_res_call_photos_enabled", false)
        put("high_res_call_photos_account", "alex@dotdesign.ro")
        put("diagnostics_reporting_mode", "on_demand")
        put("sample_set", setOf("RO", "US"))
        put("sample_double", 0.125)
        put("sample_large_long", Long.MAX_VALUE)
    }

    @Test fun exportPhase() = runBlocking {
        val legacyScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        val legacy = File(context.filesDir, DevicePreferenceStorage.RELATIVE_PATH)
        val legacyStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = legacyScope, produceFile = { legacy })
        legacyStore.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("migration_marker")] = "Test Alexandru Butica" }
        legacyScope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        legacyScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
        val prefs = PreferenceManager(context)
        assertEquals("Test Alexandru Butica", prefs.getString("migration_marker", null))
        assertTrue(DevicePreferenceStorage.file(context).isFile)
        assertEquals(context.createDeviceProtectedStorageContext().filesDir.canonicalFile,
            DevicePreferenceStorage.file(context).parentFile!!.parentFile!!.canonicalFile)
        prefs.restoreAllPreferences(fixture(), replacePortable = true)
        prefs.setInt("queued_integer", 41)
        prefs.setInt("queued_integer", 42)
        val directory = requireNotNull(CallBackgroundStorage.directory(context, "call_backgrounds"))
        val image = File(directory, "bg_${java.util.UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF00B983.toInt()) }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        prefs.setString("contact_background_default", image.absolutePath)
        prefs.setString("contact_background_num_700900123", image.absolutePath)
        NoteManager.writeNote(context, "Test Alexandru Butica", "+447700900123", "Notă pentru restaurare.")
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ro")
        val backup = requireNotNull(BackupManager.createBackup(context))
        backup.copyTo(File(context.cacheDir, "restore-fixture.rphone"), overwrite = true)
        assertEquals(42, prefs.getPersistedPreferences()["queued_integer"])
        ZipFile(backup).use { zip ->
            val json = zip.getInputStream(zip.getEntry("prefs.json")).bufferedReader().readText()
            assertEquals(fixture().size + 3, BackupPreferencesJson.decode(json).size)
            assertTrue(BackupPreferencesJson.isComplete(json))
            assertEquals(1, zip.entries().asSequence().count { it.name.startsWith("backgrounds/") })
        }
    }

    /** Invoked in a fresh process AFTER adb clears ONLY the isolated QA package. */
    @Test fun restorePhase() = runBlocking {
        val prefs = PreferenceManager(context)
        assertTrue("Destination must be a clean QA install", prefs.getPersistedPreferences().isEmpty())
        val archive = File(context.cacheDir, "restore-fixture.rphone")
        assertTrue(archive.isFile)
        assertTrue(BackupManager.restoreBackup(context, archive))
        val actual = PreferenceManager(context).getPersistedPreferences()
        fixture().forEach { (key, expected) ->
            assertEquals("Restored $key", expected, actual[key])
            if (expected !is Set<*>) assertEquals("Typed $key", expected.javaClass, actual[key]!!.javaClass)
        }
        assertEquals(42, actual["queued_integer"])
        assertEquals("Notă pentru restaurare.", NoteManager.readNoteByPhone(context, "+447700900123"))
        assertEquals("ro", context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
        val background = prefs.getString("contact_background_default", null)!!
        assertTrue(File(background).isFile)
        assertEquals(background, actual["contact_background_num_700900123"])
        assertNotNull(android.graphics.BitmapFactory.decodeFile(background))
        assertTrue(AndroidAutoIntegration.isEnabled(context))
        assertFalse(actual.keys.any { !PreferenceBackupPolicy.isPortable(it) })
    }
}
