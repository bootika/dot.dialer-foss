package io.github.bootika.dotdialer.diagnostics

import android.Manifest
import android.content.Context
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.net.toUri
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticDetail
import io.github.bootika.dotdialer.core.diagnostics.PhotoDiagnosticResult
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** IO-only; identifiers remain transient, returned/logged result contains only counts and dimensions. */
internal suspend fun readContactPhotoProbe(context: Context, number: String, session: Int): DiagnosticDetail.PhotoProbe {
    if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
        return DiagnosticDetail.PhotoProbe(session, PhotoDiagnosticResult.PERMISSION_DENIED)
    return try {
        coroutineContext.ensureActive()
        val resolver = context.contentResolver
        val ids = linkedSetOf<Long>(); val full = linkedSetOf<String>(); val thumbs = linkedSetOf<String>()
        resolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI), null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && ids.size < 50) {
                coroutineContext.ensureActive()
                ids += cursor.getLong(0)
                cursor.getString(1)?.takeIf(String::isNotBlank)?.let(full::add)
                cursor.getString(2)?.takeIf(String::isNotBlank)?.let(thumbs::add)
            }
        } ?: return DiagnosticDetail.PhotoProbe(session, PhotoDiagnosticResult.ERROR)
        if (ids.isEmpty()) return DiagnosticDetail.PhotoProbe(session, PhotoDiagnosticResult.NO_MATCH)
        var raw = 0; var google = 0; var samsung = 0; var other = 0; var primary = 0
        val rawFull = mutableListOf<Pair<Int, Int>>()
        val placeholders = ids.joinToString(",") { "?" }
        resolver.query(ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts.CONTACT_ID} IN ($placeholders) AND ${ContactsContract.RawContacts.DELETED}=0",
            ids.map(Long::toString).toTypedArray(), null)?.use { cursor ->
            while (cursor.moveToNext() && raw < 200) {
                coroutineContext.ensureActive(); raw++
                val type = cursor.getString(1).orEmpty()
                when { type == "com.google" -> google++
                    type.contains("samsung", true) -> samsung++
                    else -> other++ }
                val rawUri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, cursor.getLong(0))
                rawFull += photoBounds(context, Uri.withAppendedPath(rawUri,
                    ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY).toString())
            }
        } ?: return DiagnosticDetail.PhotoProbe(session, PhotoDiagnosticResult.ERROR, matches = ids.size)
        resolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.IS_SUPER_PRIMARY),
            "${ContactsContract.Data.CONTACT_ID} IN ($placeholders) AND ${ContactsContract.Data.MIMETYPE}=?",
            (ids.map(Long::toString) + ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE).toTypedArray(), null)?.use { cursor ->
            while (cursor.moveToNext()) { coroutineContext.ensureActive(); if (cursor.getInt(0) != 0) primary++ }
        } ?: return DiagnosticDetail.PhotoProbe(session, PhotoDiagnosticResult.ERROR, matches = ids.size)
        val fullBounds = full.map { photoBounds(context, it) }; val thumbBounds = thumbs.map { photoBounds(context, it) }
        DiagnosticDetail.PhotoProbe(session, PhotoDiagnosticResult.OK, ids.size, raw, google, samsung, other, primary,
            full.size, thumbs.size, fullBounds.count { it.first > 0 }, thumbBounds.count { it.first > 0 },
            fullBounds.maxOfOrNull { it.first } ?: 0, fullBounds.maxOfOrNull { it.second } ?: 0,
            thumbBounds.maxOfOrNull { it.first } ?: 0, thumbBounds.maxOfOrNull { it.second } ?: 0,
            rawFull.count { it.first > 0 }, rawFull.maxOfOrNull { it.first } ?: 0, rawFull.maxOfOrNull { it.second } ?: 0)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (denied: SecurityException) { DiagnosticDetail.PhotoProbe(session, PhotoDiagnosticResult.PERMISSION_DENIED) }
    catch (failure: Exception) { DiagnosticDetail.PhotoProbe(session, PhotoDiagnosticResult.ERROR) }
}

private fun photoBounds(context: Context, value: String): Pair<Int, Int> = try {
    val uri = value.toUri()
    if (uri.scheme != "content") 0 to 0 else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        maxOf(0, options.outWidth) to maxOf(0, options.outHeight)
    }
} catch (failure: Exception) { 0 to 0 }
