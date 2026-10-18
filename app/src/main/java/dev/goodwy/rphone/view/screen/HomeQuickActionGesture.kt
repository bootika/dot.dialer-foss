package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon

internal class HomeQuickGestureState {
    var expanded by mutableStateOf(false)
    var hovered by mutableIntStateOf(-1)
    var origin = Offset.Zero
    var pointer = Offset.Zero
    var distance = 0f
    val bounds = mutableMapOf<Int, Rect>()
}

internal fun Modifier.quickActionGesture(state: HomeQuickGestureState, onAction: (Int) -> Unit): Modifier =
    onGloballyPositioned { state.origin = it.boundsInRoot().topLeft }
        .semantics { onLongClick { state.expanded = true; true } }
        .pointerInput(state) {
            detectDragGesturesAfterLongPress(onDragStart = { start ->
                state.pointer = state.origin + start; state.distance = 0f; state.hovered = -1; state.expanded = true
            }, onDrag = { change, amount ->
                change.consume(); state.pointer += amount; state.distance += amount.getDistance()
                state.hovered = state.bounds.entries.firstOrNull { it.value.contains(state.pointer) }?.key ?: -1
            }, onDragEnd = {
                val chosen = state.hovered
                if (state.distance > viewConfiguration.touchSlop) {
                    state.expanded = false
                    if (chosen >= 0) onAction(chosen)
                }
                state.hovered = -1
            }, onDragCancel = { state.expanded = false; state.hovered = -1 })
        }

@Composable
internal fun HomeQuickActionTray(state: HomeQuickGestureState, onAction: (Int) -> Unit, onMore: () -> Unit) {
    if (!state.expanded) return
    androidx.activity.compose.BackHandler { state.expanded = false }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(8.dp)) {
            val labels = listOf(stringResource(R.string.call), stringResource(R.string.message), "WhatsApp", stringResource(R.string.copy_action))
            val icons = listOf(DotDialerHandsetIcon, Icons.AutoMirrored.Rounded.Message, Icons.AutoMirrored.Rounded.Chat, Icons.Rounded.ContentCopy)
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { index, label ->
                    TextButton(onClick = { state.expanded = false; onAction(index) },
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).onGloballyPositioned { state.bounds[index] = it.boundsInRoot() }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(42.dp).background(if (state.hovered == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(icons[index], null, modifier = Modifier.size(22.dp), tint = if (state.hovered == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            TextButton(onClick = { state.expanded = false; onMore() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.more)) }
        }
    }
}
