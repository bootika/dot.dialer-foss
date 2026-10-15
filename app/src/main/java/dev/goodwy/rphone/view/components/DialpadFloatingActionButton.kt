package dev.goodwy.rphone.view.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R

internal const val DIALPAD_FAB_DEFAULT_ELEVATION_DP = 2
internal const val DIALPAD_FAB_INTERACTION_ELEVATION_DP = 4

/** The single branded dialpad FAB used by both Home/Recents and the contacts dialpad page. */
@Composable
fun DialpadFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(17.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = DIALPAD_FAB_DEFAULT_ELEVATION_DP.dp,
            pressedElevation = DIALPAD_FAB_INTERACTION_ELEVATION_DP.dp,
            focusedElevation = DIALPAD_FAB_DEFAULT_ELEVATION_DP.dp,
            hoveredElevation = DIALPAD_FAB_DEFAULT_ELEVATION_DP.dp,
        ),
        contentColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            Color.Black
        } else {
            Color.White
        },
    ) {
        Icon(
            imageVector = Icons.Default.Dialpad,
            contentDescription = stringResource(R.string.keypad),
        )
    }
}
