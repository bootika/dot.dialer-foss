package dev.goodwy.rphone.view.screen

import android.content.Context
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import dev.goodwy.rphone.view.components.circularCombinedClickable
import dev.goodwy.rphone.view.components.performAppHaptic

internal fun Modifier.homeCombinedClickable(
    context: Context,
    haptics: Boolean,
    hapticsStrength: String,
    hapticsIntensity: Float,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = combinedClickable(
    onClick = {
        if (haptics) performAppHaptic(context, hapticsStrength, hapticsIntensity)
        onClick()
    },
    onLongClick = onLongClick?.let { action ->
        {
            if (haptics) performAppHaptic(context, hapticsStrength, hapticsIntensity)
            action()
        }
    },
)

internal fun Modifier.homeAvatarGestures(
    context: Context,
    haptics: Boolean,
    hapticsStrength: String,
    hapticsIntensity: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier {
    fun trigger(action: () -> Unit) {
        if (haptics) performAppHaptic(context, hapticsStrength, hapticsIntensity)
        action()
    }
    return circularCombinedClickable(
        onClick = { trigger(onClick) },
        onLongClick = { trigger(onLongClick) },
    )
}
