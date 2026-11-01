package dev.goodwy.rphone.view.screen.recordings

import android.telecom.Call
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Transcribe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.R
import dev.goodwy.rphone.recordings.*
import dev.goodwy.rphone.view.screen.MoreItem
import dev.goodwy.rphone.view.screen.rememberCallConnectionStatusFeedback
import dev.goodwy.rphone.view.screen.rememberTelecomCallSnapshot
import dev.goodwy.rphone.view.screen.settings.rememberPlanAccessManager
import io.github.bootika.dotdialer.core.license.PlanFeature
import io.github.bootika.dotdialer.core.recordings.RecordingAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun CallCaptureActions(call: Call,title: String,onStarted: ()->Unit) {
    if(!BuildConfig.TEST_DISTRIBUTION || !BuildConfig.DICTATE_INTEGRATION_ENABLED)return
    val context=LocalContext.current;val capture=remember(context){AdbCallCapture.get(context)}
    val optedIn by remember(context){IntegratedCallCapture.get(context)}.optedIn.collectAsState()
    if(!optedIn)return
    val repo=rememberRecordingsRepository();val plans=rememberPlanAccessManager()
    val moduleEnabled by repo.moduleState.collectAsState()
    if(!moduleEnabled)return
    val state by capture.state.collectAsState()
    val telecom by rememberTelecomCallSnapshot(call)
    val capabilities by repo.bridge.capabilities.collectAsState()
    var backend by remember {mutableStateOf(false)}
    var unavailable by remember {mutableStateOf(false)}
    LaunchedEffect(capture){while(isActive){backend=capture.available();delay(3000)}}
    fun start(transcribe: Boolean) {
        if(!plans.allows(PlanFeature.RECORDINGS_MANUAL)){plans.requestUpgrade(PlanFeature.RECORDINGS_MANUAL);return}
        if(!backend || !repo.captureAllowed(transcribe)){unavailable=true;return}
        capture.start(call,title,RecordingCallKeys.key(call),transcribe);onStarted()
    }
    val enabled=!state.busy && captureCallStateAllowed(telecom.state) && !telecom.isConference && call.parent==null && telecom.children.isEmpty()
    Box(Modifier.alpha(if(backend)1f else .45f)) {
        MoreItem(stringResource(R.string.capture_record),Icons.Rounded.FiberManualRecord,enabled=enabled,onClick={start(false)})
    }
    Box(Modifier.alpha(if(backend && repo.captureAllowed(true))1f else .45f)) {
        MoreItem(stringResource(R.string.capture_transcribe),Icons.Rounded.Transcribe,enabled=enabled,onClick={start(true)})
    }
    if(unavailable)AlertDialog(onDismissRequest={unavailable=false},title={Text(stringResource(R.string.capture_setup))},
        text={Text(stringResource(if(!backend)R.string.capture_backend_required else if(!capabilities.separateSpeakerChannels)R.string.capture_dictate_update else R.string.capture_dictate_required))},
        confirmButton={TextButton(onClick={unavailable=false;if(!backend)context.startActivity(android.content.Intent(context,CaptureSetupActivity::class.java))}){Text(stringResource(if(!backend)R.string.capture_configure else R.string.close))}},
        dismissButton={if(!backend)TextButton(onClick={unavailable=false}){Text(stringResource(R.string.cancel))}})
}

@Composable
internal fun CallCaptureIndicator(call: Call) {
    if(!BuildConfig.TEST_DISTRIBUTION || !BuildConfig.DICTATE_INTEGRATION_ENABLED)return
    LocalCallCapturePresentation.current?.let {RecordingCaptureShield(it.first,it.second);return}
    val context=LocalContext.current
    val enabled by remember(context){IntegratedCallCapture.get(context)}.optedIn.collectAsState()
    if(!enabled)return
    val capture=remember(context){AdbCallCapture.get(context)}
    val state by capture.state.collectAsState()
    if(state.callKey!=RecordingCallKeys.key(call))return
    if(!state.busy) {
        if(state.error)Text(stringResource(R.string.capture_failed),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        return
    }
    RecordingCaptureShield(state,capture::stop)
}

internal val LocalCallCapturePresentation=staticCompositionLocalOf<Pair<CallCaptureState,()->Unit>?>{null}

@Composable
internal fun RecordingCaptureShield(state: CallCaptureState,onStop: ()->Unit) {
    val feedback=rememberCallConnectionStatusFeedback(onStop)
    val ruby=Color(0xFFC44960)
    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(vertical=6.dp).testTag("capture-indicator")) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(feedback.fill),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.heightIn(min=48.dp).padding(start=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if(state.phase=="waiting")MaterialTheme.colorScheme.onSurfaceVariant else ruby,CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(when(state.phase){"waiting"->stringResource(R.string.capture_waiting_connection);"starting"->stringResource(R.string.capture_starting);"saving"->stringResource(R.string.capture_saving)
                else->stringResource(R.string.capture_recording)+" · "+recordingTime(state.durationMs)},style=MaterialTheme.typography.bodyMedium,
                fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.weight(1f,false))
            if(state.phase!="saving")Row(Modifier.testTag("capture-stop").clickable(interactionSource=feedback.interaction,indication=null,onClick=feedback.onClick)
                .heightIn(min=48.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Rounded.Stop,null,tint=ruby,modifier=Modifier.size(20.dp));Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.capture_stop),color=ruby,style=MaterialTheme.typography.labelLarge)
            }else Spacer(Modifier.width(14.dp))
        }
        }
    }
}
