package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.AtomicFile
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.MainActivity
import dev.goodwy.rphone.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import io.github.bootika.dotdialer.core.diagnostics.ContactPhotoApplyStage
import io.github.bootika.dotdialer.core.diagnostics.ContactPhotoApplyResult
import kotlinx.coroutines.CancellationException

internal class WhatsAppAvatarContactStore(
    private val context: Context,
    private val resolver: ContentResolver = context.contentResolver,
    private val numberRegion: () -> String? = { contactNumberRegion(context) },
) {
    fun resolveByPhone(phoneNumber: String, accountName: String): WhatsAppAvatarContactTarget? {
        requirePermissions()
        val contactIds = exactContactIds(phoneNumber)
        if (contactIds.size != 1) return null
        return resolveGoogleTarget(
            contactId = contactIds.single(),
            accountName = accountName,
            matchedNumber = phoneNumber,
            strength = WhatsAppAvatarMatchStrength.ExactPhone,
        )
    }
    fun hasAnyNumber(phoneNumber: String): Boolean {
        requirePermissions()
        val uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(phoneNumber))
        val count=resolver.query(uri,arrayOf(ContactsContract.PhoneLookup._ID),null,null,null)?.use { it.count } ?: return true
        return count>0 || exactContactIds(phoneNumber).isNotEmpty()
    }

    fun resolveByUniqueName(displayName: String, accountName: String): WhatsAppAvatarContactTarget? {
        requirePermissions()
        val cleanName = displayName.trim()
        if (cleanName.length < 2) return null
        val contactIds = linkedSetOf<Long>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?",
            arrayOf(cleanName),
            null,
        )?.use { cursor -> while (cursor.moveToNext()) contactIds += cursor.getLong(0) }
        if (contactIds.size != 1) return null
        val targets = contactIds.mapNotNull {
            resolveGoogleTarget(it, accountName, null, WhatsAppAvatarMatchStrength.UniqueName)
        }
        // Provider collations may be case/accent insensitive; automatic matching is exact.
        return targets.singleOrNull()?.takeIf { it.displayName == cleanName }
    }

    fun revalidate(item: WhatsAppAvatarReviewItem): WhatsAppAvatarContactTarget? {
        val target = if (item.matchStrength == WhatsAppAvatarMatchStrength.ExactPhone && item.matchedNumber != null) {
            resolveByPhone(item.matchedNumber, item.accountName)
        } else if (item.matchStrength == WhatsAppAvatarMatchStrength.UserSelected) {
            chooseContact(item.contactId, item.accountName)
        } else {
            resolveByUniqueName(item.displayName, item.accountName)
        }
        return target?.takeIf {
            it.contactId == item.contactId && it.rawContactId == item.rawContactId &&
                it.phoneDataId == item.phoneDataId
        }
    }

    fun selectedPhotoAllowsAutomaticUpdate(target: WhatsAppAvatarContactTarget): Boolean {
        requirePermissions()
        val selected = resolver.query(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, target.contactId),
            arrayOf(ContactsContract.Contacts.PHOTO_ID), null, null, null) ?: return false
        val photoId = selected.use { cursor ->
            if (!cursor.moveToFirst()) return false
            if (cursor.isNull(0)) null else cursor.getLong(0)
        } ?: return true
        val owner = resolver.query(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, photoId),
            arrayOf(ContactsContract.Data.RAW_CONTACT_ID), null, null, null) ?: return false
        return owner.use { it.moveToFirst() && it.getLong(0) == target.rawContactId }
    }

    fun maximumDisplayPhotoDimension(): Int? {
        requirePermissions()
        return runCatching {
            resolver.query(ContactsContract.DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
                arrayOf(ContactsContract.DisplayPhoto.DISPLAY_MAX_DIM),null,null,null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getInt(0).takeIf { it > 0 } else null
            }
        }.getOrNull()
    }

    suspend fun writePhoto(target: WhatsAppAvatarContactTarget, bytes: ByteArray,
        allowSmaller: () -> Boolean = { false }, canWrite: () -> Boolean = { true }): Boolean {
        try {
            return writePhotoAndVerify(target, bytes, allowSmaller, canWrite)
        } catch (error: CancellationException) {
            ContactPhotoApplyTrace.record(ContactPhotoApplyStage.WRITE, ContactPhotoApplyResult.CANCELLED)
            throw error
        } catch (error: Exception) {
            ContactPhotoApplyTrace.record(ContactPhotoApplyStage.WRITE, when (error) {
                is SecurityException -> ContactPhotoApplyResult.PERMISSION_DENIED
                is IOException -> ContactPhotoApplyResult.IO_FAILED
                else -> ContactPhotoApplyResult.EXCEPTION
            })
            throw error
        }
    }
    private suspend fun writePhotoAndVerify(target: WhatsAppAvatarContactTarget, bytes: ByteArray,
        allowSmaller: () -> Boolean, canWrite: () -> Boolean): Boolean {
        requirePermissions()
        val expected = bytes.toContactPhotoImageInfo() ?: return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.PREFLIGHT, ContactPhotoApplyResult.CANDIDATE_INVALID)
        if (bytes.size > 8*1024*1024 || !supportedContactPhotoSize(expected))
            return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.PREFLIGHT, ContactPhotoApplyResult.CANDIDATE_INVALID)
        val latest = revalidateTarget(target) ?: return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.PREFLIGHT, ContactPhotoApplyResult.IDENTITY_CHANGED)
        if (!sameNullablePhoto(latest.photo, target.photo)) return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.PREFLIGHT, ContactPhotoApplyResult.PHOTO_CHANGED)
        val previousInfo = latest.photo?.toContactPhotoImageInfo()
        val isSmaller = previousInfo != null && (expected.width < previousInfo.width || expected.height < previousInfo.height)
        fun canCommit() = canWrite() && (!isSmaller || allowSmaller())
        if (!canCommit()) return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.PREFLIGHT,
            if (isSmaller && !allowSmaller()) ContactPhotoApplyResult.SMALLER else ContactPhotoApplyResult.INELIGIBLE)
        val before = dataFingerprint(target.rawContactId)
        val rawUri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, target.rawContactId)
        val uri = Uri.withAppendedPath(rawUri, ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY)
        if (!canCommit()) return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.PREFLIGHT, ContactPhotoApplyResult.INELIGIBLE)
        val descriptor = resolver.openAssetFileDescriptor(uri, "rw") ?: return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.WRITE, ContactPhotoApplyResult.PROVIDER_UNAVAILABLE)
        descriptor.use { asset ->
            asset.createOutputStream().use { stream ->
                if (!canCommit()) return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.WRITE, ContactPhotoApplyResult.INELIGIBLE)
                ContactPhotoApplyTrace.record(ContactPhotoApplyStage.WRITE, ContactPhotoApplyResult.STARTED)
                stream.write(bytes)
                stream.flush()
            }
        }
        // Closing display_photo finishes the pipe, not the provider's asynchronous commit.
        // Only recite the raw contact; never repeat the write while awaiting confirmation.
        repeat(31) { attempt ->
            requirePermissions()
            try {
                val verified = revalidateTarget(target) ?: return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.VERIFY, ContactPhotoApplyResult.IDENTITY_CHANGED)
                if (dataFingerprint(target.rawContactId) != before) return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.VERIFY, ContactPhotoApplyResult.FIELDS_CHANGED)
                val writtenBytes = verified.photo
                val written = writtenBytes?.toContactPhotoImageInfo()
                if (written != null && contactPhotoReadbackMatches(bytes, writtenBytes)) {
                    if (previousInfo != null && !allowSmaller() &&
                        (written.width < previousInfo.width || written.height < previousInfo.height))
                        return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.VERIFY,ContactPhotoApplyResult.SMALLER)
                    ContactPhotoApplyTrace.record(ContactPhotoApplyStage.VERIFY, ContactPhotoApplyResult.SUCCESS)
                    return true
                }
                // A still-visible previous photo is pending; unrelated content is a failure.
                if (!sameNullablePhoto(target.photo, writtenBytes)) return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.VERIFY, ContactPhotoApplyResult.CONTENT_MISMATCH)
            } catch (_: IOException) {
                // A row/file transition may temporarily be unreadable. It is never absence or success.
            }
            if (attempt < 30) delay(100)
        }
        return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.VERIFY, ContactPhotoApplyResult.TIMEOUT)
    }

    fun normalizeMatchedNumber(target: WhatsAppAvatarContactTarget, canWrite: () -> Boolean = { true }): String? {
        requirePermissions()
        val latest = revalidateTarget(target) ?: return null
        if (latest.storedNumber != target.storedNumber) return null
        val dataId = target.phoneDataId ?: return null
        val stored = target.storedNumber ?: return null
        val matched = target.matchedNumber ?: return null
        val e164 = safeE164Replacement(context, stored, matched, numberRegion()) ?: return null
        if (stored == e164) return null
        if (!canWrite()) return null
        val count = resolver.update(
            ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId),
            ContentValues().apply { put(ContactsContract.CommonDataKinds.Phone.NUMBER, e164) },
            ContactsContract.Data.RAW_CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE +
                " = ? AND " + ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?",
            arrayOf(target.rawContactId.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, stored),
        )
        if (count != 1) return null
        val verified = resolver.query(
            ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId),
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        return e164.takeIf { verified == it && revalidateTarget(target) != null }
    }

    @Suppress("DEPRECATION")
    private fun resolveGoogleTarget(
        contactId: Long,
        accountName: String,
        matchedNumber: String?,
        strength: WhatsAppAvatarMatchStrength,
    ): WhatsAppAvatarContactTarget? {
        val rawIds = mutableListOf<Long>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND " +
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND " +
                "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND " +
                "${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(contactId.toString(), GOOGLE_ACCOUNT_TYPE, accountName),
            null,
        )?.use { cursor -> while (cursor.moveToNext()) rawIds += cursor.getLong(0) }
        if (rawIds.size != 1) return null
        val rawContactId = rawIds.single()
        val phoneRows = readPhoneRows(rawContactId)
        val matchedRow = when (strength) {
            WhatsAppAvatarMatchStrength.ExactPhone -> phoneRows.filter {
                normalizedContactNumber(it.number, numberRegion()) ==
                    normalizedContactNumber(matchedNumber.orEmpty(), numberRegion())
            }.singleOrNull() ?: return null
            else -> null
        }
        val name = resolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }.orEmpty()
        return WhatsAppAvatarContactTarget(
            contactId = contactId,
            rawContactId = rawContactId,
            phoneDataId = matchedRow?.dataId,
            displayName = name,
            storedNumber = matchedRow?.number,
            matchedNumber = matchedNumber,
            accountName = accountName,
            photo = readPhoto(rawContactId),
            matchStrength = strength,
            displayNumber = matchedRow?.number ?: phoneRows.firstOrNull()?.number,
        )
    }

    fun candidates(number: String?, name: String?, account: String): List<WhatsAppAvatarContactTarget> {
        requirePermissions()
        val ids = if (number != null) exactContactIds(number) else linkedSetOf<Long>().apply {
            if (!name.isNullOrBlank()) resolver.query(ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID), ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " = ?",
                arrayOf(name.trim().removePrefix("~").trim()), null)?.use { c ->
                while (c.moveToNext()) add(c.getLong(0))
            }
        }
        return ids.mapNotNull { resolveGoogleTarget(it, account, number,
            if (number == null) WhatsAppAvatarMatchStrength.UniqueName else WhatsAppAvatarMatchStrength.ExactPhone) }
    }
    fun chooseContact(contactId: Long, account: String): WhatsAppAvatarContactTarget? {
        requirePermissions()
        return resolveGoogleTarget(contactId, account, null, WhatsAppAvatarMatchStrength.UserSelected)
    }
    fun revalidateTarget(target: WhatsAppAvatarContactTarget): WhatsAppAvatarContactTarget? {
        requirePermissions()
        val current = if (target.matchStrength == WhatsAppAvatarMatchStrength.UserSelected)
            chooseContact(target.contactId, target.accountName)
            else if (target.matchedNumber != null) resolveByPhone(target.matchedNumber, target.accountName)
            else resolveByUniqueName(target.displayName, target.accountName)
        return current?.takeIf { it.contactId == target.contactId && it.rawContactId == target.rawContactId &&
            it.phoneDataId == target.phoneDataId }
    }
    fun emails(target: WhatsAppAvatarContactTarget): List<String> {
        requirePermissions()
        if (revalidateTarget(target) == null) return emptyList()
        val emails = linkedSetOf<String>()
        resolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            ContactsContract.Data.RAW_CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
            arrayOf(target.rawContactId.toString(), ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE), null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.trim()?.takeIf { it.contains("@") }?.let { emails.add(it.lowercase(java.util.Locale.ROOT)) }
        }
        return emails.toList()
    }
    fun accountTargets(account: String): List<WhatsAppAvatarContactTarget> {
        requirePermissions()
        val ids = linkedSetOf<Long>()
        resolver.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            ContactsContract.RawContacts.ACCOUNT_TYPE + " = ? AND " + ContactsContract.RawContacts.ACCOUNT_NAME +
                " = ? AND " + ContactsContract.RawContacts.DELETED + " = 0", arrayOf("com.google", account), null)?.use { c ->
            while (c.moveToNext()) ids.add(c.getLong(0))
        }
        return ids.mapNotNull { chooseContact(it, account) }
    }
    fun accountIdentities(account: String): List<Pair<Long, Long>> {
        requirePermissions()
        return buildList {
            resolver.query(ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.CONTACT_ID),
                ContactsContract.RawContacts.ACCOUNT_TYPE + " = ? AND " + ContactsContract.RawContacts.ACCOUNT_NAME +
                    " = ? AND " + ContactsContract.RawContacts.DELETED + " = 0", arrayOf("com.google", account),
                ContactsContract.RawContacts._ID + " ASC")?.use { cursor ->
                while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getLong(1))
            }
        }
    }
    fun automaticFields(target: WhatsAppAvatarContactTarget) = dataFingerprint(target.rawContactId)
    fun uniqueEmail(target: WhatsAppAvatarContactTarget, email: String): Boolean {
        if (revalidateTarget(target) == null) return false
        val ids = linkedSetOf<Long>()
        resolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.CONTACT_ID),
            ContactsContract.Data.MIMETYPE + " = ? AND LOWER(" + ContactsContract.CommonDataKinds.Email.ADDRESS + ") = ?",
            arrayOf(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, email.lowercase(Locale.ROOT)), null)?.use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getLong(0))
        } ?: return false
        return ids == setOf(target.contactId)
    }
    fun exactNumbers(target: WhatsAppAvatarContactTarget): List<WhatsAppAvatarContactTarget> {
        return readPhoneRows(target.rawContactId).mapNotNull { row ->
            val normalized = normalizedContactNumber(row.number, numberRegion()) ?: return@mapNotNull null
            resolveByPhone(normalized, target.accountName)?.takeIf { it.contactId == target.contactId }
        }.distinctBy { it.phoneDataId }
    }
    fun businessInfo(target: WhatsAppAvatarContactTarget): Pair<String, String?>? {
        requirePermissions()
        if (revalidateTarget(target) == null) return null
        val companies = linkedSetOf<String>(); val websites = linkedSetOf<String>()
        resolver.query(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
            ContactsContract.Data.RAW_CONTACT_ID + " = ?", arrayOf(target.rawContactId.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                val value = c.getString(1)?.trim()?.takeIf(String::isNotBlank) ?: continue
                when (c.getString(0)) {
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> companies.add(value)
                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> websites.add(value)
                }
            }
        }
        val company = companies.singleOrNull() ?: return null
        return company to websites.mapNotNull(OfficialCompanyLogoSource::publicOrigin).distinct().singleOrNull()
    }
    private fun exactContactIds(phoneNumber: String): LinkedHashSet<Long> {
        val region = numberRegion()
        val expected = normalizedContactNumber(phoneNumber, region) ?: return linkedSetOf()
        val ids = linkedSetOf<Long>()
        resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null)?.use { c -> while (c.moveToNext()) {
                if (normalizedContactNumber(c.getString(1).orEmpty(), region) == expected) ids.add(c.getLong(0))
            } }
        return ids
    }
    private fun dataFingerprint(rawId: Long): List<List<String?>> {
        val columns = arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4, ContactsContract.Data.DATA5, ContactsContract.Data.DATA6,
            ContactsContract.Data.DATA7, ContactsContract.Data.DATA8, ContactsContract.Data.DATA9,
            ContactsContract.Data.DATA10, ContactsContract.Data.DATA11, ContactsContract.Data.DATA12,
            ContactsContract.Data.DATA13, ContactsContract.Data.DATA14, ContactsContract.Data.DATA15)
        return buildList {
            resolver.query(ContactsContract.Data.CONTENT_URI, columns,
                ContactsContract.Data.RAW_CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " != ?",
                arrayOf(rawId.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE),
                ContactsContract.Data._ID + " ASC")?.use { c -> while (c.moveToNext()) {
                    add(columns.indices.map { i -> if (c.isNull(i)) null else if (c.getType(i) == android.database.Cursor.FIELD_TYPE_BLOB)
                        java.util.Base64.getEncoder().encodeToString(c.getBlob(i)) else c.getString(i) })
                } } ?: throw IOException("Contact fields lookup unavailable")
        }
    }
    private fun readPhoneRows(rawContactId: Long): List<PhoneRow> {
        val rows = mutableListOf<PhoneRow>()
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) rows += PhoneRow(cursor.getLong(0), cursor.getString(1).orEmpty())
        }
        return rows
    }

    private fun readPhoto(rawContactId: Long): ByteArray? {
        var fileId: Long? = null
        var thumbnail: ByteArray? = null
        val photoCursor = resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Photo.PHOTO_FILE_ID,
                ContactsContract.CommonDataKinds.Photo.PHOTO,
            ),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE),
            ContactsContract.Data.IS_SUPER_PRIMARY + " DESC, " + ContactsContract.Data._ID + " ASC",
        ) ?: throw IOException("Contact photo lookup unavailable")
        photoCursor.use { cursor ->
            if (cursor.moveToFirst()) {
                fileId = if (cursor.isNull(0)) null else cursor.getLong(0)
                thumbnail = if (cursor.isNull(1)) null else cursor.getBlob(1)
            }
        }
        return fileId?.let {
            val stream = resolver.openInputStream(ContentUris.withAppendedId(ContactsContract.DisplayPhoto.CONTENT_URI, it))
                ?: throw IOException("Contact photo file unavailable")
            stream.use { it.readBytes() }
        } ?: thumbnail
    }

    private fun requirePermissions() {
        val missing = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            .any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing) throw SecurityException("Contacts permission is missing")
    }

    private data class PhoneRow(val dataId: Long, val number: String)

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}


