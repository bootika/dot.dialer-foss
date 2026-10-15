package io.github.bootika.dotdialer.core.preferences

/** Dialpad tint transparency only. Optical preset never depends on the slider. */
object DialpadGlassPolicy {
    const val DEFAULT_STRENGTH_PERCENT = 30
    const val STEP_PERCENT = 10
    fun normalizeStrength(value: Any?): Int = (value as? Int)?.coerceIn(0, 100)
        ?.let { ((it + STEP_PERCENT / 2) / STEP_PERCENT) * STEP_PERCENT } ?: DEFAULT_STRENGTH_PERCENT

    enum class Mode { NORMAL, GLASS }
    data class Optics(val saturation: Float = 1.4f, val blurScale: Float = 1f,
        val refractionHeightDp: Float = 18f, val refractionAmountDp: Float = 52f,
        val highlightAlpha: Float = 1f, val shadowAlpha: Float = 1f)
    private val fixedOptics = Optics()
    data class Effect(val mode: Mode, val fillAlpha: Float, val optics: Optics)

    fun effect(glass: Boolean, strength: Int, supported: Boolean, hasBackdrop: Boolean): Effect {
        val percent = normalizeStrength(strength)
        val mode = when {
            glass && supported && hasBackdrop -> Mode.GLASS
            else -> Mode.NORMAL
        }
        val alpha = when { mode == Mode.NORMAL || percent == 0 -> 1f; percent == 100 -> 0.35f
            else -> 1f - 0.65f * percent / 100f }
        return Effect(mode, alpha, fixedOptics)
    }
}
