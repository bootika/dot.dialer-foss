package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.theme.customColors

@Composable
internal fun SupportFeedbackEntry() {
    val context = LocalContext.current
    RillListItem(
        headline = stringResource(R.string.support_title),
        supporting = stringResource(R.string.support_subtitle),
        expandText = true,
        leadingIcon = Icons.Rounded.Email,
        iconContainerColor = Color.Black,
        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
        trailingIcon = Icons.Default.ChevronRight,
        onClick = { ro.dotdesign.support.SupportActivity.open(context) }
    )
}
