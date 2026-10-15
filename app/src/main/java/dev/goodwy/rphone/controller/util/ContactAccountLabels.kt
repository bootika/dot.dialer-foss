package dev.goodwy.rphone.controller.util

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.Log

/** Display metadata only: never resolves a provider from the account's email/domain. */
internal fun contactAccountProviderName(account: Account, systemLabel: String?): String {
    val label = systemLabel?.trim()?.takeIf { it.isNotEmpty() && '@' !in it }
    if (label != null) return label
    return ContactUtils.getKnownAccountProviderName(account) ?: when (account.type) {
        "com.google.android.apps.tachyon" -> "Meet"
        else -> account.type
    }
}

/** Local authenticator resources; no account creation, tokens, contacts writes or network. */
internal fun loadContactAccountProviderLabels(context: Context): Map<String, String> {
    val authenticators = try {
        AccountManager.get(context).authenticatorTypes
    } catch (error: SecurityException) {
        Log.w("ContactAccountLabels", "Account provider metadata is unavailable", error)
        return emptyMap()
    }
    return authenticators.mapNotNull { description ->
        val label = try {
            if (description.labelId == 0) null else context.packageManager
                .getResourcesForApplication(description.packageName)
                .getText(description.labelId).toString().trim().takeIf(String::isNotEmpty)
        } catch (error: PackageManager.NameNotFoundException) {
            Log.w("ContactAccountLabels", "Account provider package is unavailable", error)
            null
        } catch (error: Resources.NotFoundException) {
            Log.w("ContactAccountLabels", "Account provider label resource is unavailable", error)
            null
        } catch (error: SecurityException) {
            Log.w("ContactAccountLabels", "Account provider label is inaccessible", error)
            null
        }
        label?.let { description.type to it }
    }.toMap()
}
