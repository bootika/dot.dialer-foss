package dev.goodwy.rphone.view.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.goodwy.rphone.view.theme.CallActionColors
import kotlin.math.abs

internal const val INCOMING_ACTION_TRIGGER_FRACTION = 0.85f
internal const val INCOMING_ACTION_IDLE_ALPHA = 0.68f
internal const val DEFAULT_INCOMING_SWIPE_TRIGGER_FRACTION = 0.80f
internal const val DEFAULT_INCOMING_SWIPE_TRACK_WIDTH_DP = 288f
internal const val INCOMING_ACTION_COMPLETION_DURATION_MS = 120

/**
 * Maps a drag to a stable 0..1 visual state. Reaching the action threshold always produces the
 * fully committed color, so the visual state and the gesture outcome cannot disagree.
 */
internal fun incomingActionFeedbackProgress(
    progress: Float,
    triggerFraction: Float = INCOMING_ACTION_TRIGGER_FRACTION,
): Float {
    if (triggerFraction <= 0f) return 1f
    return (abs(progress) / triggerFraction).coerceIn(0f, 1f)
}

/** The release condition deliberately includes the exact visual/haptic threshold. */
internal fun incomingActionCommitReached(
    distance: Float,
    triggerDistance: Float = INCOMING_ACTION_TRIGGER_FRACTION,
): Boolean = triggerDistance > 0f && abs(distance) >= triggerDistance

internal fun incomingActionAlpha(
    progress: Float,
    triggerFraction: Float = INCOMING_ACTION_TRIGGER_FRACTION,
    idleAlpha: Float = INCOMING_ACTION_IDLE_ALPHA,
): Float {
    val feedback = incomingActionFeedbackProgress(progress, triggerFraction)
    return idleAlpha + (1f - idleAlpha) * feedback
}

internal fun incomingActionColor(
    idleColor: Color,
    actionColor: Color,
    progress: Float,
    triggerFraction: Float = INCOMING_ACTION_TRIGGER_FRACTION,
): Color = lerp(
    idleColor,
    actionColor,
    incomingActionFeedbackProgress(progress, triggerFraction),
)

/**
 * Produces a lighter idle color without transparency. Translucent elevated surfaces let Android's
 * polygonal hardware shadow show through their center on some Samsung GPUs.
 */
internal fun incomingActionSurfaceColor(
    actionColor: Color,
    progress: Float,
    triggerFraction: Float = INCOMING_ACTION_TRIGGER_FRACTION,
): Color {
    val opaqueAction = actionColor.copy(alpha = 1f)
    val idleColor = CallActionColors.idle(actionColor)
    return lerp(
        idleColor,
        opaqueAction,
        incomingActionFeedbackProgress(progress, triggerFraction),
    )
}

/** Travel available to a handle while retaining the same inset at both ends of its track. */
internal fun incomingSliderTravelPx(
    trackWidthPx: Float,
    handleWidthPx: Float,
    horizontalInsetPx: Float,
): Float = (trackWidthPx - handleWidthPx - horizontalInsetPx * 2f).coerceAtLeast(0f)

/** Travel from the center to either end for a bidirectional handle. */
internal fun incomingCenteredSliderTravelPx(
    trackWidthPx: Float,
    handleWidthPx: Float,
    horizontalInsetPx: Float,
): Float = ((trackWidthPx - handleWidthPx) / 2f - horizontalInsetPx).coerceAtLeast(0f)

internal fun incomingSliderOffsetPx(
    progress: Float,
    startsAtStart: Boolean,
    travelPx: Float,
    horizontalInsetPx: Float,
): Float {
    val boundedProgress = progress.coerceIn(0f, 1f)
    val travelOffset = if (startsAtStart) boundedProgress else 1f - boundedProgress
    return horizontalInsetPx + travelOffset * travelPx
}

internal fun incomingSliderOffsetAfterResize(
    currentOffsetPx: Float,
    previousTravelPx: Float,
    newTravelPx: Float,
    startsAtStart: Boolean,
): Float {
    if (previousTravelPx <= 0f) return if (startsAtStart) 0f else newTravelPx
    val progress = if (startsAtStart) {
        currentOffsetPx / previousTravelPx
    } else {
        (previousTravelPx - currentOffsetPx) / previousTravelPx
    }.coerceIn(0f, 1f)
    return if (startsAtStart) progress * newTravelPx else newTravelPx * (1f - progress)
}
