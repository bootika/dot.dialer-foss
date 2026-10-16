package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.license.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun PlanInvitationContent(initialLink: String="",onClose: () -> Unit,
    manager: PlanAccessManager=rememberPlanAccessManager(),
    previewLoader: (String,String)->PlanInvitationPreview=::loadPlanInvitation) {
    val context=LocalContext.current
    val state by manager.state.collectAsState()
    val scope=rememberCoroutineScope()
    // Deliberately not saveable: bearer links are not put into saved-instance state or backups.
    var value by remember {mutableStateOf(initialLink)}
    var details by remember {mutableStateOf<PlanInvitationPreview?>(null)}
    var busy by remember {mutableStateOf(false)}
    var failed by remember {mutableStateOf(false)}
    var activated by remember {mutableStateOf(false)}
    val token=PlanInvitationLink.token(value,context.packageName)
    fun preview() {
        val selected=token ?: return
        scope.launch {
            busy=true;failed=false
            try {details=withContext(Dispatchers.IO) {previewLoader(selected,context.packageName)}}
            catch(error: CancellationException){throw error}
            catch(error: Exception){android.util.Log.w("DotInvitations",error.javaClass.simpleName);failed=true}
            finally {busy=false}
        }
    }
    LaunchedEffect(initialLink) {if(initialLink.isNotEmpty())preview()}
    Scaffold(topBar={DotSettingsTopBar(stringResource(R.string.invitation_title),onClose)}) {padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            if(activated) {
                Text(stringResource(R.string.invitation_activated),style=MaterialTheme.typography.headlineSmall)
                Button(onClick=onClose) {Text(stringResource(R.string.plan_guide_continue))}
            } else {
                Text(stringResource(R.string.invitation_body),style=MaterialTheme.typography.bodyLarge)
                OutlinedTextField(value=value,onValueChange={value=it;details=null;failed=false},enabled=!busy,label={Text(stringResource(R.string.invitation_link))},modifier=Modifier.fillMaxWidth().testTag("invitation-link"))
                if(details==null) Button(onClick=::preview,enabled=token!=null && !busy) {Text(stringResource(R.string.invitation_preview))}
                details?.let {info ->
                    DotSettingsCard {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(if(info.tier=="premium") "Premium" else "Plus",style=MaterialTheme.typography.headlineSmall)
                        Text(info.label,style=MaterialTheme.typography.titleMedium)
                        if(info.company.isNotEmpty())Text(info.company)
                        Text(when {
                            info.days>0 -> pluralStringResource(R.plurals.invitation_days,info.days,info.days)
                            info.until>0 -> stringResource(R.string.invitation_until,DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochSecond(info.until).atZone(ZoneId.systemDefault())))
                            else -> stringResource(R.string.invitation_unlimited)
                        })
                    }}
                    Button(onClick={scope.launch {
                        busy=true;failed=false
                        try {activated=withContext(Dispatchers.IO) {manager.refresh(force=true,invitation=token)};failed=!activated}
                        finally {busy=false}
                    }},enabled=!busy && state.ready && token!=null,modifier=Modifier.fillMaxWidth().testTag("invitation-activate")) {
                        Text(stringResource(R.string.invitation_confirm))
                    }
                }
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(failed)Text(stringResource(R.string.invitation_unavailable),color=MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun PlanInvitationDialog(link: String="",onClose: () -> Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.94f)) {PlanInvitationContent(link,onClose)}
    }
}
