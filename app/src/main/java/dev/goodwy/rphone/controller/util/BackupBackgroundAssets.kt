package dev.goodwy.rphone.controller.util

import android.content.Context
import androidx.core.net.toUri
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Archive IDs, never source absolute paths, bind owned backgrounds to the destination sandbox. */
internal object BackupBackgroundAssets {
    private const val MARKER = "backup-background:"
    private val entryPattern = Regex("backgrounds/[0-9a-f-]{36}\\.jpg")

    fun isEntry(name: String) = entryPattern.matches(name)

    fun export(context: Context, preferences: Map<String, Any>, zip: ZipOutputStream): Map<String, Any> {
        val assets = linkedMapOf<String, String>()
        return preferences.mapValues { (key, value) ->
            if (!key.startsWith(PreferenceManager.CONTACT_BACKGROUND_PREFIX) || value !is String || value.isBlank()) value
            else {
                val source = ownedFile(context, value) ?: error("Selected call background is unavailable")
                val entry = assets.getOrPut(source.canonicalPath) {
                    val name = "backgrounds/${UUID.randomUUID()}.jpg"
                    require(source.length() in 1..BackupManager.MAX_ASSET_BYTES.toLong())
                    zip.putNextEntry(ZipEntry(name))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    name
                }
                MARKER + entry
            }
        }
    }

    fun restore(context: Context, preferences: Map<String, Any>, assets: Map<String, ByteArray>): Map<String, Any> {
        val pointers = preferences.filterKeys { it.startsWith(PreferenceManager.CONTACT_BACKGROUND_PREFIX) }
            .values.filterIsInstance<String>().filter { it.startsWith(MARKER) }.toSet()
        pointers.forEach { require(isEntry(it.removePrefix(MARKER)) && assets.containsKey(it.removePrefix(MARKER))) }
        pointers.forEach { pointer ->
            val bytes = assets.getValue(pointer.removePrefix(MARKER))
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096) { "Invalid background image" }
        }
        val replacements = pointers.associateWith { pointer ->
            val bytes = assets.getValue(pointer.removePrefix(MARKER))
            val directory = requireNotNull(CallBackgroundStorage.directory(context, "call_backgrounds"))
            val target = File(directory, "bg_${UUID.randomUUID()}.jpg")
            target.outputStream().use { it.write(bytes) }
            target.absolutePath
        }
        return preferences.mapValues { (key, value) ->
            if (key.startsWith(PreferenceManager.CONTACT_BACKGROUND_PREFIX) && value is String)
                replacements[value] ?: rebase(context, value) ?: value else value
        }
    }

    /** Android's file transfer preserves filenames, but profile/sandbox absolute paths can change. */
    fun rebase(context: Context, value: String): String? {
        if (!value.startsWith("/")) return null
        val name = File(value).name
        if (!Regex("bg_[0-9a-f-]+\\.jpg").matches(name)) return null
        return roots(context).map { File(it, name) }.firstOrNull { it.isFile && it.length() > 0 }?.absolutePath
    }

    private fun ownedFile(context: Context, value: String): File? {
        val source = if (value.startsWith("file://")) File(value.toUri().path.orEmpty()) else File(value)
        val canonical = source.canonicalFile
        return canonical.takeIf { it.isFile && roots(context).any { root -> it.parentFile == root.canonicalFile } }
    }

    private fun roots(context: Context): List<File> = listOf(
        File(context.filesDir, "call_backgrounds"),
        File(context.createDeviceProtectedStorageContext().filesDir, "call_backgrounds"))
}
