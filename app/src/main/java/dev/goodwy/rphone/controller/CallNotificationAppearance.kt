package dev.goodwy.rphone.controller

import android.app.Notification
import android.os.Build

/** Native presentation hint, not an exact secondary-button color or background override. */
internal fun Notification.Action.Builder.secondaryCallAction(): Notification.Action.Builder = apply {
    if (Build.VERSION.SDK_INT >= 37) setEmphasisHint(Notification.Action.EMPHASIS_SECONDARY)
}
