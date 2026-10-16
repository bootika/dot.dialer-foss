package dev.goodwy.rphone.view.screen.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.goodwy.rphone.COFFEE_URL
import dev.goodwy.rphone.DONATE_URL
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.openLink
import dev.goodwy.rphone.controller.util.toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject

/** Preserve the existing FOSS self-unlock and voluntary support paths alongside plan information. */
@Composable
internal fun FossPlanSupport() {
    val context=LocalContext.current
    val prefs=koinInject<PreferenceManager>()
    val revision by prefs.settingsChanged.collectAsState()
    val unlocked=remember(revision) {prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS,false)}
    val thanks=stringResource(R.string.thank_you_for_your_support)
    val locked=stringResource(R.string.features_locked)
    SupportIntroduction(stringResource(R.string.support_development_description1))
    Text(stringResource(R.string.support_development_description2),style=MaterialTheme.typography.bodyMedium)
    DotSettingsSwitchRow(stringResource(R.string.unlock_all_features),checked=unlocked,
        supporting=if(unlocked)stringResource(R.string.all_features_unlocked) else null,
        onCheckedChange={prefs.setBoolean(PreferenceManager.KEY_IS_PRO_FOSS,it);context.toast(if(it)thanks else locked)})
    DotSettingsCard { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.your_donation_ensures),style=MaterialTheme.typography.titleMedium)
        for(item in listOf(R.string.your_donation_ensures_1,R.string.your_donation_ensures_2,
            R.string.your_donation_ensures_3,R.string.your_donation_ensures_4)) Text(stringResource(item),style=MaterialTheme.typography.bodyMedium)
    } }
    TextButton(onClick={openLink(context,DONATE_URL)}) {Text(stringResource(R.string.support_all_options))}
    TextButton(onClick={openLink(context,COFFEE_URL)}) {Text(stringResource(R.string.support_development))}
}
