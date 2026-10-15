package dev.goodwy.rphone.modal.repository

import android.content.ContentResolver
import android.provider.ContactsContract
import android.database.Cursor

internal data class RawContactDataAccess(
    val rawContactId: String,
    val isReadOnly: Boolean,
)

internal fun readOnlyRawContactIds(rows: List<RawContactDataAccess>): Set<String> = rows
    .groupBy(RawContactDataAccess::rawContactId)
    .filterValues { values -> values.isNotEmpty() && values.all(RawContactDataAccess::isReadOnly) }
    .keys

internal data class ContactDataAccessQuery(val cursor: Cursor, val rowFlagsAvailable: Boolean)
internal data class ContactReadOnlyAccess(val rawIds: Set<String>, val rowFlagsAvailable: Boolean)

/** A missing optional OEM column is distinct from a failed/denied/empty provider query. */
internal fun ContentResolver.queryContactDataAccess(projection: Array<String>, selection: String,
    args: Array<String>): ContactDataAccessQuery {
    val uri=ContactsContract.Data.CONTENT_URI
    return try {
        ContactDataAccessQuery(requireNotNull(query(uri,projection + ContactsContract.Data.IS_READ_ONLY,
            selection,args,null)) { "Contact data access cursor unavailable" },true)
    } catch(error: IllegalArgumentException) {
        if(error.message?.contains(ContactsContract.Data.IS_READ_ONLY) != true) throw error
        ContactDataAccessQuery(requireNotNull(query(uri,projection,selection,args,null)) {
            "Contact data access cursor unavailable"
        },false)
    }
}

internal fun sourceWritableWithoutRowFlags(accountName: String?, accountType: String?, uploadingTypes: Set<String>): Boolean =
    (accountName.isNullOrBlank() && accountType.isNullOrBlank()) ||
        (!accountName.isNullOrBlank() && !accountType.isNullOrBlank() && accountType in uploadingTypes)

/** Use row flags where available; an OEM without them requires source-capability validation. */
internal fun ContentResolver.getContactReadOnlyAccess(contactId: String): ContactReadOnlyAccess {
    val rows = mutableListOf<RawContactDataAccess>()
    val result=queryContactDataAccess(arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
            "${ContactsContract.Data.CONTACT_ID} = ?",
            arrayOf(contactId))
    result.cursor.use { cursor ->
            val rawContactIdIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.RAW_CONTACT_ID)
            val readOnlyIndex = if(result.rowFlagsAvailable) cursor.getColumnIndexOrThrow(ContactsContract.Data.IS_READ_ONLY) else -1
            while (cursor.moveToNext()) {
                rows += RawContactDataAccess(
                    rawContactId = cursor.getString(rawContactIdIndex),
                    isReadOnly = readOnlyIndex >= 0 && cursor.getInt(readOnlyIndex) != 0,
                )
            }
    }
    return ContactReadOnlyAccess(readOnlyRawContactIds(rows),result.rowFlagsAvailable)
}
