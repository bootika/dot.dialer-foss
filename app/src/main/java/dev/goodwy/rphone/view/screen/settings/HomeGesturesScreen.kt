package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.getRecentTapAction
import dev.goodwy.rphone.controller.util.setRecentTapAction
import dev.goodwy.rphone.controller.util.getSwipeRightAction
import dev.goodwy.rphone.controller.util.setSwipeRightAction
import dev.goodwy.rphone.controller.util.getSwipeLeftAction
import dev.goodwy.rphone.controller.util.setSwipeLeftAction
import dev.goodwy.rphone.controller.util.HOME_ROW_ACTIONS
import io.github.bootika.dotdialer.core.preferences.RecentHistoryPolicy
import org.koin.compose.koinInject

@Destination<RootGraph>
@Composable
fun HomeGesturesScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    val settingsVersion by prefs.settingsChanged.collectAsStateWithLifecycle()
    var tapAction by remember(settingsVersion) { mutableIntStateOf(prefs.getRecentTapAction()) }
    var swipeRight by remember(settingsVersion) { mutableIntStateOf(prefs.getSwipeRightAction()) }
    var swipeLeft by remember(settingsVersion) { mutableIntStateOf(prefs.getSwipeLeftAction()) }
    var showFavorites by remember(settingsVersion) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SHOW_RECENTS_FAVORITES, true))
    }
    var clearSearch by remember(settingsVersion) {
        mutableStateOf(
            prefs.getBoolean(
                PreferenceManager.KEY_CLEAR_SEARCH_AFTER_CALL,
                PreferenceManager.DEFAULT_CLEAR_SEARCH_AFTER_CALL,
            ),
        )
    }
    var repeatContacts by remember(settingsVersion) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_REPEAT_CONTACTS_IN_SEARCH_RESULTS, false))
    }
    var period by remember(settingsVersion) {
        mutableIntStateOf(
            prefs.getInt(
                PreferenceManager.KEY_RECENT_HISTORY_PERIOD_DAYS,
                RecentHistoryPolicy.DEFAULT_PERIOD_DAYS,
            ),
        )
    }
    var normalizeDiacritics by remember(settingsVersion) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SEARCH_DIACRITIC_NORMALIZATION, true))
    }
    var allCalls by remember(settingsVersion) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS, false)) }
    var maximum by remember(settingsVersion) { mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_RECENT_HISTORY_MAX_ENTRIES, RecentHistoryPolicy.DEFAULT_MAX_ENTRIES)) }
    val noActionLabel = stringResource(R.string.no_action)
    val tapChoices = homeRowActionChoices()
    val swipeChoices = homeRowActionChoices(includeDelete = true)
    fun swipeLabel(value: Int): String = swipeChoices.firstOrNull { it.second == value }?.first
        ?: noActionLabel
    val periodChoices = listOf(
        stringResource(R.string.last_30_days) to 30,
        stringResource(R.string.last_3_months) to 90,
        stringResource(R.string.last_6_months) to 180,
        stringResource(R.string.last_year) to 365,
        stringResource(R.string.all_history) to 0,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DotSettingsTopBar(
                title = stringResource(R.string.home_and_gestures),
                onBack = navigator::navigateUp,
            )
        },
    ) { padding ->
        LazyColumn(state = rememberSettingsListState(),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingsSectionLabel(stringResource(R.string.home_content))
                DotSettingsCard {
                    DotSettingsSwitchRow(stringResource(R.string.show_all_recent_calls),
                        stringResource(R.string.show_all_recent_calls_subtitle), allCalls,
                        { allCalls = it; prefs.setBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS, it) })
                    DotSettingsDivider()
                    DotSettingsSwitchRow(
                        title = stringResource(R.string.show_recents_favorites),
                        checked = showFavorites,
                        onCheckedChange = {
                            showFavorites = it
                            prefs.setBoolean(PreferenceManager.KEY_SHOW_RECENTS_FAVORITES, it)
                        },
                    )
                    DotSettingsDivider()
                    DotSettingsChoiceRow(
                        title = stringResource(R.string.period),
                        value = periodChoices.firstOrNull { it.second == period }?.first.orEmpty(),
                        choices = periodChoices,
                        selectedValue = period,
                        onValueChange = {
                            period = it
                            prefs.setInt(PreferenceManager.KEY_RECENT_HISTORY_PERIOD_DAYS, it)
                        },
                    )
                }
            }
            item {
                DotSettingsCard { DotSettingsChoiceRow(stringResource(R.string.recent_history_maximum),
                    maximum.toString(), listOf(100,250,500,1000).map { it.toString() to it }, maximum,
                    { maximum = it; prefs.setInt(PreferenceManager.KEY_RECENT_HISTORY_MAX_ENTRIES, it) }) }
            }
            item {
                SettingsSectionLabel(stringResource(R.string.actions))
                DotSettingsCard {
                    QuickActionGestureSetting(prefs)
                    DotSettingsDivider()
                    DotSettingsChoiceRow(
                        title = stringResource(R.string.tap_a_recent),
                        value = swipeLabel(tapAction),
                        choices = tapChoices,
                        selectedValue = tapAction,
                        onValueChange = {
                            tapAction = it
                            prefs.setRecentTapAction(it)
                        },
                    )
                    DotSettingsDivider()
                    DotSettingsChoiceRow(
                        title = stringResource(R.string.swipe_right_action),
                        value = swipeLabel(swipeRight),
                        choices = swipeChoices,
                        selectedValue = swipeRight,
                        onValueChange = {
                            swipeRight = it
                            prefs.setSwipeRightAction(it)
                            prefs.setBoolean(
                                PreferenceManager.KEY_SWIPE_TO_CALL,
                                swipeRight != PreferenceManager.SWIPE_ACTION_NONE ||
                                    swipeLeft != PreferenceManager.SWIPE_ACTION_NONE,
                            )
                        },
                    )
                    DotSettingsDivider()
                    DotSettingsChoiceRow(
                        title = stringResource(R.string.swipe_left_action),
                        value = swipeLabel(swipeLeft),
                        choices = swipeChoices,
                        selectedValue = swipeLeft,
                        onValueChange = {
                            swipeLeft = it
                            prefs.setSwipeLeftAction(it)
                            prefs.setBoolean(
                                PreferenceManager.KEY_SWIPE_TO_CALL,
                                swipeRight != PreferenceManager.SWIPE_ACTION_NONE ||
                                    swipeLeft != PreferenceManager.SWIPE_ACTION_NONE,
                            )
                        },
                    )
                }
            }
            item {
                SettingsSectionLabel(stringResource(R.string.search))
                DotSettingsCard {
                    VoiceSearchSetting(prefs)
                    DotSettingsDivider()
                    DotSettingsSwitchRow(
                        title = stringResource(R.string.search_ignore_diacritics),
                        supporting = stringResource(R.string.search_ignore_diacritics_subtitle),
                        checked = normalizeDiacritics,
                        onCheckedChange = {
                            normalizeDiacritics = it
                            prefs.setBoolean(PreferenceManager.KEY_SEARCH_DIACRITIC_NORMALIZATION, it)
                        })
                    DotSettingsDivider()
                    DotSettingsSwitchRow(
                        title = stringResource(R.string.search_repeat_contacts),
                        supporting = stringResource(R.string.settings_repeat_contacts_explanation),
                        checked = repeatContacts,
                        onCheckedChange = {
                            repeatContacts = it
                            prefs.setBoolean(PreferenceManager.KEY_REPEAT_CONTACTS_IN_SEARCH_RESULTS, it)
                        },
                    )
                    DotSettingsDivider()
                    DotSettingsSwitchRow(
                        title = stringResource(R.string.clear_search_after_call),
                        checked = clearSearch,
                        onCheckedChange = {
                            clearSearch = it
                            prefs.setBoolean(PreferenceManager.KEY_CLEAR_SEARCH_AFTER_CALL, it)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun homeRowActionChoices(includeDelete: Boolean = false): List<Pair<String, Int>> =
    (if (includeDelete) dev.goodwy.rphone.controller.util.HOME_SWIPE_ACTIONS else HOME_ROW_ACTIONS).map { action ->
    val label = when (action) {
        PreferenceManager.SWIPE_ACTION_NONE -> stringResource(R.string.no_action)
        PreferenceManager.SWIPE_ACTION_CALL -> stringResource(R.string.call)
        PreferenceManager.SWIPE_ACTION_MESSAGE -> stringResource(R.string.sms)
        PreferenceManager.SWIPE_ACTION_WHATSAPP -> "WhatsApp"
        PreferenceManager.SWIPE_ACTION_CONTACT -> stringResource(R.string.contact_action)
        PreferenceManager.SWIPE_ACTION_COPY_NUMBER -> stringResource(R.string.copy_action)
        PreferenceManager.SWIPE_ACTION_DELETE_ENTRY -> stringResource(R.string.delete)
        else -> error("Unsupported home action")
    }
    label to action
}
