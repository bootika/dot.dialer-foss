package dev.goodwy.rphone.view.screen

internal const val HANGUP_BUTTON_MIN_SETTING = 0.1f
internal const val HANGUP_BUTTON_MAX_SETTING = 1f
internal const val HANGUP_BUTTON_DIAMETER_DP = 76f
internal const val HANGUP_BUTTON_HEIGHT_DP = 68f

internal data class HangupButtonGeometry(
    val widthDp: Float,
    val heightDp: Float,
    val isCircle: Boolean,
)

/**
 * Converts the saved slider position into the real button dimensions.
 *
 * The previous implementation treated 10% as a 76 dp circle, then treated 20% as
 * 20% of the available width. On common phones that made 10% and 20% effectively
 * identical (and could even make 20% smaller). Interpolating from the minimum usable
 * circle to the full available width keeps every slider step monotonic and visible.
 */
internal fun hangupButtonGeometry(
    availableWidthDp: Float,
    savedSetting: Float,
): HangupButtonGeometry {
    val available = availableWidthDp.coerceAtLeast(0f)
    val setting = savedSetting.coerceIn(
        HANGUP_BUTTON_MIN_SETTING,
        HANGUP_BUTTON_MAX_SETTING,
    )
    val minimumSide = HANGUP_BUTTON_DIAMETER_DP.coerceAtMost(available)
    val isCircle = setting <= HANGUP_BUTTON_MIN_SETTING + 0.0001f

    if (isCircle) {
        return HangupButtonGeometry(
            widthDp = minimumSide,
            heightDp = minimumSide,
            isCircle = true,
        )
    }

    val progress = (setting - HANGUP_BUTTON_MIN_SETTING) /
        (HANGUP_BUTTON_MAX_SETTING - HANGUP_BUTTON_MIN_SETTING)
    val width = minimumSide + (available - minimumSide) * progress

    return HangupButtonGeometry(
        widthDp = width.coerceIn(minimumSide, available),
        heightDp = HANGUP_BUTTON_HEIGHT_DP.coerceAtMost(width).coerceAtMost(available),
        isCircle = false,
    )
}
