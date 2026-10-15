package dev.goodwy.rphone.controller.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object SmallCallPhotoPreference {
    const val KEY = "small_call_photos_in_circle"
    const val DEFAULT = true
    const val MAX_SHORT_EDGE_PX = 256
}

internal data class CallPhotoDimensions(val width: Int, val height: Int, val fromWhatsApp: Boolean = false) {
    val requiresCircle: Boolean get() = fromWhatsApp
    val isSmall: Boolean get() = width > 0 && height > 0 &&
        minOf(width, height) <= SmallCallPhotoPreference.MAX_SHORT_EDGE_PX
}

/** Bounds only, local IO. A Contacts thumbnail is never evidence of full-photo resolution. */
internal class CallPhotoSizeResolver(
    private val context: Context,
    private val open: (Uri) -> java.io.InputStream? = { uri -> context.contentResolver.openInputStream(uri) },
) {
    suspend fun resolve(photoUri: String?): CallPhotoDimensions? = withContext(Dispatchers.IO) {
        try {
            val uri = photoUri?.let(Uri::parse) ?: return@withContext null
            if (uri.scheme !in setOf("content", "file")) return@withContext null
            val full = fullPhotoUri(uri) ?: return@withContext null
            if (full.scheme !in setOf("content", "file")) return@withContext null
            if (runCatching { CallPhotoOrigins(context).isWhatsApp(full) }.getOrDefault(false)) return@withContext CallPhotoDimensions(0, 0, fromWhatsApp = true)
            val input = open(full)
            input?.use { stream ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0)
                    CallPhotoDimensions(bounds.outWidth, bounds.outHeight) else null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("CallPhotoSize", "Photo size unavailable: " + error.javaClass.simpleName)
            null // Unavailable dimensions retain the user's saved appearance.
        }
    }

    private fun fullPhotoUri(uri: Uri): Uri? {
        if (uri.authority != ContactsContract.AUTHORITY) return uri
        val segments = uri.pathSegments
        val thumbnail = segments.lastOrNull() == ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
        val dataRow = segments.firstOrNull() == "data"
        if (!thumbnail && !dataRow) return uri
        val target = if (thumbnail) uri.buildUpon().path(segments.dropLast(1).joinToString("/", "/")).build() else uri
        return context.contentResolver.query(target, arrayOf(ContactsContract.Contacts.PHOTO_URI), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val full = if (index >= 0) cursor.getString(index)?.takeIf(String::isNotBlank)?.let(Uri::parse) else null
            full?.takeUnless { it == uri || it.pathSegments.lastOrNull() == ContactsContract.Contacts.Photo.CONTENT_DIRECTORY }
        }
    }
}
