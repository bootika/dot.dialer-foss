package dev.goodwy.rphone.controller.util

import android.app.PendingIntent
import android.content.Intent

internal const val GOOGLE_CONTACTS_READ_SCOPE =
    "https://www.googleapis.com/auth/contacts.readonly"

internal object GoogleContactPhotoPreferences {
    const val ENABLED = "high_res_call_photos_enabled"
    const val ACCOUNT = "high_res_call_photos_account"
}

internal sealed interface GoogleContactsAuthorizationResult {
    data class Granted(
        val accountName: String,
        val accessToken: String,
    ) : GoogleContactsAuthorizationResult

    data class ConsentRequired(
        val pendingIntent: PendingIntent,
        val requestedAccountName: String?,
    ) : GoogleContactsAuthorizationResult

    data object Unavailable : GoogleContactsAuthorizationResult
    data class Failed(val reason: String) : GoogleContactsAuthorizationResult
}

/** Distribution-specific bridge to Google's incremental authorization API. */
internal interface GoogleContactsAuthorization {
    val isAvailable: Boolean

    suspend fun authorize(accountName: String? = null): GoogleContactsAuthorizationResult

    fun completeAuthorization(
        data: Intent?,
        requestedAccountName: String? = null,
    ): GoogleContactsAuthorizationResult

    suspend fun revoke(accountName: String): Boolean
}
