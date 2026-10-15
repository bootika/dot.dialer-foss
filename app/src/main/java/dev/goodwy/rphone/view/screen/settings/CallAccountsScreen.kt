package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.util.Log
import android.view.Surface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallingAccountInfo
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.getCallingAccounts
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.CallingAccountIdentityIcon
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.Title
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallAccountsScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val accounts = remember { getCallingAccounts(context) }
    val legacyIndex = remember {
        prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, prefs.getDefaultSimIndexDefault())
    }
    val storedKey = remember { prefs.getString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, null) }
    var selectedKey by remember(accounts, storedKey, legacyIndex) {
        mutableStateOf(
            storedKey ?: legacyIndex.takeIf { it > 0 }
                ?.let { accounts.getOrNull(it - 1)?.key },
        )
    }

    LaunchedEffect(selectedKey, storedKey) {
        if (storedKey == null && selectedKey != null) {
            prefs.setString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, selectedKey)
        }
    }

    fun select(key: String?) {
        selectedKey = key
        prefs.setString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, key)
        prefs.setInt(PreferenceManager.KEY_DEFAULT_SIM, 0)
    }

    val rotation = context.currentDisplayRotation()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top,
                ),
                title = { Title(stringResource(R.string.calling_accounts)) },
                navigationIcon = { NavigationIcon(onClick = navigator::navigateUp) },
            )
        },
    ) { padding ->
        LazyColumn(state = rememberSettingsListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSectionLabel(stringResource(R.string.default_sim))
                Spacer(Modifier.height(8.dp))
                RillExpressiveCard {
                    CallingAccountRow(
                        account = null,
                        title = stringResource(R.string.ask_first),
                        description = stringResource(R.string.ask_first_calling_account_subtitle),
                        color = MaterialTheme.colorScheme.primary,
                        selected = selectedKey == null,
                        onClick = { select(null) },
                    )
                    accounts.forEach { account ->
                        CallingAccountRow(
                            account = account,
                            title = account.label,
                            description = account.description,
                            color = account.displayColor(MaterialTheme.colorScheme.primary),
                            selected = selectedKey == account.key,
                            onClick = { select(account.key) },
                        )
                    }
                }
            }

            if (accounts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_calling_accounts),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            item {
                RillExpressiveCard {
                    RillListItem(
                                expandText = true,
                        headline = stringResource(R.string.manage_calling_accounts_system),
                        supporting = stringResource(R.string.system_settings),
                        leadingIcon = Icons.Rounded.PhoneInTalk,
                        trailingIcon = Icons.Default.ChevronRight,
                        onClick = { openSystemCallingAccounts(context) },
                    )
                }
            }

            item { SettingsBottomPadding() }
        }
    }
}

@Composable
private fun CallingAccountRow(
    account: CallingAccountInfo?,
    title: String,
    description: String?,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = color.copy(alpha = 0.16f),
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (account != null) {
                    CallingAccountIdentityIcon(
                        account = account,
                        fallbackColor = color,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.SimCard,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/** SIM identity is the one Settings exception: keep Android's system-provided color. */
private fun CallingAccountInfo.displayColor(fallback: Color): Color =
    highlightColor.takeIf { Color(it).alpha > 0f }?.let(::Color) ?: fallback

private fun openSystemCallingAccounts(context: Context) {
    val intents = listOf(
        Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS),
        Intent("android.telecom.action.SHOW_CALL_SETTINGS"),
        Intent(android.provider.Settings.ACTION_SETTINGS),
    )
    var lastFailure: Exception? = null
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (error: Exception) {
            lastFailure = error
        }
    }
    Log.w("CallAccountsScreen", "No Android calling-account settings screen is available", lastFailure)
}
