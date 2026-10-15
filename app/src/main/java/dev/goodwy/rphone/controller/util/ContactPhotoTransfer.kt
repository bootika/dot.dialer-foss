package dev.goodwy.rphone.controller.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun contactPhotoMime(context: Context, uri: Uri): String {
    val detected = try { context.contentResolver.getType(uri) } catch (error: Exception) {
        android.util.Log.w("ContactPhotoTransfer", "Photo MIME unavailable; trying the file extension")
        null
    }
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.')?.lowercase())
    return detected?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
}

internal suspend fun copyContactPhoto(context: Context, source: Uri, target: Uri) = withContext(Dispatchers.IO) {
    require(source != target)
    requireNotNull(context.contentResolver.openInputStream(source)).use { input ->
        requireNotNull(context.contentResolver.openOutputStream(target, "w")).use { output ->
            check(input.copyTo(output) > 0) { "Empty contact photo" }
        }
    }
}

internal suspend fun contactPhotoShareIntent(context: Context, source: Uri): Intent = withContext(Dispatchers.IO) {
    val mime = contactPhotoMime(context, source)
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
    val directory = File(context.cacheDir, "shared_contact_photos").apply { check(isDirectory || mkdirs()) }
    // Private cache, narrow ownership. Android may still need recent files after the chooser returns.
    directory.listFiles()?.filter { it.name.startsWith("photo-") && it.lastModified() < System.currentTimeMillis() - 86400000 }
        ?.forEach { it.delete() }
    val file = File(directory, "photo-${UUID.randomUUID()}.$extension")
    try {
        requireNotNull(context.contentResolver.openInputStream(source)).use { input ->
            file.outputStream().use { output -> check(input.copyTo(output) > 0) { "Empty contact photo" } }
        }
        val shared = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, shared)
            clipData = ClipData.newUri(context.contentResolver, "Contact photo", shared)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } catch (error: Exception) {
        file.delete()
        throw error
    }
}
