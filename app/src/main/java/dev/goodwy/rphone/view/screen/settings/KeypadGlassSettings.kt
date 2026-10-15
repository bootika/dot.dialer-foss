package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.rememberDialpadEffectState
import java.text.NumberFormat
import io.github.bootika.dotdialer.core.preferences.DialpadGlassPolicy
import kotlin.math.roundToInt

@Composable
internal fun KeypadGlassSettings(prefs: PreferenceManager) {
    val state = rememberDialpadEffectState(prefs)
    var value by remember { mutableIntStateOf(state.strength) }
    var editing by remember { mutableStateOf(false) }
    val enabled = state.glass
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(state.strength) { if (!editing) value = state.strength }
    LaunchedEffect(enabled) { if (!enabled) { editing = false; value = state.strength } }
    EffectPreferenceRow(prefs, PreferenceManager.KEY_LG_DIALPAD, false, R.string.liquid_glass_dialpad)
    if (enabled) {
        SettingsPercentageSlider(
            title = stringResource(R.string.keypad_effect_strength),
            label = NumberFormat.getPercentInstance(locale).format(value / 100.0),
            value = value.toFloat(), minimum = 0f, maximum = 100f, step = DialpadGlassPolicy.STEP_PERCENT.toFloat(),
            enabled = true, tag = "keypad-transparency-slider",
            onValueChange = { editing = true; value = DialpadGlassPolicy.normalizeStrength(it.roundToInt()) },
            onValueChangeFinished = {
                prefs.setInt(PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH, value)
                editing = false
            },
        )
    }
}
