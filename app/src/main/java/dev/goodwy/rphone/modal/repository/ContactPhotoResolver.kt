package dev.goodwy.rphone.modal.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import dev.goodwy.rphone.modal.data.Contact

internal fun Contact.withPhotoIfMissing(candidatePhotoUri: String?): Contact {
    return if (photoUri.isNullOrBlank() && !candidatePhotoUri.isNullOrBlank()) {
        copy(photoUri = candidatePhotoUri)
    } else {
        this
    }
}

internal fun ContentResolver.getSystemContactPhotoUri(contactId: String): String? {
    return try {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
        var photoUri: String? = null
        query(uri, arrayOf(ContactsContract.Contacts.PHOTO_URI), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) photoUri = cursor.getString(0)
        }
        photoUri
    } catch (error: Exception) {
        Log.w("ContactsRepository", "Unable to read the contact photo", error)
        null
    }
}

internal fun ContentResolver.getSystemContactPhotoUris(): Map<String, String> {
    val photos = mutableMapOf<String, String>()
    return try {
        query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.PHOTO_URI),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val photoIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val photoUri = cursor.getString(photoIndex)
                if (!id.isNullOrBlank() && !photoUri.isNullOrBlank()) photos[id] = photoUri
            }
        }
        photos
    } catch (error: Exception) {
        Log.w("ContactsRepository", "Unable to build the contact photo index", error)
        emptyMap()
    }
}
