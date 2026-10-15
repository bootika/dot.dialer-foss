package dev.goodwy.rphone.view.screen

import kotlin.math.roundToInt

/**
 * Adaptive vertical rhythm for the default in-call screen.
 *
 * Touch targets keep their ergonomic size, while the space between the identity, avatar, action
 * rows and hang-up button grows gradually with the usable window height. The timer always owns a
 * slot, including before the call becomes active, so connecting never makes the avatar jump.
 */
internal data class CallScreenSpacing(
    val topSpacingDp: Int,
    val nameToStatusGapDp: Int,
    val connectionToTimerGapDp: Int,
    val timerSlotHeightDp: Int,
    val avatarTopGapDp: Int,
    val actionRowsGapDp: Int,
    val hangupGapDp: Int,
    val controlsBottomPaddingDp: Int,
) {
    val reservedHeaderSpacingDp: Int
        get() = topSpacingDp +
            nameToStatusGapDp +
            connectionToTimerGapDp +
            timerSlotHeightDp
}

internal const val CALL_WAVE_LAYER_COUNT = 9
internal const val CALL_WAVE_SAMPLE_COUNT = 192
internal val CALL_WAVE_RADIAL_FRACTIONS =
    floatArrayOf(0f, 0.11f, 0.24f, 0.36f, 0.51f, 0.63f, 0.76f, 0.88f, 1f)
internal val CALL_WAVE_FREQUENCIES =
    floatArrayOf(12.5f, 16.0f, 13.75f, 19.25f, 15.0f, 21.5f, 17.25f, 23.0f, 18.5f)
internal val CALL_WAVE_PHASE_SPEEDS =
    floatArrayOf(0.74f, -1.08f, 0.88f, -0.66f, 1.2f, -0.93f, 0.57f, -1.31f, 0.82f)
internal val CALL_WAVE_AMPLITUDES =
    floatArrayOf(0.054f, 0.071f, 0.061f, 0.078f, 0.067f, 0.074f, 0.058f, 0.069f, 0.052f)
internal const val CALL_SCREEN_WAVE_FIELD_DP = 296
internal const val CALL_SCREEN_WAVE_AVATAR_DP = 164
internal const val CALL_SCREEN_ACTION_DIAMETER_DP = 68
internal const val CALL_CONNECTION_SHIELD_HEIGHT_DP = CALL_CONNECTION_TOUCH_HEIGHT_DP
internal const val CALL_AVATAR_CONTROL_CLEARANCE_DP = 24
internal const val CALL_AVATAR_MIDDLE_ZONE_MAX_FRACTION = 0.64f
// One global clearance rule for every circular hero state; diameter must remain its complement.
internal const val CALL_CIRCULAR_AVATAR_CLEARANCE_FRACTION = 0.12f
internal const val CALL_CIRCULAR_AVATAR_MIDDLE_ZONE_FRACTION =
    1f - 2f * CALL_CIRCULAR_AVATAR_CLEARANCE_FRACTION
internal const val CALL_AVATAR_MAX_AMPLITUDE = 0.058f
internal const val CALL_SCREEN_COMPANY_ROW_HEIGHT_DP = 20

internal data class CallAvatarGeometry(
    val fieldSizeDp: Int,
    val avatarSizeDp: Int,
)

internal fun callAvatarGeometry(
    availableHeightDp: Int,
    screenWidthDp: Int = 360,
    hasOtherCall: Boolean = false,
): CallAvatarGeometry {
    val safeScreenWidth = screenWidthDp.coerceAtLeast(1)
    val availableWidth = (safeScreenWidth - 48).coerceAtLeast(1)
    // The call screen is normally constrained vertically, not horizontally. Grow the avatar only
    // after preserving a comfortable identity area, both action rows and the hang-up control.
    // A secondary-call banner consumes part of the same vertical budget.
    val effectiveHeight = (availableHeightDp - if (hasOtherCall) 92 else 0).coerceAtLeast(1)
    val heightProgress = ((effectiveHeight - 600).toFloat() / (880 - 600)).coerceIn(0f, 1f)
    // Keep breathing room around the hero on tall phones as well. A larger maximum made the
    // avatar visually collide with the timer and action rows in conference and waiting states.
    val heightLimitedSize = (164 + (220 - 164) * heightProgress).roundToInt()
    val avatarSize = heightLimitedSize.coerceAtMost(availableWidth)
    val fieldPadding = if (effectiveHeight < 680) 110 else 132
    val fieldSize = (avatarSize + fieldPadding)
        .coerceAtMost((safeScreenWidth - 12).coerceAtLeast(avatarSize))
    return CallAvatarGeometry(fieldSizeDp = fieldSize, avatarSizeDp = avatarSize)
}

internal fun callAvatarSizeForMiddleZone(
    preferredSizeDp: Int,
    availableHeightDp: Int,
): Int = minOf(
    preferredSizeDp.coerceAtLeast(0),
    (availableHeightDp * CALL_AVATAR_MIDDLE_ZONE_MAX_FRACTION).roundToInt(),
    (availableHeightDp - (CALL_AVATAR_CONTROL_CLEARANCE_DP * 2)).coerceAtLeast(0),
)

/** 12% clear space after the reserved timer slot; width-limited windows preserve a full circle. */
internal fun callCircularAvatarSizeDp(availableWidthDp: Int, availableHeightDp: Int): Int =
    minOf(
        availableWidthDp.coerceAtLeast(0),
        (availableHeightDp.coerceAtLeast(0) * CALL_CIRCULAR_AVATAR_MIDDLE_ZONE_FRACTION).toInt(),
    )

internal fun callScreenSpacing(
    availableHeightDp: Int,
    hasOtherCall: Boolean,
): CallScreenSpacing {
    val baseTopSpacing = if (hasOtherCall) {
        adaptiveCallSpacing(availableHeightDp, minimumDp = 6, maximumDp = 10)
    } else {
        adaptiveCallSpacing(availableHeightDp, minimumDp = 12, maximumDp = 28)
    }
    // Keep the complete call composition optically centred between the status bar and navigation
    // area. navigationBarsPadding already protects the gesture/button target, so moving a small,
    // height-aware amount from the bottom padding to the top shifts every call state together
    // without changing its total vertical budget or introducing device-specific coordinates.
    val verticalOpticalShift = adaptiveCallSpacing(
        availableHeightDp,
        minimumDp = 4,
        maximumDp = 8,
    )
    val baseControlsBottomPadding = adaptiveCallSpacing(
        availableHeightDp,
        minimumDp = 6,
        maximumDp = 12,
    )
    // Keep both anchored blocks away from the physical screen edges. Applying the same inset at
    // the top and bottom makes the identity move down and the controls move up without shifting
    // the optical centre of the middle photo zone.
    val contentEdgeInset = 4
    val avatarGap = if (hasOtherCall) {
        adaptiveCallSpacing(availableHeightDp, minimumDp = 6, maximumDp = 10)
    } else {
        adaptiveCallSpacing(availableHeightDp, minimumDp = 8, maximumDp = 14)
    }

    return CallScreenSpacing(
        topSpacingDp = baseTopSpacing + verticalOpticalShift + contentEdgeInset,
        nameToStatusGapDp = adaptiveCallSpacing(
            availableHeightDp,
            minimumDp = 8,
            maximumDp = 12,
        ),
        connectionToTimerGapDp = adaptiveCallSpacing(
            availableHeightDp,
            minimumDp = 8,
            maximumDp = 12,
        ),
        timerSlotHeightDp = adaptiveCallSpacing(availableHeightDp, minimumDp = 32, maximumDp = 36),
        avatarTopGapDp = avatarGap,
        actionRowsGapDp = adaptiveCallSpacing(availableHeightDp, minimumDp = 10, maximumDp = 18),
        hangupGapDp = adaptiveCallSpacing(availableHeightDp, minimumDp = 18, maximumDp = 30),
        controlsBottomPaddingDp =
            (baseControlsBottomPadding - verticalOpticalShift).coerceAtLeast(2) +
                contentEdgeInset,
    )
}

internal fun callIdentityVerticalCompensationDp(hasSupplementalIdentity: Boolean): Int =
    if (hasSupplementalIdentity) CALL_SCREEN_COMPANY_ROW_HEIGHT_DP else 0

internal fun callIdentityReportedHeight(measuredHeight: Int, compensation: Int): Int =
    (measuredHeight - compensation.coerceAtLeast(0)).coerceAtLeast(0)

/** Shared optical centre for circular avatars and the sharp region of full-photo mode. */
internal fun callMiddleZoneCenter(start: Float, end: Float): Float? =
    if (start.isFinite() && end.isFinite() && end > start) {
        start + (end - start) / 2f
    } else {
        null
    }

private const val CALL_SCREEN_COMPACT_HEIGHT_DP = 600
private const val CALL_SCREEN_EXPANDED_HEIGHT_DP = 840

private fun adaptiveCallSpacing(
    availableHeightDp: Int,
    minimumDp: Int,
    maximumDp: Int,
): Int {
    val progress = ((availableHeightDp - CALL_SCREEN_COMPACT_HEIGHT_DP).toFloat() /
        (CALL_SCREEN_EXPANDED_HEIGHT_DP - CALL_SCREEN_COMPACT_HEIGHT_DP))
        .coerceIn(0f, 1f)
    return (minimumDp + (maximumDp - minimumDp) * progress).roundToInt()
}
