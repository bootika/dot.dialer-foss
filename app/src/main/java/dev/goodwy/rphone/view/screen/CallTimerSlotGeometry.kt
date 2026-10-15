package dev.goodwy.rphone.view.screen

import kotlin.math.ceil

/** Keeps the timer still while seconds change and expands it only once an hour is shown. */
internal fun callDurationSlotWidthDp(duration: String, fontScale: Float = 1f): Int {
    val baseWidth = if (duration.count { it == ':' } >= 2) 100 else 76
    return ceil(baseWidth * fontScale.coerceAtLeast(1f)).toInt()
}

internal fun callTimerSlotHeightDp(baseHeightDp: Int, fontScale: Float): Int =
    ceil(baseHeightDp * fontScale.coerceAtLeast(1f)).toInt()
