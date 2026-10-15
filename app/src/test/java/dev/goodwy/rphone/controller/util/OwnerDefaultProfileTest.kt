package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Independent frozen, reusable subset of the actual 2026-10-08 device snapshot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class OwnerDefaultProfileTest {
    private lateinit var context: Context
    private lateinit var singleton: java.lang.reflect.Field
    private var previousStore: Any? = null

    @Before fun isolateDataStore() {
        val base = RuntimeEnvironment.getApplication()
        val directory = File(base.cacheDir, "owner-defaults-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun createDeviceProtectedStorageContext(): Context = this
            override fun getFilesDir(): File = File(directory, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir(): File = File(directory, "no_backup").apply { mkdirs() }
            override fun moveSharedPreferencesFrom(sourceContext: Context, name: String): Boolean = false
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("${directory.name}-$name", mode)
        }
        singleton = Class.forName("dev.goodwy.rphone.controller.util.SharedPreferenceDataStoreKt")
            .getDeclaredField("_sharedDataStore").apply { isAccessible = true }
        previousStore = singleton.get(null)
        singleton.set(null, null)
    }

    @After fun restoreDataStoreSingleton() { singleton.set(null, previousStore) }

    @Test fun emptyPreferencesMatchOwnerProfileWithoutPersistingPersonalData() = runBlocking {
        val prefs = PreferenceManager(context)
        prefs.restoreAllPreferences(emptyMap(), replacePortable = true)
        val source = requireNotNull(javaClass.getResourceAsStream("/owner_default_profile_20261008.json"))
            .bufferedReader().use { it.readText() }
        val rows = JSONArray(source)
        assertEquals(17, rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val key = row.getString("Key")
            when (row.getString("Type")) {
                "Boolean" -> assertEquals(key, row.getBoolean("Value"), prefs.getBoolean(key, !row.getBoolean("Value")))
                "Int" -> assertEquals(key, row.getInt("Value"), prefs.getInt(key, -1))
                "String" -> assertEquals(key, row.getString("Value"), prefs.getString(key, "fixture fallback"))
                "Float" -> assertEquals(key, row.getDouble("Value").toFloat(), prefs.getFloat(key, -1f), 0.001f)
                else -> fail("Unreviewed reusable preference type")
            }
        }
        assertEquals(70f, AmbientDropletPreference.opacityPercent(prefs.getInt("ambient_droplet_brightness", -1)), 0.001f)
        assertEquals(listOf("Nu pot vorbi, scrie-mi pe WhatsApp", "Nu pot vorbi, te sun eu mai tarziu",
            "Nu pot vorbi, suna-ma mai tarziu"), prefs.getQuickResponses())
        assertEquals(prefs.getQuickResponses(), prefs.defaultQuickResponses())
        assertFalse(prefs.getBoolean("high_res_call_photos_enabled", false))
        assertNull(prefs.getString("high_res_call_photos_account", null))
        assertNull(prefs.getString("default_calling_account", null))
        assertFalse(prefs.getBoolean("is_pro_iap", false))
        assertFalse(prefs.getBoolean("onboarding_shown", false))
        assertTrue(prefs.getPersistedPreferences().isEmpty())
    }

    @Test fun savedChoicesRemainAuthoritativeAcrossReloadAndPortableRestore() = runBlocking {
        val prefs = PreferenceManager(context)
        val choices = mapOf("theme_mode" to "dark", "call_screen_appearance" to "circular_avatar",
            "ambient_droplet_brightness" to 60, "recent_history_max_entries" to 500,
            "ignore_system_font_scale" to false, "show_picture" to false)
        prefs.restoreAllPreferences(choices, replacePortable = true)
        val reloaded = PreferenceManager(context)
        assertEquals(choices, reloaded.getPersistedPreferences())
        assertEquals("dark", reloaded.getString("theme_mode", null))
        assertEquals("circular_avatar", reloaded.getString("call_screen_appearance", null))
        assertEquals(60, reloaded.getInt("ambient_droplet_brightness", -1))
        assertEquals(500, reloaded.getInt("recent_history_max_entries", -1))
        assertFalse(reloaded.getBoolean("ignore_system_font_scale", true))
        assertFalse(reloaded.getBoolean("show_picture", true))
    }

    @Test fun legacySharedPreferencesKeepExplicitAndImplicitChoicesWithoutOwnerPreset() = runBlocking {
        context.getSharedPreferences("rill_prefs", Context.MODE_PRIVATE).edit()
            .putString("theme_mode", "dark").putBoolean("show_picture", false).commit()
        val prefs = PreferenceManager(context)
        assertEquals("dark", prefs.getString("theme_mode", "light"))
        assertFalse(prefs.getBoolean("show_picture", true))
        assertEquals("circular_avatar", prefs.getString("call_screen_appearance", "full_photo"))
        assertFalse(prefs.getBoolean("ignore_system_font_scale", true))
        assertTrue(prefs.getBoolean("frosted_call_background", false))
        assertEquals(100, prefs.getInt("ambient_droplet_brightness", 140))
        assertEquals(500, prefs.getInt("recent_history_max_entries", 1000))
        assertEquals(4, prefs.getQuickResponses().size)
        assertEquals(mapOf("theme_mode" to "dark", "show_picture" to false), prefs.getPersistedPreferences())
    }

    @Suppress("DEPRECATION")
    @Test fun upgradeWithNoSavedKeysKeepsLegacyBehaviorRatherThanApplyingOwnerProfile() = runBlocking {
        val info = context.packageManager.getPackageInfo(context.packageName, 0).apply {
            firstInstallTime = 1000
            lastUpdateTime = 2000
        }
        org.robolectric.Shadows.shadowOf(context.packageManager).installPackage(info)
        assertFalse(isFreshDefaultsInstallation(context, existingFile = false, legacyPreferences = false))
        val prefs = PreferenceManager(context)
        assertEquals("auto", prefs.getString("theme_mode", "light"))
        assertEquals("circular_avatar", prefs.getString("call_screen_appearance", "full_photo"))
        assertFalse(prefs.getBoolean("ignore_system_font_scale", true))
        assertTrue(prefs.getBoolean("frosted_call_background", false))
        assertEquals(100, prefs.getInt("ambient_droplet_brightness", 140))
        assertEquals(500, prefs.getInt("recent_history_max_entries", 1000))
        assertEquals(4, prefs.getQuickResponses().size)
        assertTrue(prefs.getPersistedPreferences().isEmpty())
    }
}
