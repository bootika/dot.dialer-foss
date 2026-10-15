package dev.goodwy.rphone.controller.util

import kotlin.math.roundToInt

/** Persisted call-screen droplet controls shared by settings and rendering. */
object AmbientDropletPreference {
    const val ENABLED_KEY = "ambient_droplets_enabled"
    const val DEFAULT_ENABLED = true
    const val BRIGHTNESS_KEY = "ambient_droplet_brightness"
    const val MIN_BRIGHTNESS = 10
    // Keep legacy storage units: old100 still renders identically and now displays50%.
    const val MAX_BRIGHTNESS = 200
    const val DEFAULT_BRIGHTNESS = io.github.bootika.dotdialer.core.preferences.DotDialerPreferenceDefaults.DEFAULT_DROPLET_BRIGHTNESS
    const val MIN_OPACITY_PERCENT = 5f
    const val MAX_OPACITY_PERCENT = 100f
    const val DEFAULT_OPACITY_PERCENT = DEFAULT_BRIGHTNESS / 2f
    const val OPACITY_STEP_PERCENT = 5f

    fun opacityPercent(storedBrightness: Int): Float =
        storedBrightness.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS) / 2f

    fun storedBrightness(opacityPercent: Float): Int =
        ((if (opacityPercent.isFinite()) opacityPercent else DEFAULT_OPACITY_PERCENT)
            .coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT) * 2f).roundToInt()
}
