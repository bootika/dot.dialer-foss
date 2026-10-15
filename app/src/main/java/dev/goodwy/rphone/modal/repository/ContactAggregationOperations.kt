package dev.goodwy.rphone.modal.repository

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.provider.ContactsContract
import kotlinx.coroutines.delay

internal fun aggregationOperation(a: String, b: String, type: Int): ContentProviderOperation {
    val first = a.toLong(); val second = b.toLong()
    require(first != second)
    return ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
        .withValue(ContactsContract.AggregationExceptions.TYPE, type)
        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, minOf(first,second))
        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, maxOf(first,second)).build()
}

internal suspend fun requireContactAggregation(resolver: ContentResolver, rawIds: List<String>, together: Boolean) {
    repeat(20) {
        val ids = mutableMapOf<String,String>()
        requireNotNull(resolver.query(ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID,ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts._ID} IN (${rawIds.joinToString(",") { "?" }})",rawIds.toTypedArray(),null)) {
            "Cannot verify contact sources"
        }.use { c -> while(c.moveToNext()) ids[c.getString(0)] = c.getString(1) }
        if(ids.keys.containsAll(rawIds) &&
            (if(together) ids.values.distinct().size == 1 else ids.values.distinct().size == rawIds.distinct().size)) return
        delay(50)
    }
    error("Contacts provider has not confirmed the change; refresh before retrying")
}
