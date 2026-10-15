package dev.goodwy.rphone.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.color_call_end

/** Independent tap targets: opening the call never consumes the End button. */
@Composable
fun OngoingCallBanner(onExpand: () -> Unit, onEnd: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().testTag("ongoingCallCard")) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f).heightIn(min = 48.dp).testTag("ongoingCallReturn")
                    .clickable(onClick = onExpand), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = color_call_button.copy(alpha = .12f), shape = CircleShape,
                        modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(DotDialerHandsetIcon, null, Modifier.size(22.dp), tint = color_call_button)
                        }
                    }
                    Text(stringResource(R.string.call_is_ongoing),
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.OpenInFull, stringResource(R.string.open_call),
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(onClick = onEnd, color = color_call_end, contentColor = Color.White,
                    shape = CircleShape, modifier = Modifier.size(48.dp).testTag("ongoingCallEnd")) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(DotDialerHangupIcon, stringResource(R.string.end_call), Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}
