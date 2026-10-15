package dev.goodwy.rphone.view.screen

/**
 * Samsung-inspired answer controls use a radial drag that is symmetrical for either hand and for
 * RTL layouts. Keeping the geometry separate makes the gesture threshold deterministic in tests.
 */
internal const val SAMSUNG_RADIAL_CONTAINER_DIAMETER_DP = 144
internal const val SAMSUNG_RADIAL_TRACK_DIAMETER_DP = 144
internal const val SAMSUNG_RADIAL_HANDLE_DIAMETER_DP = 72
internal const val SAMSUNG_RADIAL_MAX_DRAG_DP = SAMSUNG_RADIAL_TRACK_DIAMETER_DP / 2f
internal const val SAMSUNG_RADIAL_TRIGGER_FRACTION = 0.82f
internal const val SAMSUNG_RADIAL_ROW_HORIZONTAL_PADDING_DP = 0
internal const val SAMSUNG_RADIAL_IDLE_BORDER_ALPHA = 0.10f
internal const val SAMSUNG_RADIAL_DRAG_BORDER_ALPHA = 0.30f
internal const val SAMSUNG_RADIAL_IDLE_FILL_ALPHA = 0.015f
internal const val SAMSUNG_RADIAL_DRAG_FILL_ALPHA = 0.065f

internal fun samsungRadialTriggerDistanceDp(): Float =
    SAMSUNG_RADIAL_MAX_DRAG_DP * SAMSUNG_RADIAL_TRIGGER_FRACTION

/** Keeps both radial controls inside the width offered by the parent on narrow screens. */
internal fun samsungRadialControlDiameterDp(availableWidthDp: Float): Float =
    (availableWidthDp / 2f).coerceIn(0f, SAMSUNG_RADIAL_CONTAINER_DIAMETER_DP.toFloat())
