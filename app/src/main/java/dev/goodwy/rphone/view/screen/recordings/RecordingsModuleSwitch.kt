package dev.goodwy.rphone.view.screen.recordings

import android.telecom.Call
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.filled.ChevronRight
import dev.goodwy.rphone.view.screen.settings.components.SettingsHomeListItem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch
import dev.goodwy.rphone.recordings.IntegratedCallCapture
import dev.goodwy.rphone.recordings.RecordingSyncJob
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.recordings.RecordingsRepository
import dev.goodwy.rphone.view.screen.MoreItem
import dev.goodwy.rphone.view.screen.settings.DotSettingsTopBar
import dev.goodwy.rphone.view.screen.settings.DotSettingsNavigationRow
import dev.goodwy.rphone.view.screen.settings.rememberPlanAccessManager
import io.github.bootika.dotdialer.core.recordings.RecordingAccess
import io.github.bootika.dotdialer.core.recordings.RecordingPolicy
import java.security.MessageDigest
import java.util.UUID
import java.util.WeakHashMap

/** Module intent stays separate from helper and transcription readiness. */
@Composable
internal fun RecordingsModuleSwitch(onOpenLibrary: (() -> Unit)? = null) {
    val repo=rememberRecordingsRepository()
    val plans=rememberPlanAccessManager();val rights by plans.state.collectAsState()
    val allowed=remember(rights){plans.allows(io.github.bootika.dotdialer.core.license.PlanFeature.RECORDINGS_MANUAL)}
    val enabled by repo.moduleState.collectAsState()
    val context=LocalContext.current;val backend=remember(context){IntegratedCallCapture.get(context)}
    val pending by backend.wirelessShutdownPending.collectAsState()
    val scope=rememberCoroutineScope();var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf(false)}
    LaunchedEffect(backend,enabled){backend.refreshWirelessOffStatus()}
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner){val observer=androidx.lifecycle.LifecycleEventObserver {_,event ->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)backend.refreshWirelessOffStatus()};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    val title=stringResource(R.string.recordings_title)
    Column {
        dev.goodwy.rphone.view.screen.settings.DotSettingsCard {
            Box(Modifier.testTag("recordings-master-switch")){
            dev.goodwy.rphone.view.screen.settings.DotSettingsSwitchRow(
                title=title,supporting=stringResource(if(enabled)R.string.rec_ui_master else R.string.recordings_master_off_summary),
                checked=enabled,onCheckedChange={value ->
                    if(!busy)if(value && !allowed)plans.requestUpgrade(io.github.bootika.dotdialer.core.license.PlanFeature.RECORDINGS_MANUAL)
                    else scope.launch {
                        busy=true;error=false
                        try{if(value){backend.enable();RecordingSyncJob.schedule(context);if(dev.goodwy.rphone.BuildConfig.DEBUG && !backend.available())context.startActivity(android.content.Intent(context,CaptureSetupActivity::class.java))}else backend.disable(stopWireless=true)}
                        catch(cancelled: kotlinx.coroutines.CancellationException){throw cancelled}
                        catch(_: Exception){error=true}finally{busy=false}
                    }
                },enabled=!busy)
            }
            if(onOpenLibrary!=null) {
                dev.goodwy.rphone.view.screen.settings.DotSettingsDivider()
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(start=16.dp)) {
                    Icon(Icons.Rounded.GraphicEq,null,tint=MaterialTheme.colorScheme.primary)
                    Box(Modifier.weight(1f)) {DotSettingsNavigationRow(stringResource(R.string.recordings_saved),onClick=onOpenLibrary)}
                }
            }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error)Text(stringResource(R.string.recordings_error),color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(16.dp))
        if(!enabled && pending)Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)) {
            Text(stringResource(R.string.recordings_wireless_off_needed),style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick={
            try{context.startActivity(android.content.Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"))}
            catch(_: Exception){context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))}
        },modifier=Modifier.testTag("recordings-wireless-off")){Text(stringResource(R.string.capture_open_wireless))}
        }
    }

}
