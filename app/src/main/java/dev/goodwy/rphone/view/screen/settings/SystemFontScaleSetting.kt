package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.goodwy.rphone.R

@Composable
internal fun SystemFontScaleSetting(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    DotSettingsSwitchRow(
        title = stringResource(R.string.ignore_system_font_scale),
        supporting = stringResource(R.string.ignore_system_font_scale_description),
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}
