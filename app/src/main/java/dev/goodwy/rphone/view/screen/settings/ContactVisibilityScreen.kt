package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.accounts.Account
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.controller.util.contactAccountProviderName
import dev.goodwy.rphone.controller.util.loadContactAccountProviderLabels
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillSwitchListItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.screen.settings.components.SettingsImmediateSection
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContactVisibilityScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val viewModel: ContactsViewModel = koinActivityViewModel()
    val accounts by viewModel.availableAccounts.collectAsStateWithLifecycle()
    val visibleAccounts by viewModel.visibleAccountsFlow.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val providerLabels by produceState(emptyMap<String, String>(), context, accounts, configuration) {
        value = withContext(Dispatchers.IO) { loadContactAccountProviderLabels(context) }
    }

    val currentVisible = remember(visibleAccounts, accounts) {
        visibleAccounts ?: (accounts.map { "${it.type}|${it.name}" } + "local|local").toSet()
    }

    fun toggleAccount(key: String, enabled: Boolean) {
        val newSet = if (enabled) currentVisible + key else currentVisible - key
        viewModel.setVisibleAccounts(newSet)
    }

    val listState = rememberSettingsListState()
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    fun navigateBack() {
        isClosing = true
        scope.launch {
            delay(280.milliseconds)
            navigator.navigateUp()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible && !isClosing) 1f else 0f,
        animationSpec = if (isClosing) tween(280, easing = FastOutLinearInEasing) else tween(350),
        label = "settingsAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible && !isClosing) 0.dp else if (isClosing) 60.dp else 30.dp,
        animationSpec = if (isClosing) tween(300, easing = FastOutLinearInEasing)
        else spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "settingsOffsetY"
    )
    LaunchedEffect(Unit) { visible = true }

    val rotation = context.currentDisplayRotation()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title(stringResource(R.string.managing_contact_sources)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                }
            )
        }
    ) { padding ->
        BackHandler { navigateBack() }
        ScrollHapticsEffect(listState = listState)
        LazyColumn(state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .alpha(alpha)
                .offset { IntOffset(0, offsetY.roundToPx()) },
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsImmediateSection() {
                    Text(
                        text = stringResource(R.string.managing_contact_sources_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    )
                }
            }

            item {
                SettingsImmediateSection() {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.contact_sources))
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline = ContactUtils.getFriendlyAccountName(null),
                                supporting = stringResource(R.string.contacts_stored_on_device),
                                leadingIcon = ContactUtils.getAccountIcon(null),
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = currentVisible.contains("local|local"),
                                onCheckedChange = { isChecked: Boolean ->
                                    toggleAccount(
                                        "local|local",
                                        isChecked
                                    )
                                }
                            )

                            accounts.forEach { account ->
                                val key = "${account.type}|${account.name}"
                                ContactSourceVisibilityRow(
                                    account = account,
                                    providerLabel = providerLabels[account.type],
                                    checked = currentVisible.contains(key),
                                    onCheckedChange = { isChecked: Boolean ->
                                        toggleAccount(
                                            key,
                                            isChecked
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
//            item {
//                SettingsImmediateSection() {
//                    TextButton(
//                        onClick = { viewModel.setVisibleAccounts(null) },
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        Text("Reset to Show All")
//                    }
//                }
//            }

            item { SettingsBottomPadding() }
        }
    }
}

@Composable
internal fun ContactSourceVisibilityRow(
    account: Account,
    providerLabel: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    RillSwitchListItem(
        headline = contactAccountProviderName(account, providerLabel),
        supporting = account.name,
        leadingIcon = ContactUtils.getAccountIcon(account),
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}
