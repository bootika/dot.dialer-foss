package dev.goodwy.rphone.modal.repository

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.provider.ContactsContract

/** Updates known columns in matching rows; never discards unmodelled columns or read-only rows. */
internal fun MutableList<ContentProviderOperation>.syncContactEditRows(
    resolver: ContentResolver, rawId: String, mime: String, desired: List<Map<String, Any?>>,
) {
    data class Row(val id: String, val readOnly: Boolean, val values: Map<String, String?>)
    val columns = desired.flatMap { it.keys }.filterNot { it == ContactsContract.Data.DATA15 }.distinct()
    val rows = mutableListOf<Row>()
    val projection = (listOf(ContactsContract.Data._ID) + columns).toTypedArray()
    val access=resolver.queryContactDataAccess(projection,
        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
        arrayOf(rawId, mime))
    access.cursor.use { cursor ->
        val flag=if(access.rowFlagsAvailable) cursor.getColumnIndexOrThrow(ContactsContract.Data.IS_READ_ONLY) else -1
        while (cursor.moveToNext()) rows += Row(cursor.getString(0), flag >= 0 && cursor.getInt(flag) != 0,
            columns.associateWith { cursor.getString(cursor.getColumnIndexOrThrow(it)) })
    }
    // The source was validated before building this batch. The provider enforces unknown row rights;
    // expectedCount=1 makes a refused update/delete fail the entire atomic batch.
    val protectedSelection=if(access.rowFlagsAvailable) " AND (${ContactsContract.Data.IS_READ_ONLY} = 0 OR ${ContactsContract.Data.IS_READ_ONLY} IS NULL)" else ""
    val unused = rows.toMutableList()
    desired.forEach { values ->
        val identity = values[ContactsContract.Data.DATA1]?.toString()
        val exact = unused.firstOrNull { row ->
            row.values[ContactsContract.Data.DATA1] == identity &&
                values.all { (key, value) ->
                    when {
                        value is ByteArray -> false
                        key == ContactsContract.Data.IS_PRIMARY || key == ContactsContract.Data.IS_SUPER_PRIMARY ->
                            (row.values[key]?.toIntOrNull() ?: 0) == (value?.toString()?.toIntOrNull() ?: 0)
                        else -> row.values[key].orEmpty() == value?.toString().orEmpty()
                    }
                }
        }
        val matching = exact ?: unused.firstOrNull { !it.readOnly && it.values[ContactsContract.Data.DATA1] == identity }
            ?: unused.firstOrNull { !it.readOnly }
        if (matching?.readOnly == true) {
            require(values.values.none { it is ByteArray }) { "Contact photo is read-only" }
            unused.remove(matching)
        } else {
            val builder = if (matching != null) {
                unused.remove(matching)
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection("${ContactsContract.Data._ID} = ? AND ${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?$protectedSelection",
                        arrayOf(matching.id, rawId, mime)).withExpectedCount(1)
            } else ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId).withValue(ContactsContract.Data.MIMETYPE, mime)
            values.forEach { (key, value) -> builder.withValue(key, value) }
            add(builder.build())
        }
    }
    require(unused.none { it.readOnly }) { "Cannot remove read-only contact fields" }
    unused.forEach { row ->
        add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection("${ContactsContract.Data._ID} = ? AND ${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?$protectedSelection",
                arrayOf(row.id, rawId, mime)).withExpectedCount(1).build())
    }
}
