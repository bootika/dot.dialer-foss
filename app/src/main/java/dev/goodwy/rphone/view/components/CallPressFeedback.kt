package dev.goodwy.rphone.view.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import dev.goodwy.rphone.view.theme.CallControlFeedback

internal val CallPressScaleKey = SemanticsPropertyKey<Float>("CallPressScale")
internal val CallPressFillAlphaKey = SemanticsPropertyKey<Float>("CallPressFillAlpha")
internal var SemanticsPropertyReceiver.callPressScale by CallPressScaleKey

@Composable
internal fun Modifier.callPressFeedback(pressed: Boolean, enabled: Boolean = true, fillAlpha: Float? = null): Modifier {
    val amount by animateFloatAsState(
        if (pressed && enabled) CallControlFeedback.PressedScale else 1f,
        tween(CallControlFeedback.DurationMillis), label = "callPressFeedback",
    )
    return semantics { callPressScale = amount; fillAlpha?.let { this[CallPressFillAlphaKey] = it } }.drawWithContent {
        scale(amount) { this@drawWithContent.drawContent() }
    }
}
