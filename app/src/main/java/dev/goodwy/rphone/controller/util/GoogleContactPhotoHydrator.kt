package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

private const val GOOGLE_ACCOUNT_TYPE = "com.google"
private const val MAX_APPROVED_PHOTO_BYTES = 20 * 1024 * 1024
private const val VERIFICATION_ATTEMPTS = 20
private const val VERIFICATION_DELAY_MILLIS = 100L

internal class GoogleContactPhotoHydrator(
    private val context: Context,
    private val resolver: ContentResolver = context.contentResolver,
) : GoogleContactPhotoStore {
    override suspend fun snapshot(
        phoneNumber: String,
        googleAccountName: String,
    ): GoogleContactPhotoSnapshot = withContext(Dispatchers.IO) {
        val target = resolveTarget(phoneNumber, googleAccountName)
        readSnapshot(target)
    }

    override suspend fun hydrate(
        request: GoogleContactPhotoHydrationRequest,
    ): GoogleContactPhotoHydrationResult = withContext(Dispatchers.IO) {
        validateRequest(request)?.let { return@withContext GoogleContactPhotoHydrationResult.Rejected(it) }
        missingPermissions().takeIf { it.isNotEmpty() }?.let {
            return@withContext GoogleContactPhotoHydrationResult.PermissionMissing(it)
        }

        if (request.approvedReferencePhoto == null || !request.canWrite())
            return@withContext GoogleContactPhotoHydrationResult.Rejected(GoogleContactPhotoHydrationRejection.ApprovalRequired)

        val approvedInfo = request.approvedPhoto.toContactPhotoImageInfo()
            ?: return@withContext GoogleContactPhotoHydrationResult.Rejected(
                GoogleContactPhotoHydrationRejection.InvalidPhoto,
            )

        try {
            val target = resolveTarget(request.phoneNumber, request.googleAccountName)
            if ((request.expectedContactId != null && target.contactId != request.expectedContactId) ||
                (request.expectedRawContactId != null && target.rawContactId != request.expectedRawContactId)
            ) return@withContext GoogleContactPhotoHydrationResult.Rejected(GoogleContactPhotoHydrationRejection.IdentityChanged)
            val before = readSnapshot(target)
            val current = before.highResolutionPhoto ?: before.thumbnail
            if (!sameNullablePhoto(current, request.approvedReferencePhoto) ||
                !sameNullablePhoto(request.approvedReferencePhoto, request.approvedPhoto) ||
                !isMeaningfulPhotoUpgrade(request.approvedReferencePhoto, request.approvedPhoto))
                return@withContext GoogleContactPhotoHydrationResult.Rejected(GoogleContactPhotoHydrationRejection.PhotoContentMismatch)
            val currentThumbnail = before.thumbnailInfo
            if (currentThumbnail != null &&
                approvedInfo.width.toLong() * approvedInfo.height <=
                currentThumbnail.width.toLong() * currentThumbnail.height
            ) {
                return@withContext GoogleContactPhotoHydrationResult.Rejected(
                    GoogleContactPhotoHydrationRejection.HighResolutionPhotoNotLarger,
                )
            }
            val existingHighResolution = before.highResolutionInfo
            if (before.photoFileId != null && existingHighResolution != null) {
                val distance = perceptualHashDistance(
                    approvedInfo.perceptualHash,
                    existingHighResolution.perceptualHash,
                )
                val existingPixels = existingHighResolution.width.toLong() * existingHighResolution.height
                val approvedPixels = approvedInfo.width.toLong() * approvedInfo.height
                if (existingPixels > approvedPixels ||
                    (existingPixels == approvedPixels && distance <= request.maxPerceptualHashDistance)
                ) {
                    return@withContext GoogleContactPhotoHydrationResult.AlreadyHydrated(
                        snapshot = before,
                        approvedPhotoInfo = approvedInfo,
                        perceptualHashDistance = distance,
                    )
                }
            }

            if (!request.canWrite()) return@withContext GoogleContactPhotoHydrationResult.Rejected(
                GoogleContactPhotoHydrationRejection.ApprovalRequired)
            writeDisplayPhoto(target.rawContactId, request.approvedPhoto)
            val after = awaitVerifiedSnapshot(target, approvedInfo, request.maxPerceptualHashDistance)
            verifyIdentity(before, after)?.let {
                return@withContext GoogleContactPhotoHydrationResult.Rejected(it)
            }
            if (after.photoFileId == null) {
                return@withContext GoogleContactPhotoHydrationResult.Rejected(
                    GoogleContactPhotoHydrationRejection.PhotoFileMissing,
                )
            }
            val highResolutionInfo = after.highResolutionInfo
                ?: return@withContext GoogleContactPhotoHydrationResult.Rejected(
                    GoogleContactPhotoHydrationRejection.HighResolutionPhotoMissing,
                )
            val thumbnailInfo = after.thumbnailInfo
            if (thumbnailInfo != null &&
                highResolutionInfo.width.toLong() * highResolutionInfo.height <=
                thumbnailInfo.width.toLong() * thumbnailInfo.height
            ) {
                return@withContext GoogleContactPhotoHydrationResult.Rejected(
                    GoogleContactPhotoHydrationRejection.HighResolutionPhotoNotLarger,
                )
            }
            val distance = perceptualHashDistance(
                approvedInfo.perceptualHash,
                highResolutionInfo.perceptualHash,
            )
            if (distance > request.maxPerceptualHashDistance) {
                return@withContext GoogleContactPhotoHydrationResult.Rejected(
                    GoogleContactPhotoHydrationRejection.PhotoContentMismatch,
                )
            }
            GoogleContactPhotoHydrationResult.Hydrated(
                before = before,
                after = after,
                approvedPhotoInfo = approvedInfo,
                perceptualHashDistance = distance,
            )
        } catch (error: HydrationResolutionException) {
            GoogleContactPhotoHydrationResult.Rejected(error.rejection)
        } catch (error: SecurityException) {
            GoogleContactPhotoHydrationResult.PermissionMissing(missingPermissions())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            GoogleContactPhotoHydrationResult.Failed(
                message = error.message ?: "Unable to hydrate the Google contact photo",
                cause = error,
            )
        }
    }

    private fun validateRequest(
        request: GoogleContactPhotoHydrationRequest,
    ): GoogleContactPhotoHydrationRejection? = when {
        request.phoneNumber.isBlank() -> GoogleContactPhotoHydrationRejection.EmptyPhoneNumber
        request.googleAccountName.isBlank() -> GoogleContactPhotoHydrationRejection.EmptyAccountName
        request.approvedPhoto.isEmpty() -> GoogleContactPhotoHydrationRejection.EmptyPhoto
        request.approvedPhoto.size > MAX_APPROVED_PHOTO_BYTES ->
            GoogleContactPhotoHydrationRejection.PhotoTooLarge
        else -> null
    }

    private fun missingPermissions(): Set<String> = buildSet {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            add(Manifest.permission.READ_CONTACTS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            add(Manifest.permission.WRITE_CONTACTS)
        }
    }

    private fun resolveTarget(phoneNumber: String, googleAccountName: String): HydrationTarget {
        val normalizedNumber = PhoneNumberUtils.normalizeNumber(phoneNumber)
        if (normalizedNumber.isBlank()) {
            throw HydrationResolutionException(GoogleContactPhotoHydrationRejection.EmptyPhoneNumber)
        }
        val matchingContactIds = linkedSetOf<Long>()
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalizedNumber),
        )
        resolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.CONTACT_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            val contactIdIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.CONTACT_ID)
            while (cursor.moveToNext()) matchingContactIds += cursor.getLong(contactIdIndex)
        }
        if (matchingContactIds.isEmpty()) {
            throw HydrationResolutionException(GoogleContactPhotoHydrationRejection.ContactNotFound)
        }

        val googleTargets = matchingContactIds.flatMap { contactId ->
            queryGoogleRawContacts(contactId, googleAccountName).map { rawContactId ->
                HydrationTarget(
                    contactId = contactId,
                    rawContactId = rawContactId,
                    matchingContactIds = matchingContactIds.sorted(),
                    accountName = googleAccountName,
                )
            }
        }
        if (googleTargets.isEmpty()) {
            throw HydrationResolutionException(
                GoogleContactPhotoHydrationRejection.GoogleRawContactNotFound,
            )
        }
        if (googleTargets.map { it.contactId }.distinct().size > 1) {
            throw HydrationResolutionException(GoogleContactPhotoHydrationRejection.AmbiguousContact)
        }
        if (googleTargets.size > 1) {
            throw HydrationResolutionException(
                GoogleContactPhotoHydrationRejection.AmbiguousGoogleRawContact,
            )
        }
        return googleTargets.single()
    }

    private fun queryGoogleRawContacts(contactId: Long, googleAccountName: String): List<Long> {
        val result = mutableListOf<Long>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND " +
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND " +
                "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND " +
                "${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(contactId.toString(), GOOGLE_ACCOUNT_TYPE, googleAccountName),
            ContactsContract.RawContacts._ID + " ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            while (cursor.moveToNext()) result += cursor.getLong(idIndex)
        }
        return result
    }

    private fun readSnapshot(target: HydrationTarget): GoogleContactPhotoSnapshot {
        val identity = readIdentity(target.contactId)
        val rawContactIds = readRawContactIds(target.contactId)
        val photo = readPhotoRow(target.rawContactId)
        val highResolution = photo.photoFileId?.let(::readHighResolutionPhoto)
        return GoogleContactPhotoSnapshot(
            contactId = target.contactId,
            rawContactId = target.rawContactId,
            photoDataRowId = photo.dataRowId,
            displayName = identity.displayName,
            phoneNumbers = identity.phoneNumbers,
            matchingContactIds = target.matchingContactIds,
            rawContactIds = rawContactIds,
            accountName = target.accountName,
            accountType = GOOGLE_ACCOUNT_TYPE,
            photoFileId = photo.photoFileId,
            thumbnail = photo.thumbnail,
            highResolutionPhoto = highResolution,
        )
    }

    private fun readIdentity(contactId: Long): ContactIdentity {
        var displayName = ""
        resolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) displayName = cursor.getString(0).orEmpty()
        }
        val phoneNumbers = mutableListOf<String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            ContactsContract.CommonDataKinds.Phone.NUMBER + " ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) phoneNumbers += cursor.getString(0).orEmpty()
        }
        return ContactIdentity(displayName, phoneNumbers.sorted())
    }

    private fun readRawContactIds(contactId: Long): List<Long> {
        val ids = mutableListOf<Long>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND " +
                "${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()),
            ContactsContract.RawContacts._ID + " ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        return ids
    }

    private fun readPhotoRow(rawContactId: Long): ContactPhotoRow {
        var row = ContactPhotoRow(null, null, null)
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.CommonDataKinds.Photo.PHOTO_FILE_ID,
                ContactsContract.CommonDataKinds.Photo.PHOTO,
            ),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                rawContactId.toString(),
                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
            ),
            ContactsContract.Data.IS_SUPER_PRIMARY + " DESC, " +
                ContactsContract.Data.IS_PRIMARY + " DESC, " +
                ContactsContract.Data._ID + " ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                row = ContactPhotoRow(
                    dataRowId = cursor.getLong(0),
                    photoFileId = if (cursor.isNull(1)) null else cursor.getLong(1),
                    thumbnail = if (cursor.isNull(2)) null else cursor.getBlob(2),
                )
            }
        }
        return row
    }

    private fun readHighResolutionPhoto(photoFileId: Long): ByteArray? {
        // An aggregate may display a WhatsApp photo. Verify this Google raw contact's own asset.
        val photoUri = ContentUris.withAppendedId(ContactsContract.DisplayPhoto.CONTENT_URI, photoFileId)
        return resolver.openInputStream(photoUri)?.use { it.readBytes() }
    }

    private fun writeDisplayPhoto(rawContactId: Long, approvedPhoto: ByteArray) {
        val rawContactUri = ContentUris.withAppendedId(
            ContactsContract.RawContacts.CONTENT_URI,
            rawContactId,
        )
        val displayPhotoUri = Uri.withAppendedPath(
            rawContactUri,
            ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY,
        )
        val descriptor = resolver.openAssetFileDescriptor(displayPhotoUri, "rw")
            ?: throw IOException("Contacts Provider did not open the display photo")
        descriptor.use { asset ->
            asset.createOutputStream().use { output ->
                output.write(approvedPhoto)
                output.flush()
            }
        }
    }

    private suspend fun awaitVerifiedSnapshot(
        target: HydrationTarget,
        approved: ContactPhotoImageInfo,
        maxHashDistance: Int,
    ): GoogleContactPhotoSnapshot {
        var latest = readSnapshot(target)
        repeat(VERIFICATION_ATTEMPTS - 1) {
            val image = latest.highResolutionInfo
            val thumbnail = latest.thumbnailInfo
            if (latest.photoFileId != null && image != null &&
                (thumbnail == null || image.width.toLong() * image.height > thumbnail.width.toLong() * thumbnail.height) &&
                perceptualHashDistance(image.perceptualHash, approved.perceptualHash) <= maxHashDistance
            ) return latest
            delay(VERIFICATION_DELAY_MILLIS)
            latest = readSnapshot(target)
        }
        return latest
    }

    private fun verifyIdentity(
        before: GoogleContactPhotoSnapshot,
        after: GoogleContactPhotoSnapshot,
    ): GoogleContactPhotoHydrationRejection? =
        if (
            before.contactId != after.contactId ||
            before.rawContactId != after.rawContactId ||
            before.displayName != after.displayName ||
            before.phoneNumbers != after.phoneNumbers ||
            before.matchingContactIds != after.matchingContactIds ||
            before.rawContactIds != after.rawContactIds ||
            before.accountName != after.accountName ||
            before.accountType != after.accountType
        ) {
            GoogleContactPhotoHydrationRejection.IdentityChanged
        } else {
            null
        }
}

private data class HydrationTarget(
    val contactId: Long,
    val rawContactId: Long,
    val matchingContactIds: List<Long>,
    val accountName: String,
)

private data class ContactIdentity(
    val displayName: String,
    val phoneNumbers: List<String>,
)

private data class ContactPhotoRow(
    val dataRowId: Long?,
    val photoFileId: Long?,
    val thumbnail: ByteArray?,
)

private class HydrationResolutionException(
    val rejection: GoogleContactPhotoHydrationRejection,
) : IllegalStateException(rejection.name)
