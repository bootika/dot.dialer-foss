package dev.goodwy.rphone.controller.license

import android.content.Context
import android.provider.Settings
import ro.dotdesign.support.SupportReference

/** Uses the existing scoped installation identity; it grants no access. */
object SupportIdentity {
    @JvmStatic
    @android.annotation.SuppressLint("HardwareIds")
    fun code(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (id.isNullOrBlank()) return ""
        return SupportReference.code("dialer", scopedLicenseInstallationId(id, context.packageName))
    }
}
