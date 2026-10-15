package dev.goodwy.rphone.modal.repository

import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.modal.data.getDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.bootika.dotdialer.core.preferences.RecentHistoryPolicy
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer

class CallLogRepository(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val contactsRepo: IContactsRepository,
    private val preferenceManager: PreferenceManager = PreferenceManager(context),
) : ICallLogRepository {

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    override suspend fun getCallLogs(): List<CallLogEntry> {
        val periodDays = RecentHistoryPolicy.sanitizePeriodDays(
            preferenceManager.getInt(
                PreferenceManager.KEY_RECENT_HISTORY_PERIOD_DAYS,
                RecentHistoryPolicy.DEFAULT_PERIOD_DAYS,
            ),
        )
        val maxEntries = RecentHistoryPolicy.sanitizeMaxEntries(
            preferenceManager.getInt(
                PreferenceManager.KEY_RECENT_HISTORY_MAX_ENTRIES,
                RecentHistoryPolicy.DEFAULT_MAX_ENTRIES,
            ),
        )
        val cutoff = RecentHistoryPolicy.cutoffMillis(periodDays, System.currentTimeMillis())
        return queryCallLogs(cutoff = cutoff, maxEntries = maxEntries,
            individualCalls = preferenceManager.getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS, false))
    }

    override suspend fun getCompleteCallLogs(): List<CallLogEntry> =
        queryCallLogs(cutoff = null, maxEntries = Int.MAX_VALUE)

    private suspend fun queryCallLogs(
        cutoff: Long?,
        maxEntries: Int,
        individualCalls: Boolean = false,
    ): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val callLogs = mutableListOf<CallLogEntry>()
        val selection = cutoff?.let { "${CallLog.Calls.DATE} >= ?" }
        val selectionArgs = cutoff?.let { arrayOf(it.toString()) }
        // A raw provider limit would let repeated calls consume the whole allowance before the
        // UI unifies them in grouped mode. Individual mode instead caps raw call rows.
        val boundedUri = CallLog.Calls.CONTENT_URI

        // IContactsRepository already owns an invalidatable snapshot cache. Building this small
        // lookup per call-log refresh avoids a second TTL cache that could retain deleted Android
        // aggregate IDs and old photos after a contact edit/merge.
        val allContacts = try { contactsRepo.getContacts() } catch (e: Exception) { emptyList() }
        val contactMap = mutableMapOf<String, Contact>()
        allContacts.forEach { contact ->
            contact.phoneNumbers.forEach { number ->
                SearchTextNormalizer.phoneIdentityKey(number)?.let { key ->
                    val existing = contactMap[key]
                    if (existing == null || (existing.photoUri == null && contact.photoUri != null)) {
                        contactMap[key] = contact
                    }
                }
            }
        }

        val baseProjection = mutableListOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_PHOTO_URI,
            CallLog.Calls.CACHED_LOOKUP_URI,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        baseProjection.add("phone_account_label")
        baseProjection.add(CallLog.Calls.PHONE_ACCOUNT_ID)
        baseProjection.add(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)

        try {
            val cursor = contentResolver.query(
                boundedUri,
                baseProjection.toTypedArray(),
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use { parseCursor(it, callLogs, contactMap, maxEntries, individualCalls) }
        } catch (e: Exception) {
            // If the above fails due to "phone_account_label" or other columns, try a safer subset
            try {
                val safeProjection = arrayOf(
                    CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.CACHED_PHOTO_URI, CallLog.Calls.CACHED_LOOKUP_URI,
                    CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION,
                    CallLog.Calls.PHONE_ACCOUNT_ID, CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME
                )
                val cursor = contentResolver.query(
                    boundedUri,
                    safeProjection,
                    selection,
                    selectionArgs,
                    "${CallLog.Calls.DATE} DESC"
                )
                cursor?.use { parseCursor(it, callLogs, contactMap, maxEntries, individualCalls) }
            } catch (e2: Exception) {
                android.util.Log.e("CallLogRepository", "Unable to read call history", e2)
            }
        }

        callLogs
    }

    override suspend fun saveCallLog(entry: CallLogEntry) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(CallLog.Calls.NUMBER, entry.number)
            put(CallLog.Calls.TYPE, entry.type)
            put(CallLog.Calls.DATE, entry.date)
            put(CallLog.Calls.DURATION, entry.duration)
            put(CallLog.Calls.NEW, if (entry.type == CallLog.Calls.MISSED_TYPE) 1 else 0)
        }
        try {
            contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
        } catch (e: Exception) {
            android.util.Log.e("CallLogRepository", "Unable to save call-history entry", e)
        }
        Unit
    }

    override suspend fun markMissedCallsSeen() = withContext(Dispatchers.IO) {
        val selection = "${CallLog.Calls.TYPE} = ? AND " +
            "(${CallLog.Calls.NEW} = 1 OR ${CallLog.Calls.IS_READ} = 0)"
        val fallbackSelection = "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1"
        val selectionArgs = arrayOf(CallLog.Calls.MISSED_TYPE.toString())
        val values = ContentValues().apply {
            put(CallLog.Calls.NEW, 0)
            put(CallLog.Calls.IS_READ, 1)
        }

        try {
            contentResolver.update(CallLog.Calls.CONTENT_URI, values, selection, selectionArgs)
        } catch (error: IllegalArgumentException) {
            // Some OEM call-log providers do not expose IS_READ even though NEW is writable.
            // Keep the important behavior working instead of leaving the missed call unread.
            try {
                val fallbackValues = ContentValues().apply { put(CallLog.Calls.NEW, 0) }
                contentResolver.update(
                    CallLog.Calls.CONTENT_URI,
                    fallbackValues,
                    fallbackSelection,
                    selectionArgs,
                )
            } catch (fallbackError: Exception) {
                android.util.Log.e(
                    "CallLogRepository",
                    "Unable to mark missed calls as seen",
                    fallbackError,
                )
            }
        } catch (error: Exception) {
            android.util.Log.e("CallLogRepository", "Unable to mark missed calls as seen", error)
        }
        Unit
    }

    private fun contactIdFromLookupUri(lookupUri: String): String? {
        return try {
            val segments = lookupUri.toUri().pathSegments
            segments.lastOrNull { it.toLongOrNull() != null } ?: segments.lastOrNull()
        } catch (error: Exception) {
            android.util.Log.w("CallLogRepository", "Unable to parse a cached contact lookup URI", error)
            null
        }
    }

    private suspend fun parseCursor(
        cursor: Cursor,
        callLogs: MutableList<CallLogEntry>,
        contactMap: Map<String, Contact>,
        maxEntries: Int,
        individualCalls: Boolean,
    ) {
        val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
        val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
        val cachedNameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
        val cachedPhotoIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
        val cachedLookupIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI)
        val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
        val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
        val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

        val labelIdx = cursor.getColumnIndex("phone_account_label")
        val accountIdIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
        val componentNameIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)

        val tempLogs = mutableListOf<CallLogEntry>()
        val simCache = mutableMapOf<String, String>()
        val directContactCache = mutableMapOf<String, Contact?>()
        val unknownAvatar = dev.goodwy.rphone.controller.util.UnsavedNumberAvatars.reader(context,preferenceManager)
        val unknownLabel = context.getString(R.string.label_unknown)

        val displayOrder = preferenceManager.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0)

        val seenPhoneNumbers = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            if (individualCalls && tempLogs.size >= maxEntries) break
            val callId = cursor.getLong(idIdx)
            val number = cursor.getString(numberIdx) ?: unknownLabel
            val phoneIdentity = SearchTextNormalizer.phoneIdentityKey(number) ?: number
            if (
                !individualCalls && maxEntries != Int.MAX_VALUE &&
                phoneIdentity !in seenPhoneNumbers &&
                seenPhoneNumbers.size >= maxEntries
            ) {
                break
            }
            seenPhoneNumbers += phoneIdentity
            val type = cursor.getInt(typeIdx)
            val date = cursor.getLong(dateIdx)
            val duration = cursor.getLong(durationIdx)

            var simLabel = if (labelIdx != -1) cursor.getString(labelIdx) else null

            val isBlocked = type == CallLog.Calls.BLOCKED_TYPE// || type == CallLog.Calls.REJECTED_TYPE

            // If label is missing, try to resolve it from account ID
            if (simLabel.isNullOrEmpty() && accountIdIdx != -1 && componentNameIdx != -1) {
                val accountId = cursor.getString(accountIdIdx)
                val componentStr = cursor.getString(componentNameIdx)

                if (!accountId.isNullOrEmpty() && !componentStr.isNullOrEmpty()) {
                    simLabel = simCache.getOrPut("$componentStr/$accountId") {
                        try {
                            val componentName = ComponentName.unflattenFromString(componentStr)
                            if (componentName != null) {
                                val handle = PhoneAccountHandle(componentName, accountId)
                                telecomManager?.getPhoneAccount(handle)?.label?.toString() ?: ""
                            } else ""
                        } catch (e: Exception) { "" }
                    }
                }
            }

            if (simLabel?.isEmpty() == true) simLabel = null

            // Enrich with contact data
            val lookupKey = SearchTextNormalizer.phoneIdentityKey(number)
            val cachedName = cursor.getString(cachedNameIdx)
            val cachedLookupUri = cursor.getString(cachedLookupIdx)
            val matchedContact = lookupKey?.let(contactMap::get) ?: if (
                lookupKey != null && (!cachedName.isNullOrBlank() || !cachedLookupUri.isNullOrBlank())
            ) {
                if (directContactCache.containsKey(lookupKey)) {
                    directContactCache[lookupKey]
                } else {
                    val resolved = try {
                        contactsRepo.getContactByNumber(number)
                    } catch (error: Exception) {
                        android.util.Log.w(
                            "CallLogRepository",
                            "Unable to refresh stale contact metadata for a recent call",
                            error,
                        )
                        null
                    }
                    directContactCache[lookupKey] = resolved
                    resolved
                }
            } else {
                null
            }

//            val displayName = matchedContact?.displayName ?: cursor.getString(cachedNameIdx)
            val photoUri = matchedContact?.photoUri ?: cursor.getString(cachedPhotoIdx) ?:
                if (matchedContact == null) unknownAvatar(number) else null
            val contactId = matchedContact?.id ?: cachedLookupUri?.let {
                contactIdFromLookupUri(it)
            }

            val contactName = if (matchedContact != null) getDisplayName(matchedContact, displayOrder) else null //matchedContact?.displayName
            val phoneDetails = matchedContact?.phoneDetails?.firstOrNull {
                SearchTextNormalizer.phoneIdentityKey(it.number) == lookupKey
            }
            val phoneType = phoneDetails?.type
            val phoneLabel = phoneDetails?.label

//                val isCallerIdName = contactName == null && cachedName != null
            val isCallerIdName = contactName.isNullOrEmpty() && !cachedName.isNullOrEmpty()
            val displayName = when {
                !contactName.isNullOrEmpty() -> contactName
                !cachedName.isNullOrEmpty() -> cachedName
                else -> number
            }

            val lastEntry = tempLogs.lastOrNull()
            if (!individualCalls && lastEntry != null && lastEntry.number == number) {
                tempLogs[tempLogs.size - 1] = lastEntry.copy(
                    types = lastEntry.types + type,
                    ids = lastEntry.ids + callId
                )
            } else {
                tempLogs.add(
                    CallLogEntry(
                        id = callId,
                        number = number,
                        name = displayName,
                        type = type,
                        date = date,
                        duration = duration,
                        photoUri = photoUri,
                        contactId = contactId,
                        simLabel = simLabel,
                        isBlocked = isBlocked,
                        types = listOf(type),
                        ids = listOf(callId),
                        isCallerIdName = isCallerIdName,
                        phoneType = phoneType,
                        phoneLabel = phoneLabel
                    )
                )
            }
        }
        callLogs.addAll(tempLogs)
    }

    override suspend fun deleteCallLog(number: String) = withContext(Dispatchers.IO) {
        try {
            contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.NUMBER} = ?",
                arrayOf(number)
            )
        } catch (e: Exception) {
            android.util.Log.e("CallLogRepository", "Unable to delete call-history entries by number", e)
        }
        Unit
    }

    override suspend fun deleteCallLogsByIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        try {
            val selection = "${CallLog.Calls._ID} IN (${ids.joinToString(",")})"
            contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, null)
        } catch (e: Exception) {
            android.util.Log.e("CallLogRepository", "Unable to delete selected call-history entries", e)
        }
    }

    override suspend fun clearCallLogs() = withContext(Dispatchers.IO) {
        try {
            contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        } catch (e: Exception) {
            android.util.Log.e("CallLogRepository", "Unable to clear call history", e)
        }
        Unit
    }
}
