package dev.goodwy.rphone.auto

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.goodwy.rphone.BuildConfig

/** Runtime gate for the opt-in Android Auto surface in test builds. */
internal object AndroidAutoIntegration {
    fun isAvailable(): Boolean = BuildConfig.DEBUG

    fun isEnabled(context: Context): Boolean {
        if (!isAvailable()) return false
        return context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        if (!isAvailable()) return
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun component(context: Context): ComponentName =
        ComponentName(context, DotDialerCarAppService::class.java)
}
