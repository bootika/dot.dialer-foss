package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.goodwy.rphone.view.components.rememberCallTapFeedback
import dev.goodwy.rphone.view.components.rememberCallTapHaptic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import dev.goodwy.rphone.view.theme.CallControlFeedback
import dev.goodwy.rphone.view.components.rememberEnabledCallAction

internal data class CallConnectionStatusFeedback(
    val interaction: MutableInteractionSource,
    val fill: Color,
    val onClick: () -> Unit,
)

@Composable
internal fun rememberCallConnectionStatusFeedback(onClick: () -> Unit): CallConnectionStatusFeedback {
    val interaction = remember { MutableInteractionSource() }
    val feedback = rememberCallTapFeedback(interaction)
    val haptic = rememberCallTapHaptic()
    val base = callConnectionShieldColor(MaterialTheme.colorScheme.surface.luminance() < .5f)
    val alpha by animateFloatAsState(
        if (feedback.pressed) (base.alpha + .10f).coerceAtMost(1f) else base.alpha,
        tween(CallControlFeedback.DurationMillis), label = "connectionShieldOpacity",
    )
    val fill = base.copy(alpha = alpha)
    val click = rememberEnabledCallAction(true) { feedback.acknowledge(); haptic(); onClick() }
    return CallConnectionStatusFeedback(interaction, fill, click)
}
