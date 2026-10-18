package dev.goodwy.rphone.controller.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/** Disabled in the manifest as well as the preference; restore and cold starts share this owner. */
internal object SelectedTextDialerIntegration {
    private fun component(context: Context) = ComponentName(context.packageName, "dev.goodwy.rphone.SelectedTextDialer")
    fun isEnabled(context: Context): Boolean = try {
        context.packageManager.getComponentEnabledSetting(component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } catch (failure: RuntimeException) {
        Log.w("SelectedTextDialer", "Selection integration unavailable: ${failure.javaClass.simpleName}")
        false
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean = try {
        if (isEnabled(context) != enabled) context.packageManager.setComponentEnabledSetting(component(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP)
        isEnabled(context) == enabled
    } catch (failure: RuntimeException) {
        Log.w("SelectedTextDialer", "Unable to update selection integration", failure)
        false
    }
}
