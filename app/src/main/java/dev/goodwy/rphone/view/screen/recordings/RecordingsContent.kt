package dev.goodwy.rphone.view.screen.recordings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.recordings.*
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.screen.settings.*
import io.github.bootika.dotdialer.core.license.*
import io.github.bootika.dotdialer.core.recordings.*
import kotlinx.coroutines.*

@Composable
internal fun RecordingsContent(callKey: String="", callerTitle: String="") {
    val context=LocalContext.current
    val repo=rememberRecordingsRepository()
    val plans=rememberPlanAccessManager()
    val rights by plans.state.collectAsState()
    val capabilities by repo.bridge.capabilities.collectAsState()
    val revision by repo.revision.collectAsState()
    val preferences by repo.preferencesRevision.collectAsState()
    val scope=rememberCoroutineScope()
    var assets by remember {mutableStateOf(emptyList<SavedRecording>())}
    var jobs by remember {mutableStateOf(emptyList<RecordingJob>())}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var error by remember {mutableStateOf(false)}
    var errorText by remember {mutableIntStateOf(R.string.recordings_error)}
    var busy by remember {mutableStateOf(false)}
    var languagePicker by remember {mutableStateOf(false)}
    val access=remember(rights,capabilities) {RecordingPolicy.processing(plans.effective,capabilities)}
    val complete=remember(rights) {plans.allows(PlanFeature.RECORDINGS_AUTOMATIC)}
    fun run(action: suspend () -> Unit) {scope.launch {busy=true;error=false;try {action()} catch(e: CancellationException) {throw e} catch(failure: Exception) {RecordingDiagnostics.failure("RecordingsContent",failure);errorText=if(failure is DictateFailure && failure.code=="UNSUPPORTED_AUDIO")R.string.recordings_audio_format else R.string.recordings_error;error=true} finally {busy=false}}}
    val setup=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
        if(result.resultCode==Activity.RESULT_OK)run {repo.bridge.approveSetup();repo.changed();RecordingSyncJob.schedule(context)}
    }
    fun configure() {
        if(!plans.allows(PlanFeature.RECORDINGS_MANUAL)) {plans.requestUpgrade(PlanFeature.RECORDINGS_MANUAL);return}
        try {
            if(repo.bridge.installed())setup.launch(repo.bridge.setupIntent())
            else dev.goodwy.rphone.controller.util.openLink(context,dev.goodwy.rphone.dotDictateUrl(java.util.Locale.getDefault().language))
        } catch(failure: Exception) {RecordingDiagnostics.failure("RecordingsContent",failure);errorText=if(failure is DictateFailure && failure.code=="UNSUPPORTED_AUDIO")R.string.recordings_audio_format else R.string.recordings_error;error=true}
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null)run {val asset=repo.import(uri,callerTitle,callKey);selected=asset.id}
    }
    RecordingsBridgeLifecycle(repo)
    LaunchedEffect(revision,callKey) {
        val loaded=withContext(Dispatchers.IO) {repo.catalog.recordings() to repo.catalog.jobs()}
        assets=loaded.first.filter {callKey.isEmpty() || it.callKey==callKey};jobs=loaded.second
        if(selected!=null && assets.none {it.id==selected})selected=null
    }
    LaunchedEffect(repo) {while(isActive) {delay(3_000);if(repo.integrationEnabled() && withContext(Dispatchers.IO) {repo.catalog.jobs().any {it.state in setOf("queued","running","cancelling")}}) {
        try {withContext(Dispatchers.IO) {RecordingSyncEngine.synchronize(repo)}}catch(failure: Exception) {RecordingDiagnostics.failure("RecordingsContent",failure); }
    }}}
    if(languagePicker)RecordingLanguageDialog(capabilities.targetLanguages,{languagePicker=false}) {language ->languagePicker=false;run {repo.setTranslation(language)}}
    val asset=assets.firstOrNull {it.id==selected}
    if(asset!=null) {
        RecordingDetails(asset,repo,plans,capabilities,rights.revision,revision,callKey.isEmpty(),{selected=null},::configure)
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp).testTag("recordings-library"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(repo.integrationEnabled())RillExpressiveCard {
            DotSettingsNavigationRow(stringResource(if(capabilities.connected)R.string.recordings_connected else R.string.recordings_connect),
                supporting=stringResource(recordingAccessText(access)),contentActive=access==RecordingAccess.READY,onClick=::configure)
            Text(stringResource(R.string.recordings_requirements),Modifier.padding(horizontal=16.dp,vertical=10.dp),style=MaterialTheme.typography.bodyMedium)
            if(capabilities.connected)Row(Modifier.padding(horizontal=8.dp)) {
                TextButton(onClick={context.packageManager.getLaunchIntentForPackage(DictateProtocol.PACKAGE)?.let {context.startActivity(it)}}) {Text(stringResource(R.string.recordings_open_dictate))}
                TextButton(onClick={run {repo.disconnect()}}) {Text(stringResource(R.string.recordings_disconnect))}
            }
        }
        if(repo.integrationEnabled())FilledTonalButton(onClick={if(access==RecordingAccess.READY)import.launch(arrayOf("audio/*")) else configure()},enabled=!busy,
            modifier=Modifier.fillMaxWidth().alpha(if(access==RecordingAccess.READY)1f else .45f).testTag("recordings-import")) {Text(stringResource(R.string.recordings_import))}
        if(repo.integrationEnabled())RillExpressiveCard {
            val auto=remember(preferences) {repo.optedIn()}
            val translation=remember(preferences) {repo.translationLanguage()}
            DotSettingsSwitchRow(stringResource(R.string.recordings_auto_transcribe),stringResource(R.string.recordings_auto_transcribe_summary),auto,
                {value ->if(!complete)plans.requestUpgrade(PlanFeature.RECORDINGS_AUTOMATIC)else if(value && access!=RecordingAccess.READY)configure()else run {repo.setAutomatic(value)}},contentActive=complete && access==RecordingAccess.READY)
            DotSettingsSwitchRow(stringResource(R.string.recordings_auto_translate),supporting=if(translation.isEmpty())stringResource(R.string.recordings_auto_translate_summary)else translation,
                checked=translation.isNotEmpty(),onCheckedChange={value ->if(!complete)plans.requestUpgrade(PlanFeature.RECORDINGS_AUTOMATIC)else if(value && RecordingPolicy.processing(plans.effective,capabilities,true)!=RecordingAccess.READY)configure()else if(value)languagePicker=true else run {repo.setTranslation("")}},contentActive=complete && RecordingPolicy.processing(plans.effective,capabilities,true)==RecordingAccess.READY)
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error)Text(stringResource(errorText),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("recordings-error"))
        if(assets.isEmpty())Text(stringResource(R.string.recordings_empty),style=MaterialTheme.typography.bodyLarge)
        for(recording in assets) {
            Card(onClick={selected=recording.id},modifier=Modifier.fillMaxWidth().testTag("recording-${recording.id}")) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(recording.title,style=MaterialTheme.typography.titleMedium)
                    Text(recordingTime(recording.durationMs)+" · "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(recording.createdAt)),style=MaterialTheme.typography.bodyMedium)
                    jobs.firstOrNull {it.recordingId==recording.id}?.let {RecordingJobStatus(it)}
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

internal fun recordingAccessText(access: RecordingAccess): Int=when(access) {
    RecordingAccess.READY->R.string.recordings_connected
    RecordingAccess.PLAN_REQUIRED->R.string.recordings_plan_required
    RecordingAccess.DISCONNECTED->R.string.recordings_disconnected
    RecordingAccess.LICENSE_REQUIRED->R.string.recordings_license_required
    RecordingAccess.LICENSE_UNAVAILABLE->R.string.recordings_license_unavailable
    RecordingAccess.KEY_REQUIRED->R.string.recordings_key_required
    RecordingAccess.UNSUPPORTED->R.string.recordings_update_required
}
internal fun recordingTime(ms: Long): String {
    val seconds=(ms/1000).coerceAtLeast(0)
    return if(seconds>=3600)"%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60) else "%d:%02d".format(seconds/60,seconds%60)
}
