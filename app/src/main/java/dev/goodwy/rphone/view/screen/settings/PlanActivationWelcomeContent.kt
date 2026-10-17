package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.GuideNavigationControls
import io.github.bootika.dotdialer.core.license.AppPlan

/** Presentation of already verified access; this page never activates or extends a license. */
@Composable
internal fun PlanActivationWelcomeContent(plan: AppPlan, onNext: () -> Unit, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag("plan-activation-welcome")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical=24.dp),
                horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
                Surface(shape=CircleShape, color=MaterialTheme.colorScheme.primaryContainer, modifier=Modifier.size(96.dp)) {
                    Box(contentAlignment=Alignment.Center) {
                        Icon(Icons.Rounded.Check, contentDescription=null, modifier=Modifier.size(48.dp), tint=MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Text(stringResource(R.string.plan_activation_congratulations), style=if(LocalDensity.current.fontScale > 1.5f) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge, textAlign=TextAlign.Center)
                Text(stringResource(R.string.plan_activation_active, plan.displayName), style=MaterialTheme.typography.headlineSmall,
                    color=MaterialTheme.colorScheme.primary, textAlign=TextAlign.Center)
                Text(stringResource(if(plan==AppPlan.PREMIUM) R.string.plan_premium_summary else R.string.plan_plus_summary),
                    style=MaterialTheme.typography.bodyLarge, textAlign=TextAlign.Center)
                Text(stringResource(R.string.plan_activation_setup), style=MaterialTheme.typography.bodyLarge,
                    color=MaterialTheme.colorScheme.onSurfaceVariant, textAlign=TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            GuideNavigationControls(isLast=false, onNext=onNext, onBack=onBack)
        }
    }
}
