package dev.goodwy.rphone.view.screen.recordings

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.goodwy.rphone.recordings.RecordingsRepository
import dev.goodwy.rphone.recordings.RecordingDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

internal enum class RecordingBridgeCheck { IDLE, CHECKING, FAILED }

@Composable
internal fun RecordingsBridgeLifecycle(repo: RecordingsRepository, poll: Boolean=false): RecordingBridgeCheck {
    var check by remember(repo) {mutableStateOf(RecordingBridgeCheck.IDLE)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val enabled by repo.moduleState.collectAsState()
    LaunchedEffect(repo,lifecycle,poll,enabled) {
        check=RecordingBridgeCheck.IDLE
        if(!repo.integrationEnabled() || !enabled)return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            do {
                if(repo.bridge.trusted()) {
                    check=RecordingBridgeCheck.CHECKING
                    try {repo.refreshBridge();check=RecordingBridgeCheck.IDLE}
                    catch(error: CancellationException) {check=RecordingBridgeCheck.IDLE;throw error}
                    catch(error: Exception) {check=RecordingBridgeCheck.FAILED;RecordingDiagnostics.failure("resume",error)}
                } else check=RecordingBridgeCheck.IDLE
                if(poll)delay(30_000)else awaitCancellation()
            } while(true)
        }
    }
    return check
}
