package io.github.bootika.dotdialer.core.haptics

enum class HapticStrength {
    LIGHT,
    STRONG,
    CUSTOM;

    companion object {
        fun fromStorage(value: String): HapticStrength = when (value.lowercase()) {
            "strong" -> STRONG
            "custom" -> CUSTOM
            else -> LIGHT
        }
    }
}

enum class HapticIntent {
    TAP,
    KEYBOARD,
    CONFIRM,
    REJECT,
    LONG_PRESS,
    SCROLL_TICK,
}

data class HapticPulse(
    val durationMs: Long,
    val amplitude: Int,
)

/** Pure policy kept separate from the Android vibrator so it can be regression tested. */
object HapticPolicy {
    fun pulse(
        intent: HapticIntent,
        strength: HapticStrength,
        customIntensity: Float,
    ): HapticPulse {
        val normalizedIntensity = customIntensity.coerceIn(0f, 1f)
        val base = when (strength) {
            HapticStrength.LIGHT -> HapticPulse(durationMs = 16L, amplitude = 64)
            HapticStrength.STRONG -> HapticPulse(durationMs = 32L, amplitude = 180)
            HapticStrength.CUSTOM -> HapticPulse(
                durationMs = (10f + normalizedIntensity * 40f).toLong(),
                amplitude = (40f + normalizedIntensity * 180f).toInt(),
            )
        }

        return when (intent) {
            HapticIntent.KEYBOARD,
            HapticIntent.SCROLL_TICK,
            -> base.copy(
                durationMs = base.durationMs.coerceAtMost(12L),
                amplitude = base.amplitude.coerceAtMost(110),
            )

            HapticIntent.CONFIRM -> base.copy(amplitude = (base.amplitude + 20).coerceAtMost(255))
            HapticIntent.REJECT -> base.copy(
                durationMs = (base.durationMs + 10).coerceAtMost(60L),
                amplitude = (base.amplitude + 35).coerceAtMost(255),
            )

            HapticIntent.LONG_PRESS -> base.copy(durationMs = (base.durationMs + 8).coerceAtMost(55L))
            HapticIntent.TAP -> base
        }
    }
}
