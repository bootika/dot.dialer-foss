package dev.goodwy.rphone.view.screen.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.LogoDev
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import dev.goodwy.rphone.view.screen.settings.components.SettingsImmediateSection
import dev.goodwy.rphone.view.screen.settings.SettingsBottomPadding
import dev.goodwy.rphone.view.screen.settings.SettingsSectionLabel

@Composable
internal fun SettingsHomeContent(
    listState: LazyListState,
    scaffoldPadding: PaddingValues,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SettingsSearchEntry>,
    navigator: DestinationsNavigator,
    appVersion: String,
    storeName: String,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onStartUiTour: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(top = scaffoldPadding.calculateTopPadding()),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SettingsSearchBar(value = searchQuery, onValueChange = onSearchQueryChange) }

        if (searchQuery.isNotBlank()) {
            item { SettingsSearchResults(searchQuery, searchResults, onSearchQueryChange) }
            item { Spacer(Modifier.height(80.dp).navigationBarsPadding()) }
        } else {
            item {
                SettingsHomeCard {
                    SettingsRootRow(SettingsFunction.INTERFACE.title, Icons.Rounded.Palette) { navigator.navigate(InterfaceScreenDestination()) }
                    SettingsRootRow(SettingsFunction.CALLS.title, DotDialerHandsetIcon) { navigator.navigate(CallSettingScreenDestination()) }
                    SettingsRootRow(SettingsFunction.CONTACTS.title, Icons.Rounded.PeopleAlt) { navigator.navigate(ContactManagementScreenDestination()) }
                    SettingsRootRow(SettingsFunction.SOUND.title, Icons.AutoMirrored.Rounded.VolumeUp) { navigator.navigate(SoundVibrationScreenDestination()) }
                    SettingsRootRow(SettingsFunction.PRIVACY.title, Icons.Rounded.Lock) { navigator.navigate(SettingsCategoryScreenDestination(category = "privacy")) }
                }
            }
            item {
                Column {
                    SettingsSectionLabel(stringResource(R.string.backup_and_restore))
                    SettingsHomeCard {
                        SettingsRootRow(R.string.create_backup, Icons.Rounded.Backup, onCreateBackup)
                        SettingsRootRow(R.string.restore_backup, Icons.Rounded.Restore, onRestoreBackup)
                    }
                }
            }
            item {
                SettingsHomeCard {
                    SettingsRootRow(SettingsFunction.HELP.title, Icons.Outlined.Info) { navigator.navigate(SettingsCategoryScreenDestination(category = "help")) }
                }
            }
            item { SettingsBottomPadding(120.dp) }
        }
    }
}

@Composable
private fun EssentialSettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsImmediateSection() {
        Column {
            SettingsSectionLabel(title)
            SettingsHomeCard(content)
        }
    }
}

@Composable
private fun SettingsSearchResults(
    query: String,
    results: List<SettingsSearchEntry>,
    onSearchQueryChange: (String) -> Unit,
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.no_settings_found, query),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        SettingsHomeCard {
            results.forEach { entry ->
                SettingsHomeListItem(
                    headline = entry.headline,
                    supporting = entry.supporting,
                    leadingIcon = entry.leadingIcon,
                    iconContainerColor = entry.iconContainerColor,
                    iconBgContainerColor = entry.iconBgContainerColor,
                    trailingIcon = Icons.Default.ChevronRight,
                    onClick = {
                        entry.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsRootRow(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    SettingsHomeListItem(headline = stringResource(label), leadingIcon = icon,
        trailingIcon = Icons.Default.ChevronRight, onClick = action)
}
