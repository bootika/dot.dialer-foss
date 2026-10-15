package dev.goodwy.rphone.controller.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal data class ContactNumberMatch(val contactId: String, val usedFallback: Boolean)

/** Read-only fallback after an empty successful platform lookup; identity requires all digits. */
internal suspend fun lookupContactNumber(
    resolver: ContentResolver, number: String, region: () -> String?,
): ContactNumberMatch? {
    if (number.isBlank()) return null
    try {
        val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val result = resolver.query(lookup, arrayOf(ContactsContract.PhoneLookup.CONTACT_ID), null, null, null)
            ?: return null
        result.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(ContactsContract.PhoneLookup.CONTACT_ID)
                return if (index >= 0) cursor.getString(index)?.let { ContactNumberMatch(it, false) } else null
            }
        }
        coroutineContext.ensureActive()
        val iso = reliableContactLookupRegion(listOf(region())) ?: return null
        val target = lookupNumberKey(number, iso) ?: return null
        val candidates = resolver.query(Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER),
            null, null, null) ?: return null
        var match: String? = null
        candidates.use { cursor ->
            val idIndex = cursor.getColumnIndex(Phone.CONTACT_ID)
            val numberIndex = cursor.getColumnIndex(Phone.NUMBER)
            if (idIndex < 0 || numberIndex < 0) return null
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val candidate = cursor.getString(numberIndex) ?: continue
                if (lookupNumberKey(candidate, iso) != target) continue
                val id = cursor.getString(idIndex)?.takeIf(String::isNotBlank) ?: return null
                if (match != null && match != id) return null
                match = id
            }
        }
        return match?.let { ContactNumberMatch(it, true) }
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
    } catch (failure: RuntimeException) {
        android.util.Log.w("ContactLookup", "Contact lookup unavailable (${failure.javaClass.simpleName})")
        return null
    }
}

private fun lookupNumberKey(number: String, iso: String): String? {
    val compact = number.filter { it.isDigit() || it == '+' }
    if (compact.count { it == '+' } > 1 || ('+' in compact && !compact.startsWith('+'))) return null
    return normalizedContactNumber(number, iso)
}
