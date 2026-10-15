package dev.goodwy.rphone.view.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal val SETTINGS_ICON_CREAM_FOREGROUND_LIGHT = Color(0xFF6D5530)
internal val SETTINGS_ICON_CREAM_BACKGROUND_LIGHT = Color(0xFFF3E5CA)
internal val SETTINGS_ICON_CREAM_FOREGROUND_DARK = Color(0xFFE8D4AE)
internal val SETTINGS_ICON_CREAM_BACKGROUND_DARK = Color(0xFF4A3E2E)

internal data class RillSettingsIconPalette(
    val foreground: Color,
    val background: Color,
)

@Composable
internal fun rillSettingsIconPalette(accented: Boolean): RillSettingsIconPalette {
    val colors = MaterialTheme.colorScheme
    if (accented) {
        return RillSettingsIconPalette(
            foreground = colors.primary,
            background = colors.primaryContainer,
        )
    }

    val darkTheme = colors.background.luminance() < 0.5f
    return if (darkTheme) {
        RillSettingsIconPalette(
            foreground = SETTINGS_ICON_CREAM_FOREGROUND_DARK,
            background = SETTINGS_ICON_CREAM_BACKGROUND_DARK,
        )
    } else {
        RillSettingsIconPalette(
            foreground = SETTINGS_ICON_CREAM_FOREGROUND_LIGHT,
            background = SETTINGS_ICON_CREAM_BACKGROUND_LIGHT,
        )
    }
}
