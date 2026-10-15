package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.lock.AppLockManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.AuthenticationSystemPrompt
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Missing key/corrupt credentials never unlock by themselves; Android identity is required. */
@Composable
internal fun AuthenticationRecovery(onRecovered: () -> Unit) {
    val activity = LocalActivity.current as? FragmentActivity ?: return
    val prefs = koinInject<PreferenceManager>()
    val scope = rememberCoroutineScope()
    var prompting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    if (!AppLockManager.canAuthenticate(activity)) return
    TextButton(enabled = !busy, onClick = { prompting = true }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
        Text(stringResource(R.string.authentication_recover_device))
    }
    if (prompting) AuthenticationSystemPrompt(activity,
        title = stringResource(R.string.verify_your_identity_to_continue),
        onSuccess = {
            prompting = false; busy = true
            scope.launch {
                try {
                    prefs.configureAuthentication("system")
                    onRecovered()
                } catch (error: Exception) {
                    android.util.Log.e("Authentication", "Recovery failed: ${error.javaClass.simpleName}")
                } finally { busy = false }
            }
        },
        onError = { _, _ -> prompting = false })
}
