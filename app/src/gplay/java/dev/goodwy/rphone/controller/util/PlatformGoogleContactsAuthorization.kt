package dev.goodwy.rphone.controller.util

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class PlatformGoogleContactsAuthorization(context: Context) :
    GoogleContactsAuthorization {
    private val client = Identity.getAuthorizationClient(context.applicationContext)
    private val scope = Scope(GOOGLE_CONTACTS_READ_SCOPE)

    override val isAvailable: Boolean = true

    override suspend fun authorize(accountName: String?): GoogleContactsAuthorizationResult =
        runCatching {
            val builder = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(scope))
            if (accountName.isNullOrBlank()) {
                builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            } else {
                builder.setAccount(Account(accountName, "com.google"))
            }
            mapResult(client.authorize(builder.build()).await(), accountName)
        }.getOrElse { error ->
            GoogleContactsAuthorizationResult.Failed(
                error.message ?: "Google authorization failed",
            )
        }

    override fun completeAuthorization(
        data: Intent?,
        requestedAccountName: String?,
    ): GoogleContactsAuthorizationResult {
        if (data == null) {
            return GoogleContactsAuthorizationResult.Failed("Authorization was cancelled")
        }
        return runCatching {
            mapResult(client.getAuthorizationResultFromIntent(data), requestedAccountName)
        }.getOrElse { error ->
            GoogleContactsAuthorizationResult.Failed(
                error.message ?: "Google authorization failed",
            )
        }
    }

    override suspend fun revoke(accountName: String): Boolean = runCatching {
        val request = RevokeAccessRequest.builder()
            .setAccount(Account(accountName, "com.google"))
            .setScopes(listOf(scope))
            .build()
        client.revokeAccess(request).await()
        true
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun mapResult(
        result: AuthorizationResult,
        requestedAccountName: String?,
    ): GoogleContactsAuthorizationResult {
        if (result.hasResolution()) {
            val resolution = result.pendingIntent
                ?: return GoogleContactsAuthorizationResult.Failed(
                    "Google authorization did not provide a consent screen",
                )
            return GoogleContactsAuthorizationResult.ConsentRequired(
                pendingIntent = resolution,
                requestedAccountName = requestedAccountName,
            )
        }
        if (GOOGLE_CONTACTS_READ_SCOPE !in result.grantedScopes) {
            return GoogleContactsAuthorizationResult.Failed(
                "Google Contacts permission was not granted",
            )
        }
        val accountName = requestedAccountName
            ?: result.toGoogleSignInAccount()?.account?.name
            ?: return GoogleContactsAuthorizationResult.Failed(
                "The authorized Google account could not be identified",
            )
        val token = result.accessToken
            ?.takeIf(String::isNotBlank)
            ?: return GoogleContactsAuthorizationResult.Failed(
                "Google did not return an access token",
            )
        return GoogleContactsAuthorizationResult.Granted(accountName, token)
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val error = task.exception
        if (error != null) continuation.resumeWith(Result.failure(error))
        else continuation.resume(task.result)
    }
}
