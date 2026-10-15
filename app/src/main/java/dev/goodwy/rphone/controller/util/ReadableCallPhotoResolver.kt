package dev.goodwy.rphone.controller.util

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolve readable local contact imagery, without fetching network URLs or copying photographs. */
internal class ReadableCallPhotoResolver(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun resolve(primary: String?, fallback: String?): String? = withContext(Dispatchers.IO) {
        listOfNotNull(primary, fallback).distinct().firstOrNull { candidate ->
            try {
                val uri = candidate.toUri()
                if (uri.scheme !in listOf("content", "file")) return@firstOrNull false
                resolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    options.outWidth > 0 && options.outHeight > 0
                } == true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w("CallPhotos", "Local photo unavailable (${failure.javaClass.simpleName})")
                false
            }
        }
    }
}
