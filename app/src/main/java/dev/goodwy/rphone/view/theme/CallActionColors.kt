package dev.goodwy.rphone.view.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Visual-only compression: layout and touch targets never shrink. */
object CallControlFeedback {
    const val PressedScale = 0.95f
    const val DurationMillis = 100
}

/** One opaque action palette shared by every call control, independent of photo or theme. */
object CallActionColors {
    val answerCommitted = DotJade
    val rejectCommitted = DotError
    const val idleLightenFraction = 0.18f
    val answerIdle = lerp(answerCommitted, Color.White, idleLightenFraction)
    val rejectIdle = lerp(rejectCommitted, Color.White, idleLightenFraction)
    val onAction = Color.White
    // A bidirectional handle is neutral until its gesture chooses Answer or Reject.
    val neutralIdle = Color.White
    val onNeutral = Color.Black

    fun idle(action: Color): Color = when (action) {
        answerCommitted -> answerIdle
        rejectCommitted -> rejectIdle
        else -> lerp(action.copy(alpha = 1f), Color.White, idleLightenFraction)
    }
}
