package dev.goodwy.rphone.recordings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo

/** Explicit bind participation only; approval and signer/UID authentication remain separate. */
internal object DictateBridgePermission {
    const val REQUIRED = android.Manifest.permission.INTERNET

    internal fun failure(
        declaredPermission: String?,
        permissionOwner: String?,
        protection: Int,
        granted: Boolean,
    ): String? = when {
        declaredPermission.isNullOrEmpty() -> null // Existing approved providers predate the OS gate.
        declaredPermission != REQUIRED || permissionOwner != "android" ||
            protection != PermissionInfo.PROTECTION_NORMAL -> "UNTRUSTED_SERVICE"
        !granted -> "BRIDGE_PERMISSION_REQUIRED"
        else -> null
    }

    fun check(context: Context) {
        val manager = context.packageManager
        val service = try {
            manager.getServiceInfo(ComponentName(DictateProtocol.PACKAGE, DictateProtocol.SERVICE), 0)
        } catch (_: PackageManager.NameNotFoundException) {
            throw DictateFailure("UNSUPPORTED")
        }
        val required = service.permission
        if (required.isNullOrEmpty()) return
        val permission = try { manager.getPermissionInfo(required, 0) }
        catch (_: PackageManager.NameNotFoundException) { throw DictateFailure("UNTRUSTED_SERVICE") }
        failure(required, permission.packageName, permission.protection,
            context.checkSelfPermission(required) == PackageManager.PERMISSION_GRANTED)
            ?.let { throw DictateFailure(it) }
    }
}