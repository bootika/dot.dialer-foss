package dev.goodwy.rphone.controller.util

internal object WhatsAppAvatarSyncPreferences {
    const val ENABLED = "whatsapp_avatar_sync_enabled"
    const val NORMALIZE_NUMBERS = "whatsapp_avatar_sync_normalize_numbers"
    const val EXISTING_PHOTO_POLICY = "whatsapp_avatar_sync_existing_photo_policy"
}

internal enum class ExistingPhotoPolicy(val storedValue: String) {
    Ask("ask"),
    AlwaysReplace("always_replace"),
    NeverReplace("never_replace");

    companion object {
        fun fromStored(value: String?): ExistingPhotoPolicy =
            entries.firstOrNull { it.storedValue == value } ?: Ask
    }
}

internal enum class WhatsAppAvatarMatchStrength { ExactPhone, UniqueName, UserSelected, Ambiguous }

internal data class WhatsAppAvatarReviewItem(
    val id: String,
    val contactId: Long,
    val rawContactId: Long,
    val phoneDataId: Long?,
    val displayName: String,
    val matchedNumber: String?,
    val accountName: String,
    val candidateFileName: String,
    val previousFileName: String?,
    val candidateWidth: Int,
    val candidateHeight: Int,
    val previousWidth: Int?,
    val previousHeight: Int?,
    val sourcePackage: String,
    val matchStrength: WhatsAppAvatarMatchStrength,
    val createdAtMillis: Long,
    val originalWidth: Int = candidateWidth,
    val originalHeight: Int = candidateHeight,
    val source: String = "WhatsApp",
    val kind: String = "photo",
    val originalNumber: String? = null,
    val proposedNumber: String? = null,
    val displayNumber: String? = matchedNumber,
    val companyDomain: String? = null, val companyName: String? = null, val companyDiscovery: Boolean = false,
)

internal fun List<WhatsAppAvatarReviewItem>.contactPhotoProposals(): List<WhatsAppAvatarReviewItem> =
    filter { it.kind == "photo" || it.kind == "number" }

internal data class WhatsAppAvatarContactTarget(
    val contactId: Long,
    val rawContactId: Long,
    val phoneDataId: Long?,
    val displayName: String,
    val storedNumber: String?,
    val matchedNumber: String?,
    val accountName: String,
    val photo: ByteArray?,
    val matchStrength: WhatsAppAvatarMatchStrength,
    val displayNumber: String? = storedNumber,
)

