package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.goodwy.rphone.view.components.rememberCallTapFeedback
import dev.goodwy.rphone.view.components.rememberCallTapHaptic
import dev.goodwy.rphone.view.components.rememberCallPressColor
import dev.goodwy.rphone.view.components.rememberEnabledCallAction

internal data class CallConnectionStatusFeedback(
    val interaction: MutableInteractionSource,
    val pressed: Boolean,
    val fill: Color,
    val onClick: () -> Unit,
)

@Composable
internal fun rememberCallConnectionStatusFeedback(onClick: () -> Unit): CallConnectionStatusFeedback {
    val interaction = remember { MutableInteractionSource() }
    val feedback = rememberCallTapFeedback(interaction)
    val haptic = rememberCallTapHaptic()
    val fill = rememberCallPressColor(callConnectionShieldColor(MaterialTheme.colorScheme.surface.luminance() < .5f), feedback.pressed)
    val click = rememberEnabledCallAction(true) { feedback.acknowledge(); haptic(); onClick() }
    return CallConnectionStatusFeedback(interaction, feedback.pressed, fill, click)
}
