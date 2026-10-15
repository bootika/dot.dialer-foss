package dev.goodwy.rphone.view.screen.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.performAppHaptic
import dev.goodwy.rphone.view.theme.customColors
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RuStoreDonateItem(
    headline: String,
    supporting: String? = null,
    trailing: String? = null,
    label: String? = null,
    selected: Boolean = false,
    darkTheme: Boolean,
    isPurchased: Boolean = false,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ListItemScale"
    )

    val border = when {
        isPurchased -> BorderStroke(2.2.dp, MaterialTheme.colorScheme.customColors.colorDarkGreen.copy(alpha = 0.4f))
        selected -> if (darkTheme) {
            BorderStroke(2.2.dp, MaterialTheme.colorScheme.customColors.colorPurple)
        } else {
            BorderStroke(2.2.dp, MaterialTheme.colorScheme.customColors.colorDarkPurple.copy(0.4f))
        }
        else -> BorderStroke(2.2.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f))
    }
    val color = when {
        isPurchased -> MaterialTheme.colorScheme.customColors.colorGreen.copy(alpha = 0.1f)
        selected -> MaterialTheme.colorScheme.customColors.colorPurple.copy(if (darkTheme) 0.1f else 0.2f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = color,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale),
            shadowElevation = 0.dp,
            border = border
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = {
                            if (!isPurchased) {
                                if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                    performAppHaptic(
                                        context,
                                        prefs.getString(
                                            PreferenceManager.KEY_APP_HAPTICS_STRENGTH,
                                            "light"
                                        ) ?: "light",
                                        prefs.getFloat(
                                            PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY,
                                            0.5f
                                        )
                                    )
                                }
                                onClick()
                            }
                        },
                        enabled = !isPurchased
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (supporting != null) {
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (trailing != null) {
                    Spacer(modifier = Modifier.width(24.dp))
                    Text(
                        text = if (isPurchased) "✓ Purchased" else trailing,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isPurchased) MaterialTheme.colorScheme.customColors.colorDarkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (label != null && selected) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 20.dp, y = (-10).dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFF49154),
                                Color(0xFFED6D93)
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}
