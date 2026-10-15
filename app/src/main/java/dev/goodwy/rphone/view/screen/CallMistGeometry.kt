package dev.goodwy.rphone.view.screen

/** Map the artwork to measured content, including font scaling and a secondary call banner. */
internal fun callMistZoneInMiddle(
    zone: CallMistZone,
    middle: AmbientDropletMiddleZone?,
    canvasTop: Float,
    canvasHeight: Float,
): CallMistZone {
    if (middle == null || canvasHeight <= 0f || !canvasHeight.isFinite() || !canvasTop.isFinite()) return zone
    val top = ((middle.topInRootPx - canvasTop) / canvasHeight).coerceIn(0f, 1f)
    val bottom = ((middle.bottomInRootPx - canvasTop) / canvasHeight).coerceIn(0f, 1f)
    if (!top.isFinite() || !bottom.isFinite() || bottom <= top) return zone
    // Reference proportions are artistic ratios, not fixed screen coordinates or dp heights.
    val scale = (bottom - top) / .385f
    return zone.copy(y = (top + bottom) / 2f + (zone.y - .46f) * scale, height = zone.height * scale)
}
