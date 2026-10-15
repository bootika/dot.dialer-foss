package dev.goodwy.rphone.controller.util

import androidx.datastore.preferences.core.*
import io.github.bootika.dotdialer.core.preferences.DotDialerPreferenceDefaults as D
import io.github.bootika.dotdialer.core.preferences.LegacyPreferenceDefaults as L
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FreshInstallationDefaultsTest {
    @Test fun existingEmptyAndSparseStoresAreNeverSeeded() = runBlocking {
        val migration = FreshInstallationDefaults(false)
        for (old in listOf(emptyPreferences(), preferencesOf(stringPreferencesKey("theme_mode") to "dark",
            booleanPreferencesKey("show_picture") to false))) {
            assertFalse(migration.shouldMigrate(old))
            assertSame(old, migration.migrate(old))
        }
        assertEquals("auto", L.stringFor("theme_mode"))
        assertEquals("circular_avatar", L.stringFor("call_screen_appearance"))
        assertEquals(true, L.booleanFor("frosted_call_background"))
        assertEquals(false, L.booleanFor("ignore_system_font_scale"))
        assertEquals(100, L.intFor("ambient_droplet_brightness"))
        assertEquals(500, L.intFor("recent_history_max_entries"))
        assertNull(L.stringFor("custom_quick_responses"))
    }

    @Test fun genuinelyFreshStoreGetsExplicitPresetOnceAndKeepsLaterChoices() = runBlocking {
        val migration = FreshInstallationDefaults(true)
        val initial = migration.migrate(emptyPreferences())
        assertEquals(18, initial.asMap().size)
        assertEquals(2, initial[intPreferencesKey(D.INSTALLATION_PROFILE_KEY)])
        assertEquals("light", initial[stringPreferencesKey("theme_mode")])
        assertEquals("full_photo", initial[stringPreferencesKey("call_screen_appearance")])
        val edited = initial.toMutablePreferences().apply { this[stringPreferencesKey("theme_mode")] = "dark" }
        assertFalse(migration.shouldMigrate(edited))
        assertSame(edited, migration.migrate(edited))
        assertEquals("dark", edited[stringPreferencesKey("theme_mode")])
    }

    @Test fun restoredSettingsAreNotTreatedAsAnEmptyInstallationAndProfileCannotBeImported() = runBlocking {
        val migration = FreshInstallationDefaults(true)
        val restored = preferencesOf(stringPreferencesKey("theme_mode") to "dark")
        assertFalse(migration.shouldMigrate(restored))
        assertSame(restored, migration.migrate(restored))
        val fresh = migration.migrate(emptyPreferences()).toMutablePreferences()
        fresh.applyRestoredPreferences(mapOf("theme_mode" to "dark", D.INSTALLATION_PROFILE_KEY to 1), replacePortable = true)
        assertEquals(2, fresh[intPreferencesKey(D.INSTALLATION_PROFILE_KEY)])
        assertEquals("dark", fresh[stringPreferencesKey("theme_mode")])
    }
}
