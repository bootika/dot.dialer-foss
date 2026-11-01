package dev.goodwy.rphone.view.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Meaningful status foregrounds do not inherit the user's interaction accent. */
internal val ColorScheme.semanticSuccess: Color
    get() = semanticSuccessColor(background.luminance() < 0.5f)

internal fun semanticSuccessColor(dark: Boolean): Color =
    if (dark) Color(0xFF4DE2A8) else Color(0xFF007A4D)
