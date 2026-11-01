package dev.goodwy.rphone.view.screen.recordings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
internal fun RecordingsContent(callKey: String="", callerTitle: String="",callerNumber: String="",settingsOnly: Boolean=false,onOpenLibrary: ()->Unit={}) {
    CompositionLocalProvider(LocalSettingsExplanations provides !settingsOnly) {
        RecordingsContentBody(callKey,callerTitle,callerNumber,settingsOnly,onOpenLibrary)
    }
}

@Composable
private fun RecordingsContentBody(callKey: String,callerTitle: String,callerNumber: String,settingsOnly: Boolean,onOpenLibrary: ()->Unit) {
    val context=LocalContext.current
    val repo=rememberRecordingsRepository()
    val plans=rememberPlanAccessManager()
    val rights by plans.state.collectAsState()
    val capabilities by repo.bridge.capabilities.collectAsState()
    val displayLocale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val revision by repo.revision.collectAsState()
    val preferences by repo.preferencesRevision.collectAsState()
    val scope=rememberCoroutineScope()
    var assets by remember {mutableStateOf(emptyList<SavedRecording>())}
    var loaded by remember(callKey) {mutableStateOf(false)}
    var jobs by remember {mutableStateOf(emptyList<RecordingJob>())}
    var counts by remember {mutableStateOf(emptyMap<String,Pair<Int,Int>>())}
    var contacts by remember {mutableStateOf(emptyMap<String,RecordingContactIdentity>())}
    var contactRefresh by remember {mutableIntStateOf(0)}
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event ->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)contactRefresh++}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
    val numbers=assets.map {it.phoneNumber}.filter {it.isNotEmpty()}.distinct()
    LaunchedEffect(numbers,contactRefresh) {
        contacts=withContext(Dispatchers.IO) {numbers.associateWith {recordingContactIdentity(context,it)}}
    }
    val groups=remember(assets,contacts) {groupRecordings(assets,contacts)}
    val listState=rememberLazyListState()
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var error by remember {mutableStateOf(false)}
    var errorText by remember {mutableIntStateOf(R.string.recordings_error)}
    var busy by remember {mutableStateOf(false)}
    var languagePicker by remember {mutableStateOf(false)}
    var outputPicker by remember {mutableStateOf(false)}
    val moduleEnabled by repo.moduleState.collectAsState()
    val injectedRepository=LocalRecordingsRepository.current
    LaunchedEffect(repo,moduleEnabled) {if(injectedRepository==null && dev.goodwy.rphone.BuildConfig.TEST_DISTRIBUTION)AdbCallCapture.get(context).recover()}
    val access=remember(rights,capabilities) {RecordingPolicy.processing(plans.effective,capabilities)}
    val complete=remember(rights) {plans.allows(PlanFeature.RECORDINGS_AUTOMATIC)}
    fun run(action: suspend () -> Unit) {scope.launch {busy=true;error=false;try {action()} catch(e: CancellationException) {throw e} catch(failure: Exception) {RecordingDiagnostics.failure("RecordingsContent",failure);errorText=if(failure is DictateFailure && failure.code=="UNSUPPORTED_AUDIO")R.string.recordings_audio_format else R.string.recordings_error;error=true} finally {busy=false}}}
    val setup=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
        if(result.resultCode==Activity.RESULT_OK)run {repo.bridge.approveSetup();repo.changed();RecordingSyncJob.schedule(context)}
    }
    fun configure() {
        if(!repo.moduleEnabled())return
        if(!plans.allows(PlanFeature.RECORDINGS_MANUAL)) {plans.requestUpgrade(PlanFeature.RECORDINGS_MANUAL);return}
        try {
            if(repo.bridge.installed())setup.launch(repo.bridge.setupIntent())
            else dev.goodwy.rphone.controller.util.openLink(context,dev.goodwy.rphone.dotDictateUrl(java.util.Locale.getDefault().language))
        } catch(failure: Exception) {RecordingDiagnostics.failure("RecordingsContent",failure);errorText=if(failure is DictateFailure && failure.code=="UNSUPPORTED_AUDIO")R.string.recordings_audio_format else R.string.recordings_error;error=true}
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null)run {val asset=repo.import(uri,callerTitle,callKey,callerNumber);selected=asset.id}
    }
    val check=recordingsBridgeLifecycle(repo,poll=true)
    LaunchedEffect(revision,callKey) {
        try {
            val data=withContext(Dispatchers.IO) {Triple(repo.catalog.recordings(),repo.catalog.jobs(),repo.catalog.textCounts())}
            assets=data.first.filter {callKey.isEmpty() || it.callKey==callKey};jobs=data.second;counts=data.third
            if(selected!=null && assets.none {it.id==selected})selected=null
            loaded=true
        }catch(cancelled: CancellationException){throw cancelled}
        catch(failure: Exception){RecordingDiagnostics.failure("RecordingsLibrary",failure);error=true;loaded=true}
    }
    LaunchedEffect(repo,moduleEnabled) {if(!moduleEnabled)return@LaunchedEffect;while(isActive) {delay(3_000);if(repo.integrationEnabled() && repo.moduleEnabled() && withContext(Dispatchers.IO) {repo.catalog.jobs().any {it.state in setOf("queued","running","cancelling")}}) {
        try {withContext(Dispatchers.IO) {RecordingSyncEngine.synchronize(repo)}}catch(failure: Exception) {RecordingDiagnostics.failure("RecordingsContent",failure); }
    }}}
    if(languagePicker)RecordingLanguageDialog(capabilities.targetLanguages,{languagePicker=false}) {language ->languagePicker=false;run {repo.setTranslation(language)}}
    if(outputPicker)RecordingLanguageDialog(listOf("")+capabilities.targetLanguages,{outputPicker=false}) {language ->outputPicker=false;run {repo.setOutputLanguage(language)}}
    val asset=assets.firstOrNull {it.id==selected}
    // Do not mount a configuration-only lazy list: it would retain that anchor when groups load above it.
    if(!loaded) {
        Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center){CircularProgressIndicator()}
        return
    }
    if(asset!=null) {
        RecordingDetails(asset,repo,plans,capabilities,rights.revision,revision,callKey.isEmpty(),{selected=null},::configure)
        return
    }
    LazyColumn(state=listState,modifier=Modifier.fillMaxSize().testTag("recordings-library"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(settingsOnly)item("module-master") {RecordingsModuleSwitch(onOpenLibrary)}
        items(if(settingsOnly)emptyList() else groups,key={it.key}) {group ->
            RillExpressiveCard {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(dev.goodwy.rphone.view.components.phoneDisplayText(group.title),style=MaterialTheme.typography.titleLarge)
                    for(number in group.numbers) {
                        if(number.number.isEmpty())Text(stringResource(R.string.recordings_no_number),style=MaterialTheme.typography.bodyMedium)
                        else if(number.number!=group.title)Text(dev.goodwy.rphone.view.components.phoneDisplayText(number.number),style=MaterialTheme.typography.titleMedium)
                        for(recording in number.assets) {
                            Card(onClick={selected=recording.id},modifier=Modifier.fillMaxWidth().testTag("recording-${recording.id}")) {
                                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT).format(java.util.Date(recording.createdAt)),style=MaterialTheme.typography.titleMedium)
                                    Text(recordingTime(recording.durationMs),style=MaterialTheme.typography.bodyMedium)
                                    val textCounts=counts[recording.id] ?: (0 to 0)
                                    val content=buildList {
                                        add(stringResource(R.string.recordings_audio))
                                        if(textCounts.first>0)add(stringResource(R.string.recordings_original))
                                        if(textCounts.second>0)add(androidx.compose.ui.res.pluralStringResource(R.plurals.recordings_translations,textCounts.second,textCounts.second))
                                    }
                                    Text(content.joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
                                    jobs.firstOrNull {it.recordingId==recording.id}?.let {RecordingJobStatus(it)}
                                }
                            }
                        }
                    }
                }
            }
        }
        if(settingsOnly)item("configuration") {
            Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(!moduleEnabled)Text(stringResource(R.string.recordings_module_off),style=MaterialTheme.typography.bodyLarge)
                else {
                    if(repo.integrationEnabled() && dev.goodwy.rphone.BuildConfig.TEST_DISTRIBUTION && injectedRepository==null)IntegratedCaptureSettings()
                    if(repo.integrationEnabled())DotSettingsCard {
                        Text(stringResource(R.string.rec_ui_processing),Modifier.padding(16.dp),style=MaterialTheme.typography.titleMedium)
                        DotSettingsNavigationRow(stringResource(if(capabilities.connected)R.string.rec_ui_connection_ready else R.string.recordings_connect),
                            supporting=stringResource(when(check) {
                                RecordingBridgeCheck.CHECKING->R.string.recordings_checking
                                RecordingBridgeCheck.FAILED->R.string.recordings_check_failed
                                RecordingBridgeCheck.IDLE->if(access==RecordingAccess.READY)R.string.rec_ui_api_hint else recordingAccessText(access)
                            }),contentActive=access==RecordingAccess.READY && check==RecordingBridgeCheck.IDLE,onClick=::configure)
                        if(check==RecordingBridgeCheck.CHECKING)LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=16.dp).testTag("recordings-checking"))
                        if(access==RecordingAccess.READY && RecordingPolicy.processing(plans.effective,capabilities,true)!=RecordingAccess.READY)
                            Text(stringResource(R.string.recordings_translation_unavailable),Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium)
                        DotSettingsDivider()
                        val output=remember(preferences) {repo.outputLanguage()}
                        DotSettingsNavigationRow(stringResource(R.string.capture_output_language),
                            supporting=if(output.isEmpty())stringResource(R.string.capture_original_language)else java.util.Locale.forLanguageTag(output).getDisplayName(displayLocale),
                            contentActive=access==RecordingAccess.READY,onClick={if(access==RecordingAccess.READY)outputPicker=true else configure()})
                        Text(stringResource(R.string.rec_ui_output_hint),Modifier.padding(horizontal=16.dp).padding(bottom=12.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        DotSettingsDivider()
                        val auto=remember(preferences) {repo.optedIn()}
                        val translation=remember(preferences) {repo.translationLanguage()}
                        if(!complete)Text(AppPlan.PREMIUM.displayName,Modifier.padding(horizontal=16.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        DotSettingsSwitchRow(stringResource(R.string.recordings_auto_transcribe),stringResource(R.string.rec_ui_automatic_hint),auto,
                            {value ->if(!complete)plans.requestUpgrade(PlanFeature.RECORDINGS_AUTOMATIC)else if(value && access!=RecordingAccess.READY)configure()else run {repo.setAutomatic(value)}},contentActive=complete && access==RecordingAccess.READY)
                        DotSettingsDivider()
                        DotSettingsSwitchRow(stringResource(R.string.recordings_auto_translate),supporting=stringResource(R.string.rec_ui_translation_hint),
                            checked=translation.isNotEmpty(),onCheckedChange={value ->if(!complete)plans.requestUpgrade(PlanFeature.RECORDINGS_AUTOMATIC)else if(value && RecordingPolicy.processing(plans.effective,capabilities,true)!=RecordingAccess.READY)configure()else if(value)languagePicker=true else run {repo.setTranslation("")}},contentActive=complete && RecordingPolicy.processing(plans.effective,capabilities,true)==RecordingAccess.READY)
                        if(translation.isNotEmpty())DotSettingsNavigationRow(stringResource(R.string.rec_ui_translation_title),
                            supporting=java.util.Locale.forLanguageTag(translation).getDisplayName(displayLocale),contentActive=complete && access==RecordingAccess.READY,
                            onClick={if(!complete)plans.requestUpgrade(PlanFeature.RECORDINGS_AUTOMATIC)else if(RecordingPolicy.processing(plans.effective,capabilities,true)!=RecordingAccess.READY)configure()else languagePicker=true})
                    }
                    if(repo.integrationEnabled())DotSettingsCard {
                        Text(stringResource(R.string.rec_ui_more),Modifier.padding(16.dp),style=MaterialTheme.typography.titleMedium)
                        Box(Modifier.alpha(if(access==RecordingAccess.READY)1f else .45f).testTag("recordings-import")) {
                            DotSettingsNavigationRow(stringResource(R.string.recordings_import),onClick={if(!busy){if(access==RecordingAccess.READY)import.launch(arrayOf("audio/*"))else configure()}})
                        }
                        if(capabilities.connected) {
                            DotSettingsDivider()
                            DotSettingsNavigationRow(stringResource(R.string.recordings_open_dictate),onClick={context.packageManager.getLaunchIntentForPackage(DictateProtocol.PACKAGE)?.let {context.startActivity(it)}})
                            TextButton(onClick={run {repo.disconnect()}},modifier=Modifier.padding(horizontal=8.dp)) {Text(stringResource(R.string.recordings_disconnect))}
                        }
                    }
                }
                SettingsHelpText(R.string.guide_recording)
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(error)Text(stringResource(errorText),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("recordings-error"))
            }
        }
        if(!settingsOnly && assets.isEmpty())item("empty") {Text(stringResource(R.string.recordings_empty),style=MaterialTheme.typography.bodyLarge)}
        item("bottom") {Spacer(Modifier.height(24.dp))}
    }
}

internal fun recordingAccessText(access: RecordingAccess): Int=when(access) {
    RecordingAccess.READY->R.string.recordings_ready
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
