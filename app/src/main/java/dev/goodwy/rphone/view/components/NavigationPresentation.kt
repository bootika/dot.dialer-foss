package dev.goodwy.rphone.view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.goodwy.rphone.controller.util.PreferenceManager
import org.koin.compose.koinInject

/** Shared navigation-host presentation, independent of portrait/landscape scaffolding. */
@Composable
internal fun NavigationPresentation(neutralSettingsIcons: Boolean, content: @Composable () -> Unit) {
    val prefs = koinInject<PreferenceManager>()
    val snapshot = rememberOutgoingSimSnapshot(prefs)
    CompositionLocalProvider(
        LocalRillNeutralSettingsIcons provides neutralSettingsIcons,
        LocalOutgoingSimSnapshot provides snapshot,
        content = content,
    )
}
