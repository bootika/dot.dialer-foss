package dev.goodwy.rphone.view.screen.recordings

import android.Manifest
import android.content.Intent
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.recordings.*
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.theme.DotDialerAppTheme
import kotlinx.coroutines.*

class CaptureSetupActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(!BuildConfig.TEST_DISTRIBUTION || !BuildConfig.DICTATE_INTEGRATION_ENABLED || !IntegratedCallCapture.get(this).enabled()){finish();return}
        setContent {DotDialerAppTheme {Surface(color=MaterialTheme.colorScheme.background){CaptureSetupWizard(::finish)}}}
    }
}

@Composable
internal fun CaptureSetupWizard(onClose: ()->Unit,previewStep: Int?=null) {
    val context=LocalContext.current
    val backend=remember{IntegratedCallCapture.get(context)}
    val state by backend.state.collectAsState()
    val optedIn by backend.optedIn.collectAsState()
    LaunchedEffect(optedIn){if(previewStep==null && !optedIn)onClose()}
    var step by rememberSaveable(previewStep){mutableIntStateOf(previewStep ?: if(backend.previouslyActivated())3 else 1)}
    var ready by remember{mutableStateOf(previewStep==4)}
    var code by remember{mutableStateOf("")}
    var port by remember{mutableStateOf("")}
    var connectPort by remember{mutableStateOf("")}
    var advanced by remember{mutableStateOf(false)}
    var networkHelp by rememberSaveable{mutableStateOf(false)}
    var requestedAction by remember{mutableIntStateOf(0)}
    var requestedGeneration by remember{mutableLongStateOf(-1)}
    val scope=rememberCoroutineScope()
    var exitCleanup by rememberSaveable { mutableStateOf(false) }
    fun requestClose() {
        val wireless=runCatching {Settings.Global.getInt(context.contentResolver,"adb_wifi_enabled",-1)}.getOrDefault(-1)
        if(previewStep==null && wireless==1) exitCleanup=true else onClose()
    }
    BackHandler { requestClose() }
    fun openPairing() {
        if(!backend.enabled())return
        backend.discover()
        context.startForegroundService(Intent(context,CapturePairingService::class.java).putExtra("capture_generation",backend.generation()))
        try {context.startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"))}
        catch(error: Exception){captureFailures.report(CaptureFailureStage.SETUP_SETTINGS,error);context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))}
    }
    val notification=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted ->if(backend.accepts(requestedGeneration)){if(granted)openPairing()else advanced=true}}
    fun beginPairing(){
        requestedGeneration=backend.generation()
        if(!backend.enabled())return
        if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)notification.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openPairing()
    }
    val localNetwork=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted ->if(backend.accepts(requestedGeneration)){if(granted){backend.discover();if(requestedAction==1)beginPairing()else scope.launch{backend.activate(connectPort.toIntOrNull() ?: 0,requestedGeneration)}}else advanced=true}}
    fun needsLocalNetwork()=Build.VERSION.SDK_INT>=37 && androidx.core.content.ContextCompat.checkSelfPermission(context,"android.permission.ACCESS_LOCAL_NETWORK")!=android.content.pm.PackageManager.PERMISSION_GRANTED
    val working=state.phase in setOf("pairing","starting","finishing")
    LaunchedEffect(Unit){if(previewStep==null){if(!backend.available())backend.discover();while(isActive){ready=backend.available();if(ready)step=4;else if(backend.state.value.phase=="paired")step=3;delay(2000)}}}
    DisposableEffect(Unit){onDispose{if(previewStep==null)backend.endDiscovery()}}
    fun settings(action: String) {try{context.startActivity(Intent(action))}catch(error: Exception){captureFailures.report(CaptureFailureStage.SETUP_SETTINGS,error);context.startActivity(Intent(if(action=="android.settings.WIRELESS_DEBUGGING_SETTINGS")Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_SETTINGS))}}
    if(exitCleanup) AlertDialog(onDismissRequest={exitCleanup=false},
        title={Text(stringResource(R.string.capture_wizard_ready_title))},
        text={Text(stringResource(R.string.capture_wireless_pending))},
        confirmButton={TextButton(onClick={exitCleanup=false;settings("android.settings.WIRELESS_DEBUGGING_SETTINGS")}){Text(stringResource(R.string.capture_open_wireless))}},
        dismissButton={TextButton(onClick={exitCleanup=false}){Text(stringResource(R.string.cancel))}})
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal=24.dp,vertical=12.dp).testTag("capture-wizard")) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={if(step>1 && !ready)step-- else requestClose()}){Icon(Icons.AutoMirrored.Rounded.ArrowBack,stringResource(R.string.back))}
            Text(stringResource(R.string.capture_setup),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
        }
        val stage=if(ready || state.phase=="finishing")3 else if(step<=1)1 else 2
        Text(stringResource(R.string.guide_stage,stage),style=MaterialTheme.typography.labelLarge,modifier=Modifier.testTag("capture-stage"))
        LinearProgressIndicator(progress={stage/3f},modifier=Modifier.fillMaxWidth().padding(vertical=16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text(stringResource(if(state.phase=="finishing")R.string.capture_wizard_ready_title else when(step){0->R.string.capture_wizard_developer_title;1->R.string.capture_wizard_developer_title;2->R.string.capture_wizard_pair_title;3->R.string.capture_reactivate;else->R.string.capture_wizard_ready_title}),style=MaterialTheme.typography.headlineMedium)
            val instructions=stringResource(if(state.phase=="finishing")R.string.guide_finish_work else when(step){0->R.string.capture_wizard_intro;1->R.string.capture_wizard_developer;2->R.string.capture_wizard_pair;3->R.string.capture_wizard_reactivate;else->R.string.capture_wizard_ready})
            instructions.split("\n\n").forEach { instruction ->
                Text(instruction,style=MaterialTheme.typography.bodyLarge)
            }
            if(Build.VERSION.SDK_INT<30)Text(stringResource(R.string.capture_android_required),color=MaterialTheme.colorScheme.error)
            when(step) {
                0->RillExpressiveCard {Text(stringResource(R.string.capture_wizard_privacy),Modifier.padding(20.dp),style=MaterialTheme.typography.bodyLarge)}
                1-> {
                    OutlinedButton(onClick={settings(Settings.ACTION_DEVICE_INFO_SETTINGS)},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.capture_open_about))}
                    OutlinedButton(onClick={settings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.capture_open_developer))}
                }
                2-> {
                    Button(onClick={
                        requestedAction=1;requestedGeneration=backend.generation()
                        if(needsLocalNetwork())localNetwork.launch("android.permission.ACCESS_LOCAL_NETWORK")else beginPairing()
                    },modifier=Modifier.fillMaxWidth().testTag("capture-open-pairing")){Text(stringResource(R.string.capture_open_pairing))}
                    TextButton(onClick={advanced=!advanced}){Text(stringResource(R.string.capture_pair_manual))}
                    if(advanced) {
                        Text(stringResource(R.string.capture_pair_manual_help),style=MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(port,{port=it.filter(Char::isDigit).take(5)},label={Text(stringResource(R.string.capture_pair_port))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(code,{code=it.filter(Char::isDigit).take(6)},label={Text(stringResource(R.string.capture_pair_code))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),singleLine=true,modifier=Modifier.fillMaxWidth())
                        Button(onClick={scope.launch{backend.pair(code,port.toIntOrNull() ?: state.pairingPort);code="";if(backend.state.value.phase=="paired")step=3}},enabled=!working && code.length==6,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.capture_pair_title))}
                    }
                }
                3-> {
                    OutlinedButton(onClick={backend.discover();settings("android.settings.WIRELESS_DEBUGGING_SETTINGS")},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.capture_open_wireless))}
                    TextButton(onClick={step=2}){Text(stringResource(R.string.capture_pair_again))}
                    TextButton(onClick={advanced=!advanced}){Text(stringResource(R.string.capture_connection_manual))}
                    if(advanced)OutlinedTextField(connectPort,{connectPort=it.filter(Char::isDigit).take(5)},label={Text(stringResource(R.string.capture_connect_port))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
                }
            }
            if(step==4) {
                Text(stringResource(R.string.capture_quiet_ready),style=MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.capture_preconnect_help),style=MaterialTheme.typography.bodyLarge)
            }
            if(step<=1)Text(stringResource(R.string.capture_wizard_intro),style=MaterialTheme.typography.bodyLarge)
            if(step<=1 || step==4)RillExpressiveCard {
                Text(stringResource(R.string.capture_network_help),Modifier.padding(20.dp),style=MaterialTheme.typography.bodyLarge)
            }
            if(step in 1..3) {
                TextButton(onClick={networkHelp=!networkHelp},modifier=Modifier.testTag("capture-network-help")) {
                    Text(stringResource(R.string.capture_network_recovery_title))
                }
                if(networkHelp || state.error=="start") {
                    stringResource(R.string.capture_network_recovery).split("\n\n").forEach { instruction ->
                        Text(instruction,style=MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if(state.error=="wireless" && !ready)OutlinedButton(onClick={settings("android.settings.WIRELESS_DEBUGGING_SETTINGS")},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.capture_open_wireless))}
            if(working)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.error.isNotEmpty() && !ready)Text(stringResource(when(state.error){"tls_identity"->R.string.capture_tls_identity_changed;"code"->R.string.capture_code_error;"pair"->R.string.capture_pair_error;"wireless"->R.string.capture_wireless_pending;"network"->R.string.capture_network_required;else->R.string.capture_start_error}),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            if(step>1 && !ready)FilledTonalIconButton(onClick={step--},modifier=Modifier.size(56.dp)){Icon(Icons.AutoMirrored.Rounded.ArrowBack,stringResource(R.string.back))}
            else Spacer(Modifier.size(56.dp))
            if(step==4)Button(onClick=::requestClose,modifier=Modifier.heightIn(min=56.dp)){Text(stringResource(R.string.capture_wizard_done))}
            else if(step==3)Button(onClick={requestedAction=2;requestedGeneration=backend.generation();if(needsLocalNetwork())localNetwork.launch("android.permission.ACCESS_LOCAL_NETWORK")else scope.launch{backend.activate(connectPort.toIntOrNull() ?: 0)}},enabled=!working && Build.VERSION.SDK_INT>=30,modifier=Modifier.heightIn(min=56.dp)){Text(stringResource(R.string.capture_activate))}
            else FilledIconButton(onClick={step=if(step<=1)2 else 3},enabled=!working && Build.VERSION.SDK_INT>=30,modifier=Modifier.size(56.dp)){Icon(Icons.AutoMirrored.Rounded.ArrowForward,stringResource(R.string.capture_next))}
        }
    }
}
