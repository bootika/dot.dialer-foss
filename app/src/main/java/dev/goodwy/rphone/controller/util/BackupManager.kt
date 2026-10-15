package dev.goodwy.rphone.controller.util

import android.content.Context
import android.util.Log
import io.github.bootika.dotdialer.core.preferences.BackupEntryPolicy
import io.github.bootika.dotdialer.core.preferences.PreferenceBackupPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private const val LOG_TAG = "BackupManager"
    internal const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 1024 * 1024
    internal const val MAX_ASSET_BYTES = 8 * 1024 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 1024

    fun getBackupDir(context: Context): File {
        val dir = File(requireNotNull(context.getExternalFilesDir(null)), "Backups")
        check(dir.isDirectory || dir.mkdirs())
        return dir
    }

    suspend fun createBackup(context: Context): File? = withContext(Dispatchers.IO) {
        var pendingFile: File? = null
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File.createTempFile("DotDialer_Backup_${timestamp}_", ".rphone", getBackupDir(context))
            pendingFile = backupFile

            ZipOutputStream(FileOutputStream(backupFile)).use { zip ->
                // 1. Backup Preferences (DataStore via PreferenceManager)
                val manager = PreferenceManager(context)
                val portablePreferences = manager.getPersistedPreferences()
                    .filterKeys(PreferenceBackupPolicy::isPortable)
                val assetPreferences = BackupBackgroundAssets.export(context, portablePreferences, zip)
                val prefsJson = BackupPreferencesJson.encode(assetPreferences)
                require(prefsJson.toByteArray(Charsets.UTF_8).size <= MAX_ENTRY_BYTES)
                zip.putNextEntry(ZipEntry("prefs.json"))
                zip.write(prefsJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("platform.json"))
                zip.write(BackupPlatformSettings.capture(context).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 2. Backup notes
                val notesDir = NoteManager.getNotesDir(context)
                requireNotNull(notesDir.listFiles()).filter { it.extension == "txt" } .forEach { noteFile ->
                    require(noteFile.length() <= MAX_ENTRY_BYTES)
                    zip.putNextEntry(ZipEntry("notes/${noteFile.name}"))
                    FileInputStream(noteFile).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            require(backupFile.length() <= MAX_BACKUP_BYTES)
            java.util.zip.ZipFile(backupFile).use { saved ->
                val entries = saved.entries().asSequence().toList()
                require(entries.size <= MAX_ARCHIVE_ENTRIES)
                require(entries.sumOf { it.size } <= MAX_BACKUP_BYTES)
            }
            backupFile
        } catch (error: Exception) {
            pendingFile?.delete()
            Log.e(LOG_TAG, "Unable to create backup", error)
            null
        }
    }

    suspend fun restoreBackup(context: Context, backupFile: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (!backupFile.isFile || backupFile.length() > MAX_BACKUP_BYTES) {
                    return@withContext false
                }
                var preferencesJson: String? = null
                var platformJson: String? = null
                val pendingNotes = linkedMapOf<String, ByteArray>()
                val pendingAssets = linkedMapOf<String, ByteArray>()
                var entryCount = 0
                var extractedBytes = 0

                ZipInputStream(FileInputStream(backupFile)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        entryCount += 1
                        if (entryCount > MAX_ARCHIVE_ENTRIES) return@withContext false
                        when {
                            entry.name == "prefs.json" -> {
                                if (preferencesJson != null) return@withContext false
                                val bytes = zip.readEntryBytes(MAX_ENTRY_BYTES)
                                extractedBytes += bytes.size
                                if (extractedBytes > MAX_BACKUP_BYTES) return@withContext false
                                preferencesJson = bytes.toString(Charsets.UTF_8)
                            }
                            entry.name == "platform.json" -> {
                                if (platformJson != null) return@withContext false
                                val bytes = zip.readEntryBytes(MAX_ENTRY_BYTES)
                                extractedBytes += bytes.size
                                platformJson = bytes.toString(Charsets.UTF_8)
                            }
                            BackupBackgroundAssets.isEntry(entry.name) -> {
                                if (pendingAssets.containsKey(entry.name)) return@withContext false
                                val bytes = zip.readEntryBytes(MAX_ASSET_BYTES)
                                extractedBytes += bytes.size
                                pendingAssets[entry.name] = bytes
                            }
                            entry.name.startsWith("notes/") -> {
                                val fileName = BackupEntryPolicy.safeNoteFileName(entry.name)
                                if (fileName != null) {
                                    if (pendingNotes.containsKey(fileName)) return@withContext false
                                    val bytes = zip.readEntryBytes(MAX_ENTRY_BYTES)
                                    extractedBytes += bytes.size
                                    if (extractedBytes > MAX_BACKUP_BYTES) return@withContext false
                                    pendingNotes[fileName] = bytes
                                } else extractedBytes += zip.readEntryBytes(MAX_ENTRY_BYTES).size
                            }
                            else -> extractedBytes += zip.readEntryBytes(MAX_ENTRY_BYTES).size
                        }
                        if (extractedBytes > MAX_BACKUP_BYTES) return@withContext false
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }

                val json = preferencesJson ?: return@withContext false
                val restoredPreferences = BackupPreferencesJson.decode(json)
                val complete = BackupPreferencesJson.isComplete(json)
                val language = BackupPlatformSettings.validate(platformJson)
                if (pendingNotes.isNotEmpty()) requireNotNull(context.getExternalFilesDir(null))
                val rebasedPreferences = BackupBackgroundAssets.restore(context, restoredPreferences, pendingAssets)
                val manager = PreferenceManager(context)
                manager.restoreAllPreferences(rebasedPreferences, replacePortable = complete)
                val notesDirectory = NoteManager.getNotesDir(context)
                pendingNotes.forEach { (fileName, bytes) ->
                    val noteFile = File(notesDirectory, fileName)
                    noteFile.parentFile?.mkdirs()
                    FileOutputStream(noteFile).use { it.write(bytes) }
                }
                BackupPlatformSettings.apply(context, language, manager)
                true
            } catch (error: Exception) {
                Log.e(LOG_TAG, "Unable to restore backup", error)
                false
            }
        }

    private fun ZipInputStream.readEntryBytes(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Backup entry exceeds the allowed size" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    fun listBackups(context: Context): List<File> =
        getBackupDir(context).listFiles()
            ?.filter { it.extension == "rphone" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
