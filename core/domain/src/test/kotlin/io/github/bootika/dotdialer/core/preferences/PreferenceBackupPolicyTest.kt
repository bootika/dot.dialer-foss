package io.github.bootika.dotdialer.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceBackupPolicyTest {
    @Test
    fun `rejects entitlement flags and authentication secrets`() {
        listOf(
            DotDialerPreferenceDefaults.INSTALLATION_PROFILE_KEY,
            "is_pro_iap",
            "is_pro_sub",
            "is_pro_foss",
            "is_supporter",
            "app_lock_pin",
            "biometrics_pin",
            "biometrics_password",
            "authentication_enabled",
            "biometrics_type",
            "biometrics_app_lock",
            "biometrics_call_lock",
            "biometrics_call_lock_numbers",
            "app_lock_timeout",
        ).forEach { key -> assertFalse(PreferenceBackupPolicy.isPortable(key)) }
    }

    @Test
    fun `keeps ordinary user preferences portable`() {
        assertTrue(PreferenceBackupPolicy.isPortable("theme_mode"))
        assertTrue(PreferenceBackupPolicy.isPortable("tab_order"))
        assertTrue(PreferenceBackupPolicy.isPortable("direct_call_on_tap"))
    }

    @Test
    fun `accepts only flat text files under notes`() {
        assertEquals("Alexandru.txt", BackupEntryPolicy.safeNoteFileName("notes/Alexandru.txt"))
        assertEquals("CALL.TXT", BackupEntryPolicy.safeNoteFileName("notes/CALL.TXT"))
        assertNull(BackupEntryPolicy.safeNoteFileName("notes/"))
        assertNull(BackupEntryPolicy.safeNoteFileName("prefs.json"))
        assertNull(BackupEntryPolicy.safeNoteFileName("notes/nested/call.txt"))
        assertNull(BackupEntryPolicy.safeNoteFileName("notes/../prefs.txt"))
        assertNull(BackupEntryPolicy.safeNoteFileName("notes/..\\prefs.txt"))
        assertNull(BackupEntryPolicy.safeNoteFileName("notes/script.kt"))
    }
}
