package dev.goodwy.rphone.view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.fragment.app.FragmentActivity
import dev.goodwy.rphone.controller.lock.AppLockManager

/** One platform prompt per composition, not one prompt for each recomposition. */
@Composable
fun AuthenticationSystemPrompt(
    activity: FragmentActivity,
    title: String,
    onSuccess: () -> Unit,
    onError: (Int, String) -> Unit,
) {
    val success = rememberUpdatedState(onSuccess)
    val failure = rememberUpdatedState(onError)
    DisposableEffect(activity) {
        var active = true
        val prompt = AppLockManager.authenticate(activity, title,
            onSuccess = { if (active) success.value() },
            onError = { code, message -> if (active) failure.value(code, message) })
        onDispose {
            active = false
            prompt.cancelAuthentication()
        }
    }
}
