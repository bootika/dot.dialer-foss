package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.goodwy.rphone.view.components.rememberCallTapFeedback
import dev.goodwy.rphone.view.components.rememberCallTapHaptic
import androidx.compose.animation.animateColorAsState
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
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < .5f
    val base = callConnectionShieldColor(dark)
    val pressedFill = (if (dark) surface else Color.White).copy(alpha = if (dark) .25f else .90f)
    val fill by animateColorAsState(
        if (feedback.pressed) pressedFill else base,
        tween(CallControlFeedback.DurationMillis), label = "connectionShieldFill",
    )
    val click = rememberEnabledCallAction(true) { feedback.acknowledge(); haptic(); onClick() }
    return CallConnectionStatusFeedback(interaction, fill, click)
}
