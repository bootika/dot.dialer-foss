package dev.goodwy.rphone.view.screen

/** Existing Home dialpad scale, shared with the in-call keypad. */
internal fun dialpadPanelScale(availableWidthDp: Float, windowHeightDp: Float): Float {
    val widthScale = (availableWidthDp / 360f).coerceIn(0.6f, 1.4f)
    val heightBudget = (windowHeightDp - 64f - 24f).coerceAtLeast(200f)
    val heightScale = (heightBudget / 440f).coerceIn(0.55f, 1.4f)
    return minOf(widthScale, heightScale)
}

internal fun inCallKeyHeightDp(windowWidthDp: Float, windowHeightDp: Float): Float =
    (56f * dialpadPanelScale(windowWidthDp - 24f, windowHeightDp)).coerceAtLeast(48f)
