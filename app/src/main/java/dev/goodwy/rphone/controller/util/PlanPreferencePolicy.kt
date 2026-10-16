package dev.goodwy.rphone.controller.util

import io.github.bootika.dotdialer.core.license.PlanFeature

/** Presentation rights never erase stored settings or affect basic call controls. */
internal object PlanPreferencePolicy {
    private val effects = setOf("liquid_glass_ui", "ui_blur", "floating_bar_blur", "blur_effects_ui",
        "frosted_call_background", "ambient_droplets_enabled", "avatar_frame")
    fun feature(key: String): PlanFeature? = when {
        key in effects || key.startsWith("lg_") || key.startsWith("blur_") || key == "BLUR_dialpad" ||
            key in setOf("accent_color", "custom_font_path", "call_background_default") ||
            key.startsWith("contact_background_") -> PlanFeature.PREMIUM_APPEARANCE
        else -> null
    }
}

internal class PlanPreferenceAccess(private val allowed: (PlanFeature)->Boolean,
    private val request: (PlanFeature)->Unit, private val changed: ()->Unit) {
    fun allows(feature: PlanFeature)=allowed(feature)
    fun require(feature: PlanFeature): Boolean {
        if(allowed(feature)) return true
        request(feature);changed();return false
    }
    fun permitted(key: String)=PlanPreferencePolicy.feature(key)?.let(allowed)!=false
    fun write(key: String)=PlanPreferencePolicy.feature(key)?.let(::require) ?: true
}
