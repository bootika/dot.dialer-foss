package dev.goodwy.rphone.view.screen.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Settings items are fully drawn on entry/reentry; no preferences, delay, alpha or translation. */
@Composable
internal fun SettingsImmediateSection(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) { content() }
}
