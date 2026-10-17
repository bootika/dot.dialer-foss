package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Portrait
import androidx.compose.material.icons.rounded.RememberMe
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.screen.settings.components.SettingsImmediateSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.customColors
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AvatarsPreferenceScreen(navigator: DestinationsNavigator, advanced: Boolean = false) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()

    var showFirstLetter           by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SHOW_FIRST_LETTER, true)) }
    var avatarFrame               by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_AVATAR_FRAME, false)) }
    var showPicture               by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SHOW_PICTURE, true)) }
    var hideAvatarWithBg          by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_HIDE_AVATAR_WITH_BACKGROUND, false)) }

    val context = LocalContext.current
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
                title = { Title(stringResource(R.string.avatars_settings)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(state = rememberSettingsListState(),
            modifier = Modifier
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsImmediateSection() {
                    AvatarSettingsRecentsPreview(prefs)
                }
            }
            item {
                SettingsImmediateSection() {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        RillExpressiveCard {
                            run {
                                RillSwitchListItem(
                                headline = stringResource(R.string.show_picture_in_avatar),
                                supporting = stringResource(R.string.show_picture_in_avatar_subtitle),
                                leadingIcon = if (showPicture) Icons.Rounded.Portrait else Icons.Rounded.TextFields,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                checked = showPicture,
                                onCheckedChange = { showPicture = it; prefs.setBoolean(PreferenceManager.KEY_SHOW_PICTURE, it) }
                            )
                                
                                AvatarListSizeControl(prefs)
                                RillSwitchListItem(
                                headline = stringResource(R.string.show_first_letter_in_avatar),
                                supporting = stringResource(R.string.show_first_letter_in_avatar_subtitle),
                                leadingIcon = if (showFirstLetter) Icons.Rounded.Title else Icons.Rounded.AccountCircle,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                checked = showFirstLetter,
                                onCheckedChange = { showFirstLetter = it; prefs.setBoolean(PreferenceManager.KEY_SHOW_FIRST_LETTER, it) }
                            )
                            }
                            run {
                                RillSwitchListItem(
                                headline = stringResource(R.string.avatar_frame),
                                modifier = Modifier.alpha(if (prefs.allowsFeature(io.github.bootika.dotdialer.core.license.PlanFeature.PREMIUM_APPEARANCE)) 1f else .45f),
                                supporting = stringResource(R.string.avatar_frame_subtitle),
                                leadingIcon = if (avatarFrame) ImageVector.vectorResource(id = R.drawable.ic_person_border)
                                                else ImageVector.vectorResource(id = R.drawable.ic_person_no_border),
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                checked = avatarFrame,
                                onCheckedChange = { if (prefs.requireFeature(io.github.bootika.dotdialer.core.license.PlanFeature.PREMIUM_APPEARANCE)) { avatarFrame = it; prefs.setBoolean(PreferenceManager.KEY_AVATAR_FRAME, it) } }
                            )
                            }
                        }
                    }
                }
            }

            item { SettingsBottomPadding() }
        }
    }
}
