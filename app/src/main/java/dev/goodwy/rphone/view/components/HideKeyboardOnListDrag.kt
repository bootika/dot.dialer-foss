package dev.goodwy.rphone.view.components

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.collect

/** User drags only: query changes, list restoration and automatic scrolling keep the IME. */
@Composable
internal fun HideKeyboardOnListDrag(listState: LazyListState) {
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState, keyboard) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) keyboard?.hide()
        }
    }
}
