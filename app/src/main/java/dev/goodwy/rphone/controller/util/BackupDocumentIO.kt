package dev.goodwy.rphone.controller.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Copies a Storage Access Framework document into private storage before validating it. */
suspend fun importBackupDocument(context: Context, uri: Uri): Boolean =
    withContext(Dispatchers.IO) {
        val temporaryFile = File.createTempFile("restore_", ".rphone", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext false
            input.use { source ->
                temporaryFile.outputStream().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= BackupManager.MAX_BACKUP_BYTES)
                        target.write(buffer, 0, read)
                    }
                }
            }
            BackupManager.restoreBackup(context, temporaryFile)
        } finally {
            temporaryFile.delete()
        }
    }

/** Writes a freshly generated backup to the user-selected document before reporting success. */
suspend fun exportBackupDocument(context: Context, uri: Uri): Boolean =
    withContext(Dispatchers.IO) {
        val backupFile = BackupManager.createBackup(context)
            ?: return@withContext false
        try {
            val output = context.contentResolver.openOutputStream(uri)
                ?: return@withContext false
            output.use { target ->
                backupFile.inputStream().use { source -> source.copyTo(target) }
            }
            true
        } finally {
            backupFile.delete()
        }
    }
