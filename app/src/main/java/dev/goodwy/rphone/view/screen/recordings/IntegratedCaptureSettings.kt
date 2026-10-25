package dev.goodwy.rphone.view.screen.recordings

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.recordings.*
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.screen.settings.DotSettingsNavigationRow
import dev.goodwy.rphone.view.screen.settings.DotSettingsSwitchRow
import dev.goodwy.rphone.view.screen.settings.rememberPlanAccessManager
import io.github.bootika.dotdialer.core.license.PlanFeature
import kotlinx.coroutines.*

@Composable
internal fun IntegratedCaptureSettings() {
    val context=LocalContext.current
    val backend=remember{IntegratedCallCapture.get(context)}
    val scope=rememberCoroutineScope()
    val plans=rememberPlanAccessManager()
    val rights by plans.state.collectAsState()
    val allowed=remember(rights){plans.allows(PlanFeature.RECORDINGS_MANUAL)}
    var ready by remember {mutableStateOf<Boolean?>(null)}
    val enabled by backend.optedIn.collectAsState()
    LaunchedEffect(enabled){ready=null;if(enabled)while(isActive){ready=backend.available();delay(3000)}}
    if(!enabled)return
    dev.goodwy.rphone.view.screen.settings.DotSettingsCard {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.capture_setup),style=MaterialTheme.typography.titleMedium)
            Text(stringResource(if(!allowed)R.string.recordings_plan_required else when(ready){true->R.string.rec_ui_ready;false->R.string.rec_ui_activation;null->R.string.recordings_checking}),
                style=MaterialTheme.typography.bodyLarge,color=if(ready==true && allowed)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.testTag("capture-readiness"))
            Text(stringResource(R.string.rec_ui_capture_hint),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.capture_preconnect_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(ready==false || !allowed)Button(onClick={if(allowed)context.startActivity(Intent(context,CaptureSetupActivity::class.java))else plans.requestUpgrade(PlanFeature.RECORDINGS_MANUAL)}) {
                Text(stringResource(if(allowed)R.string.capture_activate else R.string.recordings_plan_required))
            }
        }
    }
}
