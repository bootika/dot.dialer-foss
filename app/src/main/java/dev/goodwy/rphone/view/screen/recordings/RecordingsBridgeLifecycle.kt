package dev.goodwy.rphone.view.screen.recordings

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.goodwy.rphone.recordings.RecordingsRepository
import dev.goodwy.rphone.recordings.RecordingDiagnostics
import kotlinx.coroutines.launch

@Composable
internal fun RecordingsBridgeLifecycle(repo: RecordingsRepository) {
    if(!repo.integrationEnabled())return
    val scope=rememberCoroutineScope();val lifecycle=LocalLifecycleOwner.current.lifecycle
    fun refresh() {scope.launch {if(repo.bridge.trusted())try {repo.bridge.refresh()}catch(error: Exception) {RecordingDiagnostics.failure("resume",error)}}}
    LaunchedEffect(repo) {refresh()}
    DisposableEffect(repo,lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME)refresh()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
}
