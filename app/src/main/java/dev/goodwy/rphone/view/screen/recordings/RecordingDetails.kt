package dev.goodwy.rphone.view.screen.recordings

import android.media.AudioManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.license.PlanAccessManager
import dev.goodwy.rphone.recordings.*
import dev.goodwy.rphone.view.components.RillExpressiveCard
import io.github.bootika.dotdialer.core.recordings.*
import kotlinx.coroutines.*

@Composable
internal fun RecordingDetails(asset: SavedRecording,repo: RecordingsRepository,plans: PlanAccessManager,
    capabilities: DictateCapabilities,planRevision: Long,revision: Long,canPlay: Boolean,onBack: ()->Unit,onConfigure: ()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var texts by remember(asset.id) {mutableStateOf(emptyList<SavedTranscript>())}
    var jobs by remember(asset.id) {mutableStateOf(emptyList<RecordingJob>())}
    var delete by remember {mutableStateOf(false)};var translate by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)};var busy by remember {mutableStateOf(false)}
    var exportText by remember {mutableStateOf("")}
    val access=remember(planRevision,capabilities) {RecordingPolicy.processing(plans.effective,capabilities)}
    fun run(action: suspend ()->Unit) {scope.launch {busy=true;error=false;try {action()}catch(e: CancellationException) {throw e}catch(failure: Exception) {RecordingDiagnostics.failure("RecordingDetails",failure);error=true}finally {busy=false}}}
    LaunchedEffect(revision,asset.id) {val data=withContext(Dispatchers.IO) {repo.catalog.texts(asset.id) to repo.catalog.jobs().filter {it.recordingId==asset.id}};texts=data.first;jobs=data.second}
    val audioExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(asset.mimeType)) {uri ->if(uri!=null)run {
        withContext(Dispatchers.IO) {requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use {out->repo.files.audio(asset).inputStream().use {it.copyTo(out)}}}
    }}
    val textExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {uri ->if(uri!=null)run {
        withContext(Dispatchers.IO) {requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use {it.write(exportText.toByteArray(Charsets.UTF_8))}}
    }}
    if(translate)RecordingLanguageDialog(capabilities.targetLanguages,{translate=false}) {language ->translate=false;val original=texts.lastOrNull {it.operation=="transcribe"};if(original!=null)run {repo.queue(asset.id,language,original.id)}}
    if(delete)AlertDialog(onDismissRequest={delete=false},title={Text(stringResource(R.string.recordings_delete))},text={Text(stringResource(R.string.recordings_delete_confirm))},
        confirmButton={TextButton(onClick={delete=false;run {repo.delete(asset);onBack()}}) {Text(stringResource(R.string.recordings_delete))}},
        dismissButton={TextButton(onClick={delete=false}) {Text(stringResource(R.string.cancel))}})
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        TextButton(onClick=onBack) {Text(stringResource(R.string.recordings_back))}
        Text(asset.title,style=MaterialTheme.typography.titleLarge)
        Text(recordingTime(asset.durationMs),style=MaterialTheme.typography.bodyLarge)
        RecordingPlayback(asset,repo,canPlay)
        OutlinedButton(onClick={audioExport.launch(asset.fileName)},modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.recordings_export_audio))}
        if(repo.integrationEnabled())Button(onClick={if(access==RecordingAccess.READY)run {repo.queue(asset.id)}else onConfigure()},enabled=!busy,modifier=Modifier.fillMaxWidth().alpha(if(access==RecordingAccess.READY)1f else .45f)) {Text(stringResource(R.string.recordings_transcribe))}
        if(repo.integrationEnabled() && access!=RecordingAccess.READY)Text(stringResource(recordingAccessText(access)),style=MaterialTheme.typography.bodyMedium)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error)Text(stringResource(R.string.recordings_error),color=MaterialTheme.colorScheme.error)
        for(job in jobs)RillExpressiveCard {
            Column(Modifier.padding(16.dp)) {
                RecordingJobStatus(job)
                if(job.state in setOf("queued","running","blocked"))TextButton(onClick={run {repo.cancel(job.id)}}) {Text(stringResource(R.string.cancel))}
                if(repo.integrationEnabled() && job.state in setOf("failed","blocked"))TextButton(onClick={if(access==RecordingAccess.READY)run {repo.retry(job)}else onConfigure()}) {Text(stringResource(R.string.recordings_retry))}
            }
        }
        for(text in texts)RillExpressiveCard {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(if(text.operation=="transcribe")stringResource(R.string.recordings_original)else stringResource(R.string.recordings_translation,text.language),style=MaterialTheme.typography.titleMedium)
                SelectionContainer {Text(text.text,style=MaterialTheme.typography.bodyLarge)}
                Row {
                    TextButton(onClick={exportText=text.text;textExport.launch("${asset.id}-${text.id}.txt")}) {Text(stringResource(R.string.recordings_export_text))}
                    if(repo.integrationEnabled() && text.operation=="transcribe")TextButton(modifier=Modifier.alpha(if(RecordingPolicy.processing(plans.effective,capabilities,true)==RecordingAccess.READY)1f else .45f),onClick={if(RecordingPolicy.processing(plans.effective,capabilities,true)==RecordingAccess.READY)translate=true else onConfigure()}) {Text(stringResource(R.string.recordings_translate))}
                }
            }
        }
        TextButton(onClick={delete=true}) {Text(stringResource(R.string.recordings_delete),color=MaterialTheme.colorScheme.error)}
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RecordingPlayback(asset: SavedRecording,repo: RecordingsRepository,enabled: Boolean) {
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val player=remember(asset.id) {MediaPlayer()};var ready by remember(asset.id) {mutableStateOf(false)}
    var playing by remember(asset.id) {mutableStateOf(false)};var position by remember(asset.id) {mutableLongStateOf(0)}
    DisposableEffect(player,lifecycle) {
        player.setOnPreparedListener {ready=true};player.setOnCompletionListener {playing=false;position=0}
        player.setOnErrorListener {_,_,_->ready=false;playing=false;true}
        try {player.setDataSource(repo.files.audio(asset).absolutePath);player.prepareAsync()}catch(failure: Exception) {RecordingDiagnostics.failure("RecordingDetails",failure);ready=false}
        val observer=LifecycleEventObserver {_,event ->if(event==Lifecycle.Event.ON_PAUSE && playing) {player.pause();playing=false}}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);player.release()}
    }
    LaunchedEffect(playing) {while(playing) {position=player.currentPosition.toLong();delay(250)}}
    OutlinedButton(onClick={
        val mode=context.getSystemService(AudioManager::class.java).mode
        if(enabled && mode !in setOf(AudioManager.MODE_IN_CALL,AudioManager.MODE_IN_COMMUNICATION)) {
            if(playing)player.pause()else player.start();playing=!playing
        }
    },enabled=ready&&enabled,modifier=Modifier.fillMaxWidth()) {
        Text(stringResource(if(playing)R.string.recordings_pause else R.string.recordings_play)+" · "+recordingTime(position))
    }
    if(!enabled)Text(stringResource(R.string.recordings_play_after_call),style=MaterialTheme.typography.bodyMedium)
}
