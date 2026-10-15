package dev.goodwy.rphone.view.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.core.view.WindowCompat

internal fun Context.systemBarActivity(): Activity? {
    var current = this
    val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Context, Boolean>())
    while (visited.add(current)) {
        if (current is Activity) return current
        current = (current as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}

/** Android calls this 'light bars': it means dark glyphs on a light background. */
internal fun applySystemBarIconAppearance(window: Window, darkTheme: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
    }
}
