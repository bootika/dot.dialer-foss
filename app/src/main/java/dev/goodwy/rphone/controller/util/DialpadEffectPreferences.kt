package dev.goodwy.rphone.controller.util

import io.github.bootika.dotdialer.core.preferences.DialpadGlassPolicy

/** Defensive against malformed, user-editable backups; absent/invalid types use 30%. */
fun PreferenceManager.dialpadGlassStrengthPercent(): Int = DialpadGlassPolicy.normalizeStrength(
    getAllPreferences()[PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH],
)
