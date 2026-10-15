package dev.goodwy.rphone.controller

import android.app.Activity
import android.os.Build

/** Used only after the terminal Telecom guard; ordinary Back keeps platform navigation. */
internal fun Activity.finishCallTaskWithoutTransition() {
    // Window flags disappear with the task. Changing lock-screen visibility first
    // can reveal the underlying window before this translucent window is removed.
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
    finishAndRemoveTask()
    if (Build.VERSION.SDK_INT < 34) {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
