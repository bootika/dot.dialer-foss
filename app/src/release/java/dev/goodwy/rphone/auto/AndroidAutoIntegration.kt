package dev.goodwy.rphone.auto

import android.content.Context

/** Google Play does not yet accept Android Auto Calling apps in Production/Open Testing. */
internal object AndroidAutoIntegration {
    fun isAvailable(): Boolean = false

    fun isEnabled(context: Context): Boolean = false

    fun setEnabled(context: Context, enabled: Boolean) = Unit
}
