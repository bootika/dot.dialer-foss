package dev.goodwy.rphone.view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.expandedListDetails
import org.koin.compose.koinInject

@Composable
internal fun rememberExpandedListDetails(prefs: PreferenceManager = koinInject()): Boolean {
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    return remember(version) { prefs.expandedListDetails() }
}
