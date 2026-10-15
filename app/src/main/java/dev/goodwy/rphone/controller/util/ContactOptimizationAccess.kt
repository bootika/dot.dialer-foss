package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import androidx.core.content.ContextCompat

internal object ContactOptimizationAccess {
    // The legacy PackageManager overload is required on the supported API29 devices.
    @Suppress("DEPRECATION")
    fun supported(context: Context): Boolean {
        if (!dev.goodwy.rphone.BuildConfig.NOTIFICATION_AVATARS_AVAILABLE) return false
        return try {
            context.packageManager.getServiceInfo(ComponentName(context, WhatsAppAvatarNotificationListener::class.java), 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
    }
    fun granted(context: Context): Boolean = supported(context) && context.getSystemService(NotificationManager::class.java)
        ?.isNotificationListenerAccessGranted(ComponentName(context, WhatsAppAvatarNotificationListener::class.java)) == true
    fun contactsGranted(context: Context): Boolean = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    fun open(context: Context) {
        if (!supported(context)) return
        val component = ComponentName(context, WhatsAppAvatarNotificationListener::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
            else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
    }
}
