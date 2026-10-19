package dev.goodwy.rphone.view.screen.recordings

import android.telecom.Call
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

@Destination<RootGraph>
@Composable
fun RecordingsScreen(navigator: DestinationsNavigator) {
    Scaffold(topBar={DotSettingsTopBar(stringResource(R.string.recordings_title),navigator::navigateUp)}) {padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {RecordingsContent()}
    }
}

@Composable
internal fun RecordingsSettingsEntry(onOpen: ()->Unit) {
    if(!LocalContext.current.getSystemService(android.os.UserManager::class.java).isUserUnlocked)return
    val repo=rememberRecordingsRepository()
    val existing by produceState(false,repo) {value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {repo.catalog.recordings().isNotEmpty()}}
    if(!repo.integrationEnabled() && !existing)return
    RecordingsBridgeLifecycle(repo)
    val plans=rememberPlanAccessManager();val rights by plans.state.collectAsState()
    val capabilities by repo.bridge.capabilities.collectAsState()
    val active=remember(rights,capabilities) {RecordingPolicy.processing(plans.effective,capabilities)==RecordingAccess.READY}
    DotSettingsNavigationRow(stringResource(R.string.recordings_title),supporting=if(repo.integrationEnabled())stringResource(R.string.recordings_requirements)else null,contentActive=active || existing,onClick=onOpen)
}

@Composable
internal fun CallRecordingsEntry(call: Call,title: String) {
    if(!dev.goodwy.rphone.BuildConfig.DICTATE_INTEGRATION_ENABLED)return
    if(!LocalContext.current.getSystemService(android.os.UserManager::class.java).isUserUnlocked)return
    val repo=rememberRecordingsRepository()
    if(!repo.integrationEnabled())return
    RecordingsBridgeLifecycle(repo);val plans=rememberPlanAccessManager()
    val capabilities by repo.bridge.capabilities.collectAsState();val rights by plans.state.collectAsState()
    var open by remember {mutableStateOf(false)}
    val callKey=remember(call) {RecordingCallKeys.key(call)}
    val active=remember(rights,capabilities) {RecordingPolicy.processing(plans.effective,capabilities)==RecordingAccess.READY}
    Box(Modifier.alpha(if(active)1f else .45f)) {
        MoreItem(stringResource(R.string.recordings_title),leadingIcon=Icons.Rounded.GraphicEq,onClick={open=true})
    }
    if(open)ModalBottomSheet(onDismissRequest={open=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(stringResource(R.string.recordings_title),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                TextButton(onClick={open=false}) {Text(stringResource(R.string.close))}
            }
            Box(Modifier.weight(1f)) {RecordingsContent(callKey,title)}
        }
    }
}
internal val LocalRecordingsRepository=staticCompositionLocalOf<RecordingsRepository?> {null}

@Composable
internal fun rememberRecordingsRepository(): RecordingsRepository {
    val context=LocalContext.current
    val provided=LocalRecordingsRepository.current
    return remember(context,provided) {provided ?: RecordingsRepository.get(context)}
}
internal object RecordingCallKeys {
    private val fallback=WeakHashMap<Call,String>()
    @Synchronized fun key(call: Call): String {
        val details=call.details
        if(details.creationTimeMillis<=0)return fallback.getOrPut(call) {UUID.randomUUID().toString()}
        val material="${details.creationTimeMillis}|${details.accountHandle?.id}|${details.handle}"
        return MessageDigest.getInstance("SHA-256").digest(material.toByteArray()).joinToString("") {"%02x".format(it)}
    }
}
