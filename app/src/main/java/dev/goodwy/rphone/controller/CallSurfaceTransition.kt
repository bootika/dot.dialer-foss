package dev.goodwy.rphone.controller

import android.telecom.Call
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun CallSurfaceTransition(call: Call?, hasDeferredPresentation: Boolean = false, content: @Composable (Call?) -> Unit) {
    val initialKey = androidx.compose.runtime.remember { Any() }
    var firstCall by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Call?>(null) }
    androidx.compose.runtime.SideEffect { if (firstCall == null && call != null) firstCall = call }
    // A translucent starting window must not reveal Home while Telecom supplies its first call.
    Box(Modifier.fillMaxSize().background(if (call == null && !hasDeferredPresentation) MaterialTheme.colorScheme.surface else Color.Transparent)) {
        AnimatedContent(
            targetState = call,
            contentKey = { if (firstCall == null || it == null || it === firstCall) initialKey else it },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (initialState != null && targetState != null) {
                    (fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400)))
                        .togetherWith(fadeOut(tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300)))
                } else EnterTransition.None.togetherWith(ExitTransition.None).using(null)
            },
            label = "CallSwitch",
        ) { target -> content(target) }
    }
}
