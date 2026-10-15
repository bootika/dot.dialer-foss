package dev.goodwy.rphone.view.components

import android.widget.ImageView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.goodwy.rphone.controller.util.CallingAccountInfo
import dev.goodwy.rphone.controller.util.loadCallingAccountIcon

/** Displays the identity supplied by Android for a calling account without recoloring it. */
@Composable
fun CallingAccountIdentityIcon(
    account: CallingAccountInfo,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val drawable = remember(account.icon, context) {
        loadCallingAccountIcon(context, account.icon)
    }
    if (drawable != null) {
        AndroidView(
            factory = {
                ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
            },
            update = { it.setImageDrawable(drawable) },
            modifier = modifier,
        )
    } else {
        Icon(
            imageVector = Icons.Rounded.SimCard,
            contentDescription = null,
            tint = fallbackColor,
            modifier = modifier,
        )
    }
}
