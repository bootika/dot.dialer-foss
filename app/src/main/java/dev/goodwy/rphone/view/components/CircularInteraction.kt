package dev.goodwy.rphone.view.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role

/**
 * Keeps the interaction indication inside the same circular silhouette as the control.
 * The clipping intentionally precedes the gesture modifier so tap and long-press feedback
 * can never fall back to the rectangular layout bounds.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.circularCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = clip(CircleShape).combinedClickable(
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = Role.Button,
    onLongClickLabel = onLongClickLabel,
    onLongClick = onLongClick,
    interactionSource = null,
    indication = ripple(bounded = true),
    onClick = onClick,
)
