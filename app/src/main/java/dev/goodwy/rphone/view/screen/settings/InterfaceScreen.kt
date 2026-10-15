package dev.goodwy.rphone.view.screen.settings

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.generated.destinations.AvatarsPreferenceScreenDestination
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.localization.AppLanguageCatalog
import org.koin.compose.koinInject

/** Compact Interface settings defined by the approved S25 design. */
@Destination<RootGraph>
@Composable
fun InterfaceScreen(navigator: DestinationsNavigator, advanced: Boolean = false, accessibility: Boolean = false) {
    if (advanced) { InterfaceScreen(navigator, accessibility = true); return }
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val settingsVersion by prefs.settingsChanged.collectAsStateWithLifecycle()

    var themeMode by remember(settingsVersion) {
        mutableStateOf(prefs.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto")
    }
    var accentColor by remember(settingsVersion) {
        mutableStateOf(prefs.getString(PreferenceManager.KEY_ACCENT_COLOR, "jade") ?: "jade")
    }
    var avatarSize by remember(settingsVersion) {
        mutableStateOf(
            prefs.getString(
                PreferenceManager.KEY_CONTACT_AVATAR_SIZE,
                PreferenceManager.DEFAULT_CONTACT_AVATAR_SIZE,
            ) ?: PreferenceManager.DEFAULT_CONTACT_AVATAR_SIZE,
        )
    }
    var contactNameSize by remember(settingsVersion) {
        mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_CONTACT_NAME_SIZE_SP, 18))
    }
    var interfaceScale by remember(settingsVersion) {
        mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_INTERFACE_SCALE_PERCENT, 100))
    }
    var subtleAnimations by remember(settingsVersion) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SCROLL_ANIMATION, false))
    }
    var highContrast by remember(settingsVersion) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_HIGH_CONTRAST_TEXT, false))
    }
    var ignoreSystemFontScale by remember(settingsVersion) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_IGNORE_SYSTEM_FONT_SCALE, PreferenceManager.DEFAULT_IGNORE_SYSTEM_FONT_SCALE))
    }

    val themeChoices = listOf(
        stringResource(R.string.theme_system) to "auto",
        stringResource(R.string.theme_light) to "light",
        stringResource(R.string.theme_dark) to "dark",
    )
    val accentChoices = listOf(
        stringResource(R.string.accent_jade) to "jade",
        stringResource(R.string.accent_blue) to "blue",
        stringResource(R.string.accent_violet) to "violet",
        stringResource(R.string.accent_coral) to "coral",
    )
    val avatarSizeChoices = listOf(
        stringResource(R.string.avatar_size_small) to PreferenceManager.CONTACT_AVATAR_SIZE_SMALL,
        stringResource(R.string.avatar_size_medium) to PreferenceManager.CONTACT_AVATAR_SIZE_MEDIUM,
        stringResource(R.string.avatar_size_large) to PreferenceManager.CONTACT_AVATAR_SIZE_LARGE,
    )
    val nameSizeChoices = listOf(16, 18, 20, 22).map { "$it sp" to it }
    val scaleChoices = listOf(90, 100, 110, 120).map { "$it%" to it }
    val languageChoices = buildList {
        add(stringResource(R.string.theme_system) to "")
        addAll(AppLanguageCatalog.options.map { it.nativeName to it.tag })
    }
    var appLanguageTag by remember {
        mutableStateOf(currentApplicationLanguageTag(context))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DotSettingsTopBar(
                title = stringResource(if (accessibility) R.string.accessibility else R.string.interface_settings),
                onBack = navigator::navigateUp,
            )
        },
    ) { padding ->
        LazyColumn(state = rememberSettingsListState(),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!accessibility) {
                item {
                    SettingsSectionLabel(stringResource(R.string.appearance))
                    DotSettingsCard {
                        DotSettingsStringChoiceRow(
                        title = stringResource(R.string.theme),
                        value = themeChoices.firstOrNull { it.second == themeMode }?.first
                            ?: stringResource(R.string.theme_system),
                        choices = themeChoices,
                        selectedValue = themeMode,
                        onValueChange = {
                            themeMode = it
                            prefs.setString(PreferenceManager.KEY_THEME_MODE, it)
                        },
                    )
                        DotSettingsDivider()
                        DotAccentColorChoiceRow(
                        title = stringResource(R.string.accent_color),
                        choices = accentChoices,
                        selectedValue = accentColor,
                        onValueChange = {
                            accentColor = it
                            prefs.setString(PreferenceManager.KEY_ACCENT_COLOR, it)
                        },
                    )
                        DotSettingsDivider()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        DotSettingsStringChoiceRow(
                            title = stringResource(R.string.app_language),
                            value = languageChoices.firstOrNull { it.second == appLanguageTag }?.first
                                ?: stringResource(R.string.theme_system),
                            choices = languageChoices,
                            selectedValue = appLanguageTag,
                            onValueChange = { languageTag ->
                                appLanguageTag = languageTag
                                setApplicationLanguage(context, languageTag)
                            },
                        )
                    } else {
                        DotSettingsNavigationRow(
                            title = stringResource(R.string.app_language),
                            value = stringResource(R.string.theme_system),
                            onClick = { openAppLanguageSettings(context) },
                        )
                    }
                    }
                }
            }

if (!accessibility) {
            item {
                SettingsSectionLabel(stringResource(R.string.keypad))
                DotSettingsCard { KeypadGlassSettings(prefs) }
            }
            item {
                DotSettingsCard { DotSettingsSwitchRow(stringResource(R.string.subtle_animations),
                    checked = subtleAnimations, onCheckedChange = {
                        subtleAnimations = it
                        prefs.setBoolean(PreferenceManager.KEY_SCROLL_ANIMATION, it)
                    }) }
            }
}
            if (accessibility) {
                item {
                    DotSettingsCard {
                        DotSettingsChoiceRow(stringResource(R.string.interface_scale), "$interfaceScale%",
                            scaleChoices, interfaceScale, { interfaceScale = it; prefs.setInt(PreferenceManager.KEY_INTERFACE_SCALE_PERCENT, it) })
                        DotSettingsDivider()
                        DotSettingsChoiceRow(stringResource(R.string.contact_name_size), "$contactNameSize sp",
                            nameSizeChoices, contactNameSize, { contactNameSize = it; prefs.setInt(PreferenceManager.KEY_CONTACT_NAME_SIZE_SP, it) })
                        DotSettingsDivider()
                        ExpandedListDetailsSetting(prefs)
                        DotSettingsDivider()
                        DotSettingsSwitchRow(stringResource(R.string.high_contrast_text), checked = highContrast,
                            onCheckedChange = { highContrast = it; prefs.setBoolean(PreferenceManager.KEY_HIGH_CONTRAST_TEXT, it) })
                    }
                }
                item { DotSettingsCard { DotSettingsNavigationRow(stringResource(R.string.voice_search_title),
                    onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.SettingsOptionScreenDestination("I12")) }) } }
            }

            if (!accessibility) {
                item {
                    DotSettingsCard {
                        DotSettingsNavigationRow(title = stringResource(R.string.avatars_settings),
                            onClick = { navigator.navigate(AvatarsPreferenceScreenDestination()) })
                    }
                }
            }
            if (!accessibility) {
                item { DotSettingsCard { DotSettingsNavigationRow(stringResource(R.string.call_screen),
                    onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.CallerUIScreenDestination()) }) } }
                item { DotSettingsCard { DotSettingsNavigationRow(stringResource(R.string.home_and_gestures),
                    onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.HomeGesturesScreenDestination) }) } }
                item { DotSettingsCard { DotSettingsNavigationRow(stringResource(R.string.accessibility),
                    onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.InterfaceScreenDestination(accessibility = true)) }) } }
            } else {
                item {
                    SettingsSectionLabel(stringResource(R.string.advanced_settings))
                    DotSettingsCard { SystemFontScaleSetting(ignoreSystemFontScale) {
                        ignoreSystemFontScale = it
                        prefs.setBoolean(PreferenceManager.KEY_IGNORE_SYSTEM_FONT_SCALE, it)
                    } }
                }
            }

        }
    }
}

private fun currentApplicationLanguageTag(context: Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return ""
    val rawTag = context.getSystemService(LocaleManager::class.java)
        .applicationLocales
        .toLanguageTags()
    return AppLanguageCatalog.canonicalTag(rawTag)
}

private fun setApplicationLanguage(context: Context, languageTag: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        openAppLanguageSettings(context)
        return
    }
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        if (languageTag.isBlank()) LocaleList.getEmptyLocaleList()
        else LocaleList.forLanguageTags(languageTag)
}

private fun openAppLanguageSettings(context: Context) {
    val packageUri = Uri.fromParts("package", context.packageName, null)
    val preferredIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, packageUri)
    } else {
        Intent(Settings.ACTION_LOCALE_SETTINGS)
    }
    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    runCatching { context.startActivity(preferredIntent) }
        .onFailure { context.startActivity(fallbackIntent) }
}
