package dev.goodwy.rphone.controller.util

import android.content.Context
import android.content.Intent

internal class PlatformGoogleContactsAuthorization(
    @Suppress("UNUSED_PARAMETER") context: Context,
) : GoogleContactsAuthorization {
    override val isAvailable: Boolean = false

    override suspend fun authorize(accountName: String?): GoogleContactsAuthorizationResult =
        GoogleContactsAuthorizationResult.Unavailable

    override fun completeAuthorization(
        data: Intent?,
        requestedAccountName: String?,
    ): GoogleContactsAuthorizationResult = GoogleContactsAuthorizationResult.Unavailable

    override suspend fun revoke(accountName: String): Boolean = true
}
