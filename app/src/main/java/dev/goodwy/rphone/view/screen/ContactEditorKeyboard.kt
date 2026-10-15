package dev.goodwy.rphone.view.screen

import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import dev.goodwy.rphone.view.theme.systemBarActivity

/** Leave other screens' keyboard policy unchanged when the editor leaves composition. */
@Composable
@Suppress("DEPRECATION") // Scoped adjustResize remains the compatibility path for IME insets on API29.
internal fun ContactEditorKeyboardMode() {
    val activity = LocalContext.current.systemBarActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val previous = window?.attributes?.softInputMode
        val resize = previous?.let {
            (it and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        if (window != null && resize != null) window.setSoftInputMode(resize)
        onDispose {
            if (previous != null && window.attributes.softInputMode == resize) window.setSoftInputMode(previous)
        }
    }
}

/** Observe focus and IME geometry, rather than guessing a keyboard animation duration. */
@Composable
internal fun Modifier.keepFocusedEditorFieldVisible(insets: WindowInsets = WindowInsets.ime): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val bottom = insets.getBottom(LocalDensity.current)
    LaunchedEffect(focused, bottom) {
        if (focused) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    return this.bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused }
}
