package dev.goodwy.rphone.view.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal class HomeQuickGestureState {
    var expanded by mutableStateOf(false)
    var hovered by mutableIntStateOf(-1)
    var origin = Offset.Zero
    var pointer = Offset.Zero
    var distance = 0f
    val bounds = mutableMapOf<Int, Rect>()
    fun toggleForTap(allowOpen: () -> Boolean) {
        if (expanded || allowOpen()) {
            hovered = -1
            expanded = !expanded
        }
    }
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
                state.expanded = false
                state.hovered = -1
                if (state.distance > viewConfiguration.touchSlop && chosen >= 0) onAction(chosen)
            }, onDragCancel = { state.expanded = false; state.hovered = -1 })
        }

@Composable
internal fun HomeQuickActionTray(state: HomeQuickGestureState, onAction: (Int) -> Unit) {
    if (!state.expanded) return
    androidx.activity.compose.BackHandler { state.expanded = false }
    val actions = listOf(
        dev.goodwy.rphone.controller.util.PreferenceManager.SWIPE_ACTION_CALL,
        dev.goodwy.rphone.controller.util.PreferenceManager.SWIPE_ACTION_MESSAGE,
        dev.goodwy.rphone.controller.util.PreferenceManager.SWIPE_ACTION_WHATSAPP,
        dev.goodwy.rphone.controller.util.PreferenceManager.SWIPE_ACTION_COPY_NUMBER,
    )
    Row(Modifier.fillMaxWidth()) {
        actions.forEachIndexed { index, action ->
            val visual = requireNotNull(dev.goodwy.rphone.view.components.homeSwipeVisual(action))
            val background by animateColorAsState(
                targetValue = if (state.hovered == index) Color.Black.copy(alpha = .15f).compositeOver(visual.background) else visual.background,
                animationSpec = tween(100),
                label = "quickActionBackground",
            )
            Surface(
                onClick = { state.expanded = false; onAction(index) },
                shape = androidx.compose.ui.graphics.RectangleShape,
                color = background,
                contentColor = visual.foreground,
                modifier = Modifier.weight(1f).aspectRatio(1f)
                    .onGloballyPositioned { state.bounds[index] = it.boundsInRoot() },
            ) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(visual.icon, null, modifier = Modifier.size(24.dp))
                    Text(visual.label, style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}
