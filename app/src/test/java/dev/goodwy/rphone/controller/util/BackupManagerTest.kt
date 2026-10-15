package dev.goodwy.rphone.controller.util

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackupManagerTest {

    private val context = RuntimeEnvironment.getApplication()

    @org.junit.Before fun resetPortableFixture() = kotlinx.coroutines.runBlocking<Unit> {
        PreferenceManager(context).restoreAllPreferences(emptyMap(), replacePortable = true)
    }

    @Test
    fun `empty archive is rejected instead of reporting a false success`() = runTest {
        val backup = createArchive(emptyMap())

        assertFalse(BackupManager.restoreBackup(context, backup))
    }

    @Test
    fun `malformed preferences are rejected before anything is restored`() = runTest {
        val backup = createArchive(mapOf("prefs.json" to "not-json".toByteArray()))

        assertFalse(BackupManager.restoreBackup(context, backup))
    }

    @Test
    fun `valid preferences are persisted before restore reports success`() = runTest {
        val marker = "Test Alexandru Butica"
        val json = """{"data":{"backup_test_marker":"$marker"},"meta":{}}"""
        val backup = createArchive(mapOf("prefs.json" to json.toByteArray()))

        assertTrue(BackupManager.restoreBackup(context, backup))
        assertEquals(marker, PreferenceManager(context).getString("backup_test_marker", null))
    }

    private fun createArchive(entries: Map<String, ByteArray>): File {
        val file = File.createTempFile("dot_dialer_backup_", ".rphone", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `created backup round trips portable preferences and notes without exporting licenses`() = runTest {
        val preferences = PreferenceManager(context)
        preferences.restoreAllPreferences(mapOf("backup_test_marker" to "Test Alexandru Butica", "is_pro_foss" to true))
        val number = "07700900123"
        val note = "Test pentru restaurarea unei note."
        NoteManager.writeNote(context, "Test Alexandru Butica", number, note)
        val archive = requireNotNull(BackupManager.createBackup(context))
        try {
            ZipFile(archive).use { zip ->
                val saved = zip.getInputStream(zip.getEntry("prefs.json")).bufferedReader().use { it.readText() }
                assertTrue(saved.contains("Test Alexandru Butica"))
                assertFalse(saved.contains("is_pro_foss"))
                assertTrue(zip.entries().asSequence().any { it.name.startsWith("notes/") })
            }
            preferences.restoreAllPreferences(mapOf("backup_test_marker" to "", "is_pro_foss" to false))
            NoteManager.deleteNote(context, number)
            assertEquals("", NoteManager.readNoteByPhone(context, number))
            assertTrue(BackupManager.restoreBackup(context, archive))
            // Restore uses another manager; verify committed data before reading this cache.
            assertEquals("Test Alexandru Butica", preferences.getPersistedPreferences()["backup_test_marker"])
            assertEquals("Test Alexandru Butica", preferences.getString("backup_test_marker", null))
            assertEquals(note, NoteManager.readNoteByPhone(context, number))
            assertFalse(preferences.getBoolean("is_pro_foss", false))
        } finally {
            archive.delete()
            NoteManager.deleteNote(context, number)
        }
    }
}
