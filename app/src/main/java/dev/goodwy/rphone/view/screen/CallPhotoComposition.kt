package dev.goodwy.rphone.view.screen

import kotlin.math.roundToInt

/**
 * Composition rules for the full-photo call background.
 *
 * Square and landscape contact artwork spans the complete viewport width. Only the top and bottom
 * dissolve into the blurred colour echo; the horizontal edges always meet the screen edges.
 * Portrait artwork remains full-bleed.
 */
internal const val CALL_PHOTO_FULL_WIDTH_ASPECT_RATIO = 0.82f
internal const val CALL_PHOTO_MAX_PANEL_ASPECT_RATIO = 1.15f
internal const val CALL_PHOTO_TOP_SCRIM_FRACTION = 0.42f
internal const val CALL_PHOTO_BOTTOM_SCRIM_FRACTION = 0.62f
internal const val CALL_PHOTO_BOTTOM_FADE_SETTLED_STOP = 0.56f
internal const val CALL_PHOTO_EDGE_FEATHER_FRACTION = 0.38f
internal const val CALL_FULL_PHOTO_DOWNWARD_OFFSET_FRACTION = 0.03f

/** Same units in/out; translate the photo block, never the echo, identity or controls. */
internal fun callFullPhotoVerticalOffset(zoneStart: Float, zoneEnd: Float): Float {
    val height = zoneEnd - zoneStart
    return if (zoneStart.isFinite() && zoneEnd.isFinite() && height.isFinite() && height > 0f) {
        height * CALL_FULL_PHOTO_DOWNWARD_OFFSET_FRACTION
    } else 0f
}

internal fun usesFullWidthPhotoPanel(width: Int, height: Int): Boolean {
    if (width <= 0 || height <= 0) return true
    return usesFullWidthPhotoPanel(width.toFloat() / height.toFloat())
}

internal fun usesFullWidthPhotoPanel(aspectRatio: Float): Boolean =
    aspectRatio >= CALL_PHOTO_FULL_WIDTH_ASPECT_RATIO

internal fun callPhotoPanelAspectRatio(sourceAspectRatio: Float): Float =
    sourceAspectRatio.coerceIn(CALL_PHOTO_FULL_WIDTH_ASPECT_RATIO, CALL_PHOTO_MAX_PANEL_ASPECT_RATIO)

internal fun callPhotoPanelTopInsetDp(screenHeightDp: Int): Int =
    (screenHeightDp * 0.20f).toInt().coerceIn(128, 172)

/**
 * Positions the centre of the sharp photo panel in the measured free zone between the call
 * identity block and the first action-button row. The fallback preserves the approved baseline
 * until both landmarks have completed their first layout pass.
 */
internal fun callPhotoPanelTopInsetDp(
    screenWidthDp: Int,
    screenHeightDp: Int,
    panelAspectRatio: Float,
    clearZoneCenterDp: Float?,
): Int {
    if (clearZoneCenterDp == null || !clearZoneCenterDp.isFinite()) {
        return callPhotoPanelTopInsetDp(screenHeightDp)
    }

    val safeAspectRatio = panelAspectRatio.coerceAtLeast(0.01f)
    val panelHeightDp = screenWidthDp / safeAspectRatio
    return (clearZoneCenterDp - panelHeightDp / 2f)
        .roundToInt()
        .coerceAtLeast(0)
}
