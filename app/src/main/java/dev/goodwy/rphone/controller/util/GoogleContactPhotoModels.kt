package dev.goodwy.rphone.controller.util

internal const val CONTACT_PHOTO_MAX_HASH_DISTANCE = 6

internal data class GoogleContactPhotoHydrationRequest(
    val phoneNumber: String,
    val googleAccountName: String,
    val approvedPhoto: ByteArray,
    val maxPerceptualHashDistance: Int = CONTACT_PHOTO_MAX_HASH_DISTANCE,
    val expectedContactId: Long? = null,
    val expectedRawContactId: Long? = null,
    val approvedReferencePhoto: ByteArray? = null,
    val canWrite: () -> Boolean = { false },
)

internal data class ContactPhotoImageInfo(
    val byteCount: Int,
    val width: Int,
    val height: Int,
    val perceptualHash: Long,
)

internal data class GoogleContactPhotoSnapshot(
    val contactId: Long,
    val rawContactId: Long,
    val photoDataRowId: Long?,
    val displayName: String,
    val phoneNumbers: List<String>,
    val matchingContactIds: List<Long>,
    val rawContactIds: List<Long>,
    val accountName: String,
    val accountType: String,
    val photoFileId: Long?,
    val thumbnail: ByteArray?,
    val highResolutionPhoto: ByteArray?,
) {
    val thumbnailInfo: ContactPhotoImageInfo?
        get() = thumbnail?.toContactPhotoImageInfo()

    val highResolutionInfo: ContactPhotoImageInfo?
        get() = highResolutionPhoto?.toContactPhotoImageInfo()
}

internal sealed interface GoogleContactPhotoHydrationResult {
    data class Hydrated(
        val before: GoogleContactPhotoSnapshot,
        val after: GoogleContactPhotoSnapshot,
        val approvedPhotoInfo: ContactPhotoImageInfo,
        val perceptualHashDistance: Int,
    ) : GoogleContactPhotoHydrationResult

    data class AlreadyHydrated(
        val snapshot: GoogleContactPhotoSnapshot,
        val approvedPhotoInfo: ContactPhotoImageInfo,
        val perceptualHashDistance: Int,
    ) : GoogleContactPhotoHydrationResult

    data class PermissionMissing(val permissions: Set<String>) : GoogleContactPhotoHydrationResult

    data class Rejected(val reason: GoogleContactPhotoHydrationRejection) :
        GoogleContactPhotoHydrationResult

    data class Failed(val message: String, val cause: Throwable? = null) :
        GoogleContactPhotoHydrationResult
}

internal enum class GoogleContactPhotoHydrationRejection {
    ApprovalRequired,
    EmptyPhoneNumber,
    EmptyAccountName,
    EmptyPhoto,
    PhotoTooLarge,
    InvalidPhoto,
    ContactNotFound,
    AmbiguousContact,
    GoogleRawContactNotFound,
    AmbiguousGoogleRawContact,
    IdentityChanged,
    PhotoFileMissing,
    HighResolutionPhotoMissing,
    HighResolutionPhotoNotLarger,
    PhotoContentMismatch,
}

/**
 * Hydrates the Android Contacts Provider display-photo file for one existing Google raw contact.
 *
 * This class deliberately does not create contacts or update data rows. It resolves the aggregate
 * contact by phone number, narrows it to one Google raw contact, writes only that raw contact's
 * `display_photo` asset and then independently re-reads the provider state.
 */
internal interface GoogleContactPhotoStore {
    suspend fun snapshot(
        phoneNumber: String,
        googleAccountName: String,
    ): GoogleContactPhotoSnapshot

    suspend fun hydrate(
        request: GoogleContactPhotoHydrationRequest,
    ): GoogleContactPhotoHydrationResult
}

