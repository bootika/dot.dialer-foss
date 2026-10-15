package dev.goodwy.rphone.controller

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract

/** Resolves provider row identities without treating a Data row or a stale lookup ID as a contact. */
internal object ExternalContactUriResolver {
    private enum class Row { CONTACT, LOOKUP, DATA, RAW }

    private fun row(uri: Uri): Row? {
        if (uri.scheme != "content") return null
        val segments = uri.pathSegments
        val numericLast = segments.lastOrNull()?.toLongOrNull()?.let { it > 0 } == true
        if (uri.authority == "contacts") {
            return if (segments.size == 2 && segments.first() == "people" && numericLast) Row.RAW else null
        }
        if (uri.authority != ContactsContract.AUTHORITY) return null
        return when {
            segments.size == 2 && segments.first() == "contacts" && numericLast -> Row.CONTACT
            segments.size in 3..4 && segments.take(2) == listOf("contacts", "lookup") &&
                segments[2].isNotBlank() && (segments.size == 3 || numericLast) -> Row.LOOKUP
            segments.size == 2 && segments.first() == "data" && numericLast -> Row.DATA
            segments.size == 3 && segments.first() == "data" &&
                segments[1] in setOf("phones", "emails") && numericLast -> Row.DATA
            segments.size == 2 && segments.first() == "raw_contacts" && numericLast -> Row.RAW
            else -> null
        }
    }

    fun accepts(uri: Uri): Boolean = row(uri) != null

    fun resolve(resolver: ContentResolver, uri: Uri): String? {
        val kind = row(uri) ?: return null
        return try {
            if (kind == Row.LOOKUP) {
                // The provider resolves the full encoded key, including sync/aggregation changes.
                ContactsContract.Contacts.lookupContact(resolver, uri)?.lastPathSegment
                    ?.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }
            } else {
                val column = if (kind == Row.CONTACT) ContactsContract.Contacts._ID else ContactsContract.Data.CONTACT_ID
                // Legacy People IDs name raw contacts, not aggregate contacts (AOSP LegacyApiSupport).
                val queryUri = if (uri.authority == "contacts")
                    ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, ContentUris.parseId(uri)) else uri
                resolver.query(queryUri, arrayOf(column), null, null, null)?.use { cursor ->
                    if (cursor.count != 1 || !cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(column)
                    if (index < 0 || cursor.isNull(index)) return@use null
                    cursor.getString(index)?.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }
                }
            }
        } catch (error: RuntimeException) {
            // Keep failure observable without logging the URI, identity or exception message.
            val reason = if (error is SecurityException) "Contact access denied" else "Contact provider unavailable"
            android.util.Log.w("ExternalContact", reason)
            null
        }
    }
}
