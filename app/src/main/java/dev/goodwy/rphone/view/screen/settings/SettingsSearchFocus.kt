package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

internal data class SettingsSearchFocus(val id: String, val label: String, val listState: LazyListState, val ready: Boolean)
internal val LocalSettingsSearchFocus = staticCompositionLocalOf<SettingsSearchFocus?> { null }

@Composable
internal fun rememberSettingsListState(): LazyListState {
    val ownState = rememberLazyListState()
    return LocalSettingsSearchFocus.current?.listState ?: ownState
}

@Composable
internal fun Modifier.settingsSearchTarget(label: String?): Modifier {
    val target = LocalSettingsSearchFocus.current
    if (target == null || target.label != label) return this
    val bring = remember { BringIntoViewRequester() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(target.id, target.ready) {
        if (!target.ready) return@LaunchedEffect
        withFrameNanos { }
        bring.bringIntoView()
        focus.requestFocus()
    }
    return this.testTag("settingsFocus-${target.id}")
        .bringIntoViewRequester(bring).focusRequester(focus).focusable()
        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
}

@Composable
internal fun SettingsFocusContent(id: String, label: String, index: Int, content: @Composable () -> Unit) {
    val state = rememberLazyListState()
    var ready by remember(id, index) { mutableStateOf(false) }
    LaunchedEffect(id) {
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > index && it > 0 && (index >= 0 || it >= -index) }
        state.scrollToItem(if (index < 0) state.layoutInfo.totalItemsCount + index else index)
        withFrameNanos { }
        ready = true
    }
    CompositionLocalProvider(LocalSettingsSearchFocus provides SettingsSearchFocus(id, label, state, ready), content = content)
}
