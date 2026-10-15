package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.rememberExpandedListDetails

@Composable
internal fun ExpandedListDetailsSetting(prefs: PreferenceManager) {
    val checked = rememberExpandedListDetails(prefs)
    DotSettingsSwitchRow(
        title = stringResource(R.string.expanded_list_details),
        supporting = stringResource(R.string.expanded_list_details_description),
        checked = checked,
        onCheckedChange = { prefs.setBoolean(PreferenceManager.KEY_EXPANDED_LIST_DETAILS, it) },
    )
}
