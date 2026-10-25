package dev.goodwy.rphone.view.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** A new external number replaces a mounted draft; ordinary recomposition keeps edits. */
@Composable
internal fun rememberDialpadNumber(initialNumber: String?, draft: () -> String): MutableState<String> {
    val state = remember(initialNumber) { mutableStateOf(initialNumber ?: draft()) }
    LaunchedEffect(initialNumber) {
        if (initialNumber != null) io.github.bootika.dotdialer.diagnostics.recordDialNumber(
            io.github.bootika.dotdialer.core.diagnostics.DialNumberStage.DIALPAD_INITIALIZED,
            state.value, initialNumber != state.value)
    }
    return state
}
