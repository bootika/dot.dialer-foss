package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.AmbientDropletPreference
import dev.goodwy.rphone.controller.util.PreferenceManager

/** Switch and opacity control kept together so disabled-state behavior cannot drift. */
@Composable
internal fun AmbientDropletSettingsControls(
    prefs: PreferenceManager,
    settingsVersion: Int,
) {
    var enabled by remember(settingsVersion) {
        mutableStateOf(
            prefs.getBoolean(
                AmbientDropletPreference.ENABLED_KEY,
                AmbientDropletPreference.DEFAULT_ENABLED,
            ),
        )
    }
    var brightness by remember(settingsVersion) {
        mutableIntStateOf(
            prefs.getInt(
                AmbientDropletPreference.BRIGHTNESS_KEY,
                AmbientDropletPreference.DEFAULT_BRIGHTNESS,
            ).coerceIn(
                AmbientDropletPreference.MIN_BRIGHTNESS,
                AmbientDropletPreference.MAX_BRIGHTNESS,
            ),
        )
    }

    val feature = io.github.bootika.dotdialer.core.license.PlanFeature.PREMIUM_APPEARANCE
    val allowed = remember(settingsVersion) { prefs.allowsFeature(feature) }
    DotSettingsDivider()
    DotSettingsSwitchRow(
        title = stringResource(R.string.ambient_droplets),
        supporting = stringResource(R.string.ambient_droplets_supporting),
        checked = enabled, contentActive = allowed,
        onCheckedChange = {
            if (prefs.requireFeature(feature)) {
                enabled = it
                prefs.setBoolean(AmbientDropletPreference.ENABLED_KEY, it)
            }
        },
    )
    DotSettingsDivider()
    AmbientDropletBrightnessControl(
        brightness = brightness,
        enabled = enabled,
        onBrightnessChange = { brightness = it },
        onBrightnessChangeFinished = {
            prefs.setInt(AmbientDropletPreference.BRIGHTNESS_KEY, brightness)
        },
    )
}

@Composable
private fun AmbientDropletBrightnessControl(
    brightness: Int,
    enabled: Boolean,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessChangeFinished: () -> Unit,
) {
    val opacityPercent = AmbientDropletPreference.opacityPercent(brightness)
    val opacityLabel = if (opacityPercent % 1f == 0f) opacityPercent.toInt().toString() else opacityPercent.toString()
    SettingsPercentageSlider(
        title = stringResource(R.string.ambient_droplet_brightness), label = "$opacityLabel%",
        value = opacityPercent, minimum = AmbientDropletPreference.MIN_OPACITY_PERCENT,
        maximum = AmbientDropletPreference.MAX_OPACITY_PERCENT, step = AmbientDropletPreference.OPACITY_STEP_PERCENT,
        enabled = enabled, tag = "ambient-opacity-slider",
        onValueChange = { onBrightnessChange(AmbientDropletPreference.storedBrightness(it)) },
        onValueChangeFinished = onBrightnessChangeFinished,
    )
}
