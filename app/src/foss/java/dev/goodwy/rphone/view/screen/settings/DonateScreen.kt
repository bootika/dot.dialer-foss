package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.COFFEE_URL
import dev.goodwy.rphone.DONATE_URL
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.openLink
import dev.goodwy.rphone.controller.util.toast
import org.koin.compose.koinInject

@Destination<RootGraph>
@Composable
fun DonateScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    var isPro by remember(settingsState) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false))
    }
    val thankText = stringResource(R.string.thank_you_for_your_support)
    val lockedText = stringResource(R.string.features_locked)
    SupportScaffold(stringResource(R.string.support_development), { navigator.navigateUp() }, footer = {
        Button(
            onClick = { openLink(context, COFFEE_URL) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) { Text(stringResource(R.string.support_development), style = MaterialTheme.typography.titleMedium) }
        TextButton(onClick = { openLink(context, DONATE_URL) }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
            Text(stringResource(R.string.support_all_options))
        }
    }) {
        SupportIntroduction(stringResource(R.string.support_development_description1))
        DotSettingsCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.support_development_description2),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.support_development_description3),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.accentText)
            }
        }
        DotSettingsCard {
            DotSettingsSwitchRow(
                title = stringResource(R.string.unlock_all_features),
                supporting = if (isPro) stringResource(R.string.all_features_unlocked) else null,
                checked = isPro,
                onCheckedChange = {
                    isPro = it
                    prefs.setBoolean(PreferenceManager.KEY_IS_PRO_FOSS, it)
                    context.toast(if (it) thankText else lockedText)
                },
            )
        }
        DotSettingsCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.your_donation_ensures),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                listOf(R.string.your_donation_ensures_1, R.string.your_donation_ensures_2,
                    R.string.your_donation_ensures_3, R.string.your_donation_ensures_4).forEach { item ->
                    Text(stringResource(item), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
