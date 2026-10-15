package dev.goodwy.rphone.view.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.goodwy.rphone.controller.CallLogViewModel

@Composable
internal fun AcknowledgeMissedCallsEffect(viewModel: CallLogViewModel) {
    LaunchedEffect(viewModel) {
        viewModel.acknowledgeMissedCalls()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.acknowledgeMissedCalls()
    }
}
