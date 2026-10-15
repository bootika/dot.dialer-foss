package dev.goodwy.rphone.view.theme

import androidx.compose.ui.unit.Density

/** Preserve Android's density/converter unchanged unless the user explicitly opts out. */
internal fun appTextDensity(systemDensity: Density, ignoreSystemFontScale: Boolean): Density =
    if (ignoreSystemFontScale) Density(density = systemDensity.density, fontScale = 1f)
    else systemDensity
