package dev.goodwy.rphone.view.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.util.AndroidHaptics
import dev.goodwy.rphone.view.theme.CallControlFeedback
import io.github.bootika.dotdialer.core.haptics.HapticIntent

internal val LocalCallTapHapticsEnabled = staticCompositionLocalOf { true }

/** Pointer-up may arrive from a gesture begun before capability or preference changes. */
@Composable
internal fun rememberEnabledCallAction(enabled: Boolean, action: () -> Unit): () -> Unit {
    // Use the committed action outside snapshot state: an in-flight pointer handler
    // must not read eligibility from the snapshot in which its gesture started.
    val current = remember { AtomicReference<(() -> Unit)?>(null) }
    SideEffect { current.set(if (enabled) action else null) }
    DisposableEffect(Unit) { onDispose { current.set(null) } }
    return { current.get()?.invoke(); Unit }
}

internal data class CallTapFeedbackState(val pressed: Boolean, val acknowledge: () -> Unit)

/** A quick click still gets one visible pulse; commands are dispatched without waiting. */
@Composable
internal fun rememberCallTapFeedback(interaction: MutableInteractionSource, enabled: Boolean = true): CallTapFeedbackState {
    val touched by interaction.collectIsPressedAsState()
    var acknowledged by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(enabled) { if (!enabled) { job?.cancel(); acknowledged = false } }
    return CallTapFeedbackState(enabled && (touched || acknowledged)) {
        if (enabled) {
            job?.cancel()
            acknowledged = true
            job = scope.launch { delay(120); acknowledged = false }
        }
    }
}

@Composable
internal fun rememberCallTapHaptic(): () -> Unit {
    val view = LocalView.current
    val enabled = LocalCallTapHapticsEnabled.current
    return { AndroidHaptics.performOnView(view, HapticIntent.KEYBOARD, appEnabled = enabled); Unit }
}

@Composable
internal fun rememberCallPressColor(base: Color, pressed: Boolean, enabled: Boolean = true): Color {
    val target = if (pressed && enabled) (base.alpha + .14f).coerceAtMost(1f) else base.alpha
    val alpha by animateFloatAsState(target, tween(CallControlFeedback.DurationMillis), label = "callPressTint")
    // Preserve the opaque action palettes exactly; change only translucent fill opacity.
    return base.copy(alpha = if (base.alpha == 1f) 1f else alpha)
}

@Composable
internal fun CallTapIconButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val feedback = rememberCallTapFeedback(interaction, enabled)
    val pressed = feedback.pressed
    val haptic = rememberCallTapHaptic()
    val color = rememberCallPressColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0f), pressed, enabled)
    Surface(onClick = rememberEnabledCallAction(enabled) { feedback.acknowledge(); haptic(); onClick() }, enabled = enabled,
        modifier = Modifier.size(48.dp).callPressFeedback(pressed, enabled, color.alpha),
        shape = CircleShape, color = color, interactionSource = interaction) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
