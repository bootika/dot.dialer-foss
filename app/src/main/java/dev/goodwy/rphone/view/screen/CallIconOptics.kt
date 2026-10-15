package dev.goodwy.rphone.view.screen

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import dev.goodwy.rphone.view.components.DotDialerHangupIcon
import dev.goodwy.rphone.view.components.CallControlIconStyle

/** Optical viewport sizes, not button sizes. Sparse glyphs need more room than dense ones.
 * All incoming modes use the same handset pair; gestures, hit targets and colors are independent.
 * Do not scale with fontScale: the label may grow but the symbol and its circle stay stable.
 */
internal fun callActionIconSize(icon: ImageVector): Dp = when (icon) {
    DotDialerHandsetIcon, DotDialerHangupIcon -> CallControlIconStyle.PhoneSizeDp.dp
    else -> icon.defaultWidth
}
