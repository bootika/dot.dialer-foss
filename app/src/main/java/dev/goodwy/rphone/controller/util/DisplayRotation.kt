package dev.goodwy.rphone.controller.util

import android.content.Context
import android.os.Build
import android.view.Surface
import android.view.WindowManager

/** Returns display rotation across the app's supported API 29+ range. */
fun Context.currentDisplayRotation(): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return display.rotation
    }

    @Suppress("DEPRECATION")
    return getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation
        ?: Surface.ROTATION_0
}
