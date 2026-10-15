package dev.goodwy.rphone.controller

internal data class FrostedCallEffect(
    val translucentBackground: Boolean,
    val backgroundBlur: Boolean,
)

/** Never exposes a readable screen underneath when Android disables cross-window blur. */
internal fun resolveFrostedCallEffect(
    requested: Boolean,
    crossWindowBlurAvailable: Boolean,
) = FrostedCallEffect(
    translucentBackground = requested && crossWindowBlurAvailable,
    backgroundBlur = requested && crossWindowBlurAvailable,
)
