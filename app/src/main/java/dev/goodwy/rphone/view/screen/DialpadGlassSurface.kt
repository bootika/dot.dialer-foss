package dev.goodwy.rphone.view.screen

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.dialpadGlassStrengthPercent
import dev.goodwy.rphone.liquidglass.Backdrop
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.liquidglass.shadow.Shadow
import dev.goodwy.rphone.view.theme.MyColors.dialpadColor
import io.github.bootika.dotdialer.core.preferences.DialpadGlassPolicy

internal data class DialpadEffectState(val glass: Boolean, val strength: Int, val blurRadius: Float)

@Composable
internal fun rememberDialpadEffectState(prefs: PreferenceManager): DialpadEffectState {
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    return remember(version, prefs) {
        DialpadEffectState(prefs.getBoolean(PreferenceManager.KEY_LG_DIALPAD, false),
            prefs.dialpadGlassStrengthPercent(),
            prefs.getInt(PreferenceManager.KEY_BLUR_INTENSITY, 20).coerceIn(0, 100).toFloat())
    }
}

internal fun dialpadEffectFill(normal: Color, effectColor: Color, effect: DialpadGlassPolicy.Effect): Color =
    if (effect.mode == DialpadGlassPolicy.Mode.NORMAL) normal else effectColor.copy(alpha = effect.fillAlpha)

@Composable
internal fun dialpadBackdropVisibility(state: DialpadEffectState): Float = if (DialpadGlassPolicy.effect(
    state.glass, state.strength, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocalView.current.isHardwareAccelerated,
    LocalLiquidGlassBackdrop.current != null,
).mode == DialpadGlassPolicy.Mode.GLASS) 1f else 0f

/** Dim only the reflective rim on dark panels; preserve the light optical preset. */
internal fun dialpadPanelHighlight(alpha: Float, dark: Boolean): Highlight =
    Highlight.Default.copy(alpha = alpha * if (dark) .35f else 1f)

/** Same renderer for portrait and compact; the optical preset is independent of tint alpha. */
@Composable
internal fun DialpadGlassSurface(
    state: DialpadEffectState,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    content: @Composable () -> Unit,
) {
    val effect = DialpadGlassPolicy.effect(state.glass, state.strength,
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocalView.current.isHardwareAccelerated, backdrop != null)
    val optics = effect.optics
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val normal = dialpadColor
    val target = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
    val decorated = when {
        effect.mode == DialpadGlassPolicy.Mode.GLASS && backdrop != null -> modifier.drawBackdrop(
            backdrop = backdrop, shape = { shape },
            effects = {
                colorControls(saturation = optics.saturation)
                blur(state.blurRadius * density * optics.blurScale)
                lens(refractionHeight = optics.refractionHeightDp * density, refractionAmount = optics.refractionAmountDp * density)
            },
            highlight = { dialpadPanelHighlight(optics.highlightAlpha, dark) },
            shadow = { Shadow.Default.copy(alpha = optics.shadowAlpha) },
        )
        else -> modifier
    }
    Surface(modifier = decorated, shape = shape, color = dialpadEffectFill(normal, target, effect),
        shadowElevation = 0.dp, tonalElevation = 0.dp, content = content)
}
