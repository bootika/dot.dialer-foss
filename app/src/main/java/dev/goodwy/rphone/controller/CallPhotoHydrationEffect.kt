package dev.goodwy.rphone.controller

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.data.manager.CallStateManager
import org.koin.compose.koinInject

@Composable
internal fun rememberPhotoHydrationIdentityVersion(
    settingsVersion: Int,
): Int {
    val manager = koinInject<CallStateManager>()
    val revision by manager.metadataVersion.collectAsStateWithLifecycle()
    return settingsVersion * 31 + revision
}
