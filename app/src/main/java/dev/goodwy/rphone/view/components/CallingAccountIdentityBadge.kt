package dev.goodwy.rphone.view.components

import android.telecom.PhoneAccount
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.goodwy.rphone.controller.util.loadCallingAccountIcon

internal const val CALL_ACCOUNT_BADGE_CONTAINER_ALPHA = 0.28f
internal const val CALL_ACCOUNT_BADGE_BORDER_ALPHA = 0.18f

/** Compact neutral label; Android's SIM icon remains untouched and keeps its system identity. */
@Composable
fun CallingAccountIdentityBadge(account: PhoneAccount) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = CALL_ACCOUNT_BADGE_CONTAINER_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = CALL_ACCOUNT_BADGE_BORDER_ALPHA),
        ),
    ) {
        CallingAccountIdentityContent(
            account = account,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/** Account content shared by the standalone badge and the unified call-status shield. */
@Composable
fun CallingAccountIdentityContent(
    account: PhoneAccount,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val context = LocalContext.current
    val drawable = remember(account, context) {
        loadCallingAccountIcon(context, account.icon)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        if (drawable != null) {
            AndroidView(
                factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
                update = { it.setImageDrawable(drawable) },
                modifier = Modifier.size(15.dp),
            )
        } else {
            Icon(Icons.Rounded.SimCard, contentDescription = null, modifier = Modifier.size(15.dp))
        }
        Text(
            text = account.label?.toString().orEmpty(),
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
