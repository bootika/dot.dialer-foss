package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat

private const val CALLING_ACCOUNT_TAG = "CallingAccountUtils"

data class CallingAccountInfo(
    val handle: PhoneAccountHandle,
    val key: String,
    val label: String,
    val description: String?,
    val highlightColor: Int,
    val icon: Icon?,
    val isSim: Boolean,
)

/**
 * Stable persisted identity for a Telecom calling account. Unlike a list index, this survives
 * reordering SIMs and adding/removing third-party calling accounts.
 */
fun PhoneAccountHandle.toPreferenceKey(): String =
    "${Uri.encode(componentName.flattenToString())}|${Uri.encode(id)}"

fun callingAccountHandleFromKey(key: String?): PhoneAccountHandle? {
    if (key.isNullOrBlank()) return null
    val separator = key.indexOf('|')
    if (separator <= 0) return null
    val component = ComponentName.unflattenFromString(Uri.decode(key.substring(0, separator)))
        ?: return null
    val id = Uri.decode(key.substring(separator + 1))
    if (id.isBlank()) return null
    return PhoneAccountHandle(component, id)
}

fun getCallingAccounts(context: Context): List<CallingAccountInfo> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
        PackageManager.PERMISSION_GRANTED
    ) return emptyList()

    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        ?: return emptyList()
    return try {
        telecom.callCapablePhoneAccounts.mapNotNull { handle ->
            val account = telecom.loadPhoneAccount(handle) ?: return@mapNotNull null
            if (!account.isEnabled) return@mapNotNull null
            CallingAccountInfo(
                handle = handle,
                key = handle.toPreferenceKey(),
                label = account.label?.toString()?.takeIf(String::isNotBlank)
                    ?: handle.id,
                description = account.shortDescription?.toString()?.takeIf(String::isNotBlank),
                highlightColor = account.highlightColor,
                icon = account.icon,
                isSim = account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION),
            )
        }.distinctBy(CallingAccountInfo::key)
    } catch (error: SecurityException) {
        Log.w(CALLING_ACCOUNT_TAG, "Permission denied while reading calling accounts", error)
        emptyList()
    } catch (error: RuntimeException) {
        Log.w(CALLING_ACCOUNT_TAG, "Android Telecom could not enumerate calling accounts", error)
        emptyList()
    }
}

fun loadPhoneAccount(context: Context, handle: PhoneAccountHandle): PhoneAccount? {
    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return null
    return telecom.loadPhoneAccount(handle)
}

fun loadCallingAccountIcon(context: Context, icon: Icon?): Drawable? = try {
    icon?.loadDrawable(context)
} catch (error: RuntimeException) {
    Log.w(CALLING_ACCOUNT_TAG, "Android could not load a calling-account icon", error)
    null
}

private fun TelecomManager.loadPhoneAccount(handle: PhoneAccountHandle): PhoneAccount? = try {
    getPhoneAccount(handle)
} catch (error: SecurityException) {
    Log.w(CALLING_ACCOUNT_TAG, "Permission denied while reading a calling account", error)
    null
} catch (error: RuntimeException) {
    Log.w(CALLING_ACCOUNT_TAG, "Android Telecom could not read a calling account", error)
    null
}

fun resolveCallingAccount(
    context: Context,
    key: String?,
    accounts: List<CallingAccountInfo> = getCallingAccounts(context),
): PhoneAccountHandle? {
    if (key.isNullOrBlank()) return null
    return accounts.firstOrNull { it.key == key }?.handle
        ?: callingAccountHandleFromKey(key)?.takeIf { decoded ->
            accounts.any { it.handle == decoded }
        }
        // Migration from the old implementation, which stored only PhoneAccountHandle.id.
        ?: accounts.firstOrNull { it.handle.id == key }?.handle
}
