package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.modal.data.StandardizeStats
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillLoadingIndicatorView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactMergeDuplicatesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactUnmergeDuplicatesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactVisibilityScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.ContactUtils.getAccountIcon
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.screen.settings.components.SettingsImmediateSection
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContactManagementScreen(
    navigator: DestinationsNavigator,
    toolsOnly: Boolean = false,
) {
    val prefs = org.koin.compose.koinInject<dev.goodwy.rphone.controller.util.PreferenceManager>()
    val planRevision by prefs.settingsChanged.collectAsStateWithLifecycle()
    val optimizerAllowed = remember(planRevision) { prefs.allowsFeature(io.github.bootika.dotdialer.core.license.PlanFeature.MANUAL_OPTIMIZER) }
    val context = LocalContext.current
    val viewModel: ContactsViewModel = koinActivityViewModel()
    var duplicateGroups by remember { mutableStateOf<List<List<Contact>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val standardizeProgress by viewModel.standardizeProgress.collectAsStateWithLifecycle()

    if (standardizeProgress != null) {
        RillDialog(
            onDismissRequest = {},
            title = stringResource(R.string.standardizing_numbers),
            icon = Icons.Rounded.FormatListNumbered
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            val progress = standardizeProgress ?: 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.standardize_progress, (progress * 100).toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }

    var showStandardizeDialog by remember { mutableStateOf(false) }
    var standardizeStats by remember { mutableStateOf<StandardizeStats?>(null) }

    standardizeStats?.takeIf { showStandardizeDialog }?.let { stats ->
        StandardizeConfirmationDialog(
            stats = stats,
            onConfirm = {
                viewModel.formatAllPhoneNumbers()
                showStandardizeDialog = false
            },
            onDismiss = {
                showStandardizeDialog = false
                standardizeStats = null
            }
        )
    }

    LaunchedEffect(toolsOnly) {
        if (toolsOnly) viewModel.findDuplicates { groups ->
            duplicateGroups = groups
            isLoading = false
        } else isLoading = false
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
                title = { Title(stringResource(if (toolsOnly) R.string.contact_tools else R.string.contacts_and_data)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                }
            )
        }
    ) { padding ->
        BackHandler { navigateBack() }
        ScrollHapticsEffect(listState = listState)
        if (isLoading) {
            RillLoadingIndicatorView()
        } else {
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
if (!toolsOnly) {
                item {
                    SettingsImmediateSection() {
                        RillExpressiveCard {
                            RillListItem(
                                expandText = true,
                                headline = stringResource(R.string.managing_contact_sources),
                                supporting = stringResource(R.string.managing_contact_sources_subtitle),
                                leadingIcon = Icons.Rounded.PeopleAlt,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(ContactVisibilityScreenDestination) }
                            )
                        }
                    }
                }
}





if (!toolsOnly) {
                item {
                    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
                    val displayOrder by viewModel.displayOrder.collectAsStateWithLifecycle()
                    SettingsImmediateSection() {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.display))
                            RillExpressiveCard {
                                RillSelectListItem(
                                    headline = stringResource(R.string.sort_by),
                                    leadingIcon = Icons.Rounded.SortByAlpha,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                    options = listOf(
                                        stringResource(R.string.first_name) to 0,
                                        stringResource(R.string.last_name) to 1
                                    ),
                                    selectedValue = sortOrder,
                                    onValueChange = { newValue: Int -> viewModel.setSortOrder(newValue) }
                                )
                                RillSelectListItem(
                                    headline = stringResource(R.string.name_format),
                                    leadingIcon = Icons.Rounded.Badge,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                    options = listOf(
                                        stringResource(R.string.first_name_first) to 0,
                                        stringResource(R.string.last_name_first) to 1
                                    ),
                                    selectedValue = displayOrder,
                                    onValueChange = { newValue: Int -> viewModel.setDisplayOrder(newValue) }
                                )
                            }
                        }
                    }
                }
}

if (!toolsOnly) {
                item {
                    SettingsImmediateSection {
                        DotSettingsCard {
                            DotSettingsNavigationRow(stringResource(R.string.contact_photos_title),
                                contentActive = optimizerAllowed,
                                onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.ContactPhotosScreenDestination()) })
                        }
                    }
                }
}

if (toolsOnly) {
                item {
                    SettingsImmediateSection() {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.merge_and_fix))
                            RillExpressiveCard {
                                RillListItem(
                                expandText = true,
                                    headline = stringResource(R.string.standardize_phone_numbers),
                                    supporting = stringResource(R.string.standardize_phone_numbers_subtitle),
                                    leadingIcon = Icons.Rounded.Handyman,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                    trailingIcon = Icons.Rounded.AutoFixHigh,
                                    onClick = {
                                        viewModel.previewStandardize { stats ->
                                            standardizeStats = stats
                                            showStandardizeDialog = true
                                        }
                                    }
                                )
                                RillListItem(
                                expandText = true,
                                    headline = stringResource(R.string.merging_contacts),
                                    supporting = stringResource(R.string.merging_contacts_subtitle),
                                    leadingIcon = Icons.Rounded.Merge,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                    trailingIcon = Icons.Default.ChevronRight,
                                    onClick = { navigator.navigate(ContactMergeDuplicatesScreenDestination) }
                                )
                                RillListItem(
                                expandText = true,
                                    headline = stringResource(R.string.unmerging_contacts),
                                    supporting = stringResource(R.string.unmerging_contacts_subtitle),
                                    leadingIcon = Icons.AutoMirrored.Rounded.CallSplit,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                    trailingIcon = Icons.Default.ChevronRight,
                                    onClick = { navigator.navigate(ContactUnmergeDuplicatesScreenDestination) }
                                )
                            }
                        }
                    }
                }
}


                if (!toolsOnly) {
                    item {
                        DotSettingsCard {
                            DotSettingsNavigationRow(title = stringResource(R.string.contact_tools),
                                onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.ContactManagementScreenDestination(toolsOnly = true)) })
                        }
                    }
                }
                item { SettingsBottomPadding() }
            }
        }
    }
}

@Composable
fun StandardizeConfirmationDialog(
    stats: StandardizeStats,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.standardize_phone_numbers),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.standardize_phone_numbers_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatRow(
                        label = stringResource(R.string.total_contacts),
                        value = stats.totalContacts.toString()
                    )
                    StatRow(
                        label = stringResource(R.string.contacts_with_changes),
                        value = stats.contactsWithChanges.toString()
                    )
                    StatRow(
                        label = stringResource(R.string.numbers_to_standardize),
                        value = stats.totalNumbersChanged.toString()
                    )
                }

                if (stats.contactsWithChanges == 0) {
                    Text(
                        stringResource(R.string.all_numbers_are_already_standardized),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.accentText,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                if (stats.examples.isNotEmpty()) {
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                    Text(
                        stringResource(R.string.examples_of_changes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    stats.examples.forEach { example ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = example.contactName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = example.oldNumber,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "=>",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.accentText
                                    )
                                    Text(
                                        text = example.newNumber,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.accentText
                                    )
                                }
                            }
                        }
                    }

                    if (stats.totalNumbersChanged > stats.examples.size) {
                        Text(
                            stringResource(R.string.and_more, "${stats.totalNumbersChanged - stats.examples.size}"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.accentText
                        )
                    }
                }

                if (stats.contactsWithChanges > 0) {
                    Text(
                        stringResource(R.string.warning_cannot_be_undone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = stats.contactsWithChanges > 0,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.standardize))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun StatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            fontWeight = FontWeight.Medium
        )
    }
}
