package dev.goodwy.rphone.view.screen

import android.view.View
import dev.goodwy.rphone.controller.util.AndroidHaptics
import io.github.bootika.dotdialer.core.haptics.HapticIntent

/** A light tick means the handle is armed and can be released. */
internal fun performIncomingThresholdHaptic(view: View) {
    AndroidHaptics.performOnView(view, HapticIntent.SCROLL_TICK)
}

/** A distinct second pulse acknowledges that the requested action was dispatched. */
internal fun performIncomingCommitHaptic(view: View, answer: Boolean) {
    AndroidHaptics.performOnView(
        view,
        if (answer) HapticIntent.CONFIRM else HapticIntent.REJECT,
    )
}
