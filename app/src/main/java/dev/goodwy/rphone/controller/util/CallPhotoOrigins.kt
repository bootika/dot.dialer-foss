package dev.goodwy.rphone.controller.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.ContactsContract as C
import android.util.AtomicFile
import androidx.core.graphics.get
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Device-local photo provenance. No Notes/custom contact fields or cloud writes. */
internal class CallPhotoOrigins(private val context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "call-photo-origins.json"))
    private val lock = locks.computeIfAbsent(file.baseFile.absolutePath) { Any() }
    private fun read(): JSONObject {
        val stamp = file.baseFile.lastModified() to file.baseFile.length()
        val cached = snapshots[file.baseFile.absolutePath]
        if (cached?.first == stamp) return cached.second
        val json = if (!file.baseFile.isFile) JSONObject() else {
            check(file.baseFile.length() <= 1024 * 1024)
            JSONObject(file.openRead().bufferedReader().use { it.readText() })
        }
        snapshots[file.baseFile.absolutePath] = stamp to json
        return json
    }
    private fun signature(cursor: android.database.Cursor): String? {
        val keys = listOf(C.Data._ID, C.Data.DATA_VERSION, C.CommonDataKinds.Photo.PHOTO_FILE_ID)
        val values = keys.map { key ->
            val index = cursor.getColumnIndex(key)
            if (index < 0 || cursor.isNull(index)) return null
            cursor.getLong(index).takeIf { if (key == C.Data.DATA_VERSION) it >= 0 else it > 0 } ?: return null
        }
        return values.joinToString(":")
    }
    private val columns get() = arrayOf(C.Data._ID, C.Data.DATA_VERSION, C.CommonDataKinds.Photo.PHOTO_FILE_ID)
    /** Called only after a successful provider write or independently confirmed existing proposal. */
    fun rememberApplied(rawId: Long, approved: ByteArray, source: String) {
        if (source !in setOf("WhatsApp", "WhatsApp Business")) return
        runCatching {
            context.contentResolver.query(C.Data.CONTENT_URI, columns,
                "${C.Data.RAW_CONTACT_ID} = ? AND ${C.Data.MIMETYPE} = ?",
                arrayOf(rawId.toString(), C.CommonDataKinds.Photo.CONTENT_ITEM_TYPE), null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val key = signature(cursor) ?: continue
                    if (synchronized(lock) { read().optJSONObject(key)?.optInt("source") == 1 }) continue
                    val photoId = key.substringAfterLast(':').toLong()
                    val uri = android.content.ContentUris.withAppendedId(C.DisplayPhoto.CONTENT_URI, photoId)
                    val stored = context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = readBoundedPhoto(input)
                        bytes.takeIf { it.size <= 8 * 1024 * 1024 }
                    } ?: continue
                    if (!contactPhotoReadbackMatches(approved, stored)) continue
                    // A concurrent replacement invalidates the key through DATA_VERSION/PHOTO_FILE_ID.
                    synchronized(lock) {
                        val json = read()
                        json.put(key, JSONObject().put("source", 1).put("cut_corners", notificationAvatarHasCutCorners(approved))
                            .put("at", System.currentTimeMillis()))
                        if (json.length() > 4000) json.keys().asSequence().minByOrNull { json.getJSONObject(it).optLong("at") }?.let(json::remove)
                        val output = file.startWrite()
                        try { output.write(json.toString().encodeToByteArray()); file.finishWrite(output)
                            snapshots[file.baseFile.absolutePath] = (file.baseFile.lastModified() to file.baseFile.length()) to json
                            changes.update { it + 1 } }
                        catch (error: Exception) { file.failWrite(output); throw error }
                    }
                }
            }
        }.onFailure { android.util.Log.w("CallPhotoOrigins", "Photo origin unavailable: " + it.javaClass.simpleName) }
    }
    fun isWhatsApp(photo: Uri): Boolean {
        if (UnsavedNumberAvatars.ownedUri(context, photo.toString())) return File(requireNotNull(photo.path)).isFile
        if (photo.authority != C.AUTHORITY || photo.pathSegments.size != 2 || photo.pathSegments.first() != "display_photo") return false
        val id = photo.lastPathSegment?.toLongOrNull() ?: return false
        val json = synchronized(lock) { read() }
        if (json.length() == 0) return false
        return context.contentResolver.query(C.Data.CONTENT_URI, columns,
            "${C.CommonDataKinds.Photo.PHOTO_FILE_ID} = ? AND ${C.Data.MIMETYPE} = ?",
            arrayOf(id.toString(), C.CommonDataKinds.Photo.CONTENT_ITEM_TYPE), null)?.use { cursor ->
            var matches = false
            while (cursor.moveToNext()) if (signature(cursor)?.let { json.optJSONObject(it)?.optInt("source") } == 1) matches = true
            matches
        } ?: false
    }
    companion object {
        private val changes = MutableStateFlow(0)
        val revision = changes.asStateFlow()
        private val locks = ConcurrentHashMap<String, Any>()
        private val snapshots = ConcurrentHashMap<String, Pair<Pair<Long, Long>, JSONObject>>()
    }
}

/** Inspect only the already acquired candidate, never on call-screen display. */
private fun readBoundedPhoto(input: java.io.InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        check(output.size() + count <= 8 * 1024 * 1024)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun notificationAvatarHasCutCorners(bytes: ByteArray): Boolean {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth < 8 || options.outHeight < 8) return false
    options.inJustDecodeBounds = false
    options.inSampleSize = Integer.highestOneBit((maxOf(options.outWidth, options.outHeight) / 96).coerceAtLeast(1))
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return false
    return try {
        bitmap.hasAlpha() && Color.alpha(bitmap[bitmap.width / 2, bitmap.height / 2]) >= 224 &&
            listOf(0 to 0, bitmap.width - 1 to 0, 0 to bitmap.height - 1, bitmap.width - 1 to bitmap.height - 1)
                .all { (x, y) -> Color.alpha(bitmap[x, y]) <= 16 }
    } finally { bitmap.recycle() }
}
