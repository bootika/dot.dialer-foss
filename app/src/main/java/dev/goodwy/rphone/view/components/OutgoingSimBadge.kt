package dev.goodwy.rphone.view.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class OutgoingSimSnapshot(
    val prefs: PreferenceManager,
    val version: Int,
    val accounts: List<CallingAccountInfo>,
    val activeSimCount: Int,
    val icons: Map<String, ImageBitmap> = emptyMap(),
)
private data class LoadedOutgoingAccounts(
    val accounts: List<CallingAccountInfo> = emptyList(),
    val icons: Map<String, ImageBitmap> = emptyMap(),
)
internal val LocalOutgoingSimSnapshot = staticCompositionLocalOf<OutgoingSimSnapshot?> { null }

/** One off-main-thread Telecom snapshot per screen resume, not a Binder call per list row. */
@Composable
internal fun rememberOutgoingSimSnapshot(prefs: PreferenceManager): OutgoingSimSnapshot {
    val context = LocalContext.current
    val activeSims = rememberActiveSimCount()
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    var resume by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resume++ }
    val loaded by produceState(LoadedOutgoingAccounts(), resume, activeSims) {
        value = withContext(Dispatchers.IO) {
            prefs.refreshNumberSimRegion()
            val accounts = getCallingAccounts(context)
            val icons = accounts.mapNotNull { account ->
                loadCallingAccountIcon(context, account.icon)?.let {
                    account.key to it.toBitmap(48, 48).asImageBitmap()
                }
            }.toMap()
            LoadedOutgoingAccounts(accounts, icons)
        }
    }
    return remember(version, loaded, activeSims) {
        OutgoingSimSnapshot(prefs, version, loaded.accounts, activeSims, loaded.icons)
    }
}

@Composable
internal fun OutgoingSimBadge(number: String, modifier: Modifier = Modifier, contactId: String? = null) {
    val snapshot = LocalOutgoingSimSnapshot.current ?: return
    if (snapshot.activeSimCount < 2 || snapshot.accounts.size < 2) return
    val context = LocalContext.current
    val handle = remember(number, contactId, snapshot) {
        preferredOutgoingAccount(context, snapshot.prefs, number, contactId, snapshot.accounts)
    }
    val index = snapshot.accounts.indexOfFirst { it.handle == handle }
    val info = snapshot.accounts.getOrNull(index)
    if (info != null && !info.isSim) return
    val label = info?.label ?: stringResource(R.string.outgoing_sim_ask)
    val tint = info?.highlightColor?.takeIf { Color(it).alpha > 0f }?.let(::Color)
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
        val image = info?.let { snapshot.icons[it.key] }
        if (image != null) Image(image, null, Modifier.size(13.dp))
        else {
            Icon(Icons.Outlined.SimCard, null, tint = tint, modifier = Modifier.size(13.dp))
            Text(if (info == null) "?" else info.label.take(1).uppercase(), color = tint,
                fontSize = 10.sp, lineHeight = 12.sp)
        }
        Spacer(Modifier.width(3.dp))
    }
}
