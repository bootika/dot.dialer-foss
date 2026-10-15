package dev.goodwy.rphone.view.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Decorative ambient particles used outside the call-screen droplet composition. */
@Composable
fun FloatingParticles() {
    val transition = rememberInfiniteTransition(label = "particles")
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize()) {
        repeat(10) { index ->
            val startX = (index * 100f) % 1000f
            val startY = (index * 150f) % 1500f
            val animX by transition.animateFloat(
                initialValue = 0f,
                targetValue = 100f,
                animationSpec = infiniteRepeatable(
                    animation = tween(10_000 + index * 1_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "x_$index",
            )
            val animY by transition.animateFloat(
                initialValue = 0f,
                targetValue = -150f,
                animationSpec = infiniteRepeatable(
                    animation = tween(12_000 + index * 1_200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "y_$index",
            )
            val alpha by transition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(5_000 + index * 500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "alpha_$index",
            )

            Box(
                modifier = Modifier
                    .offset {
                        with(density) {
                            IntOffset(
                                x = (startX + animX).dp.roundToPx(),
                                y = (startY + animY).dp.roundToPx(),
                            )
                        }
                    }
                    .size((10 + index % 20).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    .blur(2.dp),
            )
        }
    }
}
