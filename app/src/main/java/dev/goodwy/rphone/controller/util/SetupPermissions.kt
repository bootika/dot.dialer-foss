package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.R

internal data class SetupPermission(val name: String, val reason: Int, val required: Boolean = true)

/** Only actual platform grants authorize use; the setup marker only avoids optional prompt loops. */
internal object SetupPermissions {
    @android.annotation.SuppressLint("InlinedApi") // Permission strings only; API-parameter policy is tested for 29/31/33/36/37.
    fun steps(api: Int): List<SetupPermission> = buildList {
        add(SetupPermission(Manifest.permission.READ_CONTACTS, R.string.setup_contacts_read))
        add(SetupPermission(Manifest.permission.WRITE_CONTACTS, R.string.setup_contacts_write))
        add(SetupPermission(Manifest.permission.READ_CALL_LOG, R.string.setup_history_read))
        add(SetupPermission(Manifest.permission.WRITE_CALL_LOG, R.string.setup_history_write))
        add(SetupPermission(Manifest.permission.READ_PHONE_STATE, R.string.setup_phone_state))
        add(SetupPermission(Manifest.permission.CALL_PHONE, R.string.setup_phone_call))
        add(SetupPermission(Manifest.permission.ANSWER_PHONE_CALLS, R.string.setup_phone_answer))
        if (api >= 33) add(SetupPermission(Manifest.permission.POST_NOTIFICATIONS, R.string.setup_notifications, false))
        if (api >= 31) add(SetupPermission(Manifest.permission.BLUETOOTH_CONNECT, R.string.setup_bluetooth, false))
    }
    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun next(api: Int, granted: (String) -> Boolean, skipped: Set<String> = emptySet()): SetupPermission? =
        steps(api).firstOrNull { !granted(it.name) && (it.required || it.name !in skipped) }

    fun coreGranted(context: Context): Boolean = steps(Build.VERSION.SDK_INT).filter(SetupPermission::required)
        .all { granted(context, it.name) }

    fun needsSetup(context: Context, prefs: PreferenceManager): Boolean = !isAlreadyDefaultDialer(context) ||
        !coreGranted(context) || !prefs.getBoolean(PreferenceManager.KEY_PERMISSION_SETUP_REVIEWED, false)
}
