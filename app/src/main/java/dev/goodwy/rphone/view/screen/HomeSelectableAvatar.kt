package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.components.DotUnknownCallerIcon
import dev.goodwy.rphone.view.components.RillAvatar

@Composable
internal fun HomeSelectableAvatar(
    title: String,
    photoUri: String?,
    phoneNumber: String?,
    avatarColorIndex: Int?,
    avatarSize: Int,
    selected: Boolean,
    haptics: Boolean,
    hapticsStrength: String,
    hapticsIntensity: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    colorSeed: String? = null,
) {
    val context = LocalContext.current
    val interactiveModifier = modifier
        .semantics(mergeDescendants = true) {
            this.contentDescription = title
            this.selected = selected
        }
        .homeAvatarGestures(
            context, haptics, hapticsStrength, hapticsIntensity, onClick, onLongClick,
        )
    if (selected) {
        Box(
            modifier = interactiveModifier.background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size((avatarSize * 0.56f).dp),
            )
        }
    } else {
        RillAvatar(
            name = colorSeed ?: title,
            colorSeed = dev.goodwy.rphone.view.components.avatarIdentityColorSeed(colorSeed ?: title, phoneNumber.orEmpty()),
            photoUri = photoUri,
            icon = if (photoUri == null && title == phoneNumber) DotUnknownCallerIcon else null,
            avatarColorIndex = avatarColorIndex,
            modifier = interactiveModifier,
            shape = CircleShape,
            contentSize = avatarSize.dp,
        )
    }
}


