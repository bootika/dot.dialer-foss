package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import dev.goodwy.rphone.view.components.DotDialerHangupIcon

import android.content.Context
import android.content.Intent
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.screen.settings.components.SettingsImmediateSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.color_call_end
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DonateScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.retainDocumentReadAccess
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.components.shake
import dev.goodwy.rphone.view.screen.HANGUP_BUTTON_HEIGHT_DP
import dev.goodwy.rphone.view.screen.hangupButtonGeometry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallerUIScreen(navigator: DestinationsNavigator, advanced: Boolean = false) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val scope = rememberCoroutineScope()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val purchaseHelper: PurchaseHelper = koinInject()
    val storePro by purchaseHelper.isPro.collectAsStateWithLifecycle()
    val plans=rememberPlanAccessManager()
    val planState by plans.state.collectAsStateWithLifecycle()
    val isPro=remember(planState,storePro) {storePro || plans.allows(io.github.bootika.dotdialer.core.license.PlanFeature.PREMIUM_APPEARANCE)}
    LaunchedEffect(Unit) {
        val savedIsProIap = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_IAP, false)
        val savedIsProSub = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_SUB, false)
        val savedIsProFoss = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false)
        if (savedIsProIap || savedIsProSub || savedIsProFoss) {
            purchaseHelper.setProStatusImmediate(true)
            purchaseHelper.checkProStatus()
        } else {
            purchaseHelper.checkProStatus()
        }
    }
    var enabledShake by remember { mutableStateOf(false) }
    var showSnackbar   by remember(settingsState) { mutableStateOf(false) }

    var themeMode   by remember(settingsState) { mutableStateOf(prefs.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto") }
    var callAppearance by remember(settingsState) {
        mutableStateOf(
            prefs.getString(
                PreferenceManager.KEY_CALL_SCREEN_APPEARANCE,
                PreferenceManager.DEFAULT_CALL_SCREEN_APPEARANCE,
            ) ?: PreferenceManager.DEFAULT_CALL_SCREEN_APPEARANCE,
        )
    }
    var frostedCallBackground by remember(settingsState) {
        mutableStateOf(
            prefs.getBoolean(
                PreferenceManager.KEY_FROSTED_CALL_BACKGROUND,
                PreferenceManager.DEFAULT_FROSTED_CALL_BACKGROUND,
            ),
        )
    }
    var incomingCallUiMode by remember(settingsState) {
        mutableIntStateOf(
            prefs.getInt(
                PreferenceManager.KEY_INCOMING_CALL_UI_MODE,
                PreferenceManager.DEFAULT_INCOMING_CALL_UI_MODE,
            ),
        )
    }
    var incomingAnswerPlacement by remember(settingsState) {
        mutableStateOf(
            prefs.getString(
                PreferenceManager.KEY_INCOMING_ANSWER_PLACEMENT,
                PreferenceManager.DEFAULT_INCOMING_ANSWER_PLACEMENT,
            ) ?: PreferenceManager.DEFAULT_INCOMING_ANSWER_PLACEMENT,
        )
    }
    var hangupWidth by remember(settingsState) {
        mutableFloatStateOf(
            prefs.getFloat(
                PreferenceManager.KEY_HANGUP_WIDTH,
                PreferenceManager.DEFAULT_HANGUP_WIDTH,
            ).coerceIn(0.1f, 1.0f),
        )
    }

    val callAppearanceChoices = listOf(
        stringResource(R.string.call_appearance_circular_avatar) to
            PreferenceManager.CALL_SCREEN_APPEARANCE_CIRCULAR_AVATAR,
        stringResource(R.string.call_appearance_full_photo) to
            PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO,
    )
    val incomingCallUiChoices = listOf(
        stringResource(R.string.incoming_call_ui_default_swipe) to
            PreferenceManager.INCOMING_CALL_UI_DEFAULT_SWIPE,
        stringResource(R.string.incoming_call_ui_tap_buttons) to
            PreferenceManager.INCOMING_CALL_UI_TAP_BUTTONS,
        stringResource(R.string.incoming_call_ui_samsung_drag) to
            PreferenceManager.INCOMING_CALL_UI_SAMSUNG_RADIAL,
        stringResource(R.string.incoming_call_ui_horizontal_slider) to
            PreferenceManager.INCOMING_CALL_UI_HORIZONTAL_SLIDER,
        stringResource(R.string.incoming_call_ui_horizontal_swipe) to
            PreferenceManager.INCOMING_CALL_UI_HORIZONTAL_SWIPE,
        stringResource(R.string.incoming_call_ui_vertical_swipe) to
            PreferenceManager.INCOMING_CALL_UI_VERTICAL_SWIPE,
    )
    val incomingAnswerPlacementChoices = listOf(
        stringResource(R.string.incoming_answer_placement_left_top) to
            PreferenceManager.INCOMING_ANSWER_PLACEMENT_LEFT_TOP,
        stringResource(R.string.incoming_answer_placement_right_bottom) to
            PreferenceManager.INCOMING_ANSWER_PLACEMENT_RIGHT_BOTTOM,
    )

    var defaultCallBg by remember { mutableStateOf<String?>(null) }
    var loadingBg by remember { mutableStateOf(true) }
    LaunchedEffect(settingsState) {
        loadingBg = true
        defaultCallBg = CallBackgroundStore.defaultModelAsync(context)
        loadingBg = false
    }
    var savingCallBg by remember { mutableStateOf(false) }
    val defaultCallBgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            retainDocumentReadAccess(context, uri)
            scope.launch {
                savingCallBg = true
                val ok = CallBackgroundStore.saveDefault(context, uri)
                savingCallBg = false
                if (ok) defaultCallBg = CallBackgroundStore.defaultModelAsync(context)
            }
        }
    }

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
                title = { Title(stringResource(if (advanced) R.string.advanced_settings else R.string.call_screen)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier
            .padding(
                top = padding.calculateTopPadding(),
                start = 0.dp,
                end = 0.dp,
                bottom = 0.dp
            )
            .fillMaxSize()
        ) {
            LazyColumn(state = rememberSettingsListState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                if (!advanced) {
                    item {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.incoming_call_ui))
                            DotSettingsCard {
                                DotSettingsChoiceRow(
                                title = stringResource(R.string.incoming_call_ui),
                                value = incomingCallUiChoices
                                    .firstOrNull { it.second == incomingCallUiMode }
                                    ?.first
                                    ?: stringResource(R.string.incoming_call_ui_default_swipe),
                                choices = incomingCallUiChoices,
                                selectedValue = incomingCallUiMode,
                                onValueChange = {
                                    incomingCallUiMode = it
                                    prefs.setInt(PreferenceManager.KEY_INCOMING_CALL_UI_MODE, it)
                                },
                            )
                                DotSettingsDivider()
                                DotSettingsStringChoiceRow(
                                title = stringResource(R.string.incoming_answer_placement),
                                value = incomingAnswerPlacementChoices
                                    .firstOrNull { it.second == incomingAnswerPlacement }
                                    ?.first
                                    ?: stringResource(R.string.incoming_answer_placement_left_top),
                                choices = incomingAnswerPlacementChoices,
                                selectedValue = incomingAnswerPlacement,
                                onValueChange = {
                                    incomingAnswerPlacement = it
                                    prefs.setString(
                                        PreferenceManager.KEY_INCOMING_ANSWER_PLACEMENT,
                                        it,
                                    )
                                },
                            )
                            }
                            Text(stringResource(R.string.incoming_answer_placement_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
                        }
                    }
                    item {
                        SettingsSectionLabel(stringResource(R.string.call_appearance))
                        CallPhotoAppearanceSettings(prefs, callAppearance, callAppearanceChoices) {
                            callAppearance = it
                            prefs.setString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, it)
                        }
                    }
                }
                if (!advanced) {
                    item {
                        DotSettingsCard {
                            SettingsPreferenceSwitch(prefs, PreferenceManager.KEY_LG_CALL_SCREEN, true, R.string.liquid_glass_call_screen)
                            AmbientDropletSettingsControls(prefs, settingsState)
                        }
                    }
                }

if (!advanced) {
                item {
                    RillExpressiveCard {
                        Column(
                            modifier = Modifier
                                .settingsSearchTarget(stringResource(R.string.settings_call_default_background))
                                .alpha(if (isPro) 1f else .45f)
                                .background(color = cardColor)
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Wallpaper,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        stringResource(R.string.settings_call_default_background),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (defaultCallBg != null) {
                                            stringResource(R.string.settings_call_default_background_set)
                                        } else {
                                            stringResource(R.string.settings_call_default_background_none)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .aspectRatio(2f)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                if (loadingBg) {
                                    CircularProgressIndicator()
                                } else if (defaultCallBg != null) {
                                    AsyncImage(
                                        model = defaultCallBg,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Wallpaper,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                if (savingCallBg) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isDark = isSystemInDarkTheme()
                                val borderColor =
                                    if (!isPro) {
                                        BorderStroke(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary.copy(
                                                alpha = if (themeMode == "dark" || (themeMode == "auto" && isDark)) 0.78f else 0.48f
                                            )
                                        )
                                    } else null
                                FilledTonalButton(
                                    onClick = {
                                        if (isPro) {
                                            defaultCallBgLauncher.launch(arrayOf("image/*"))
                                        } else {
                                            plans.requestUpgrade(io.github.bootika.dotdialer.core.license.PlanFeature.PREMIUM_APPEARANCE)
                                        }
                                    },
                                    border = borderColor,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (defaultCallBg != null) {
                                            stringResource(R.string.contact_call_background_change)
                                        } else {
                                            stringResource(R.string.contact_call_background_choose)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (defaultCallBg != null) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                CallBackgroundStore.clearDefault(context)
                                                defaultCallBg = null
                                            }
                                        }
                                    ) {
                                        Icon(
                                            ImageVector.vectorResource(id = R.drawable.ic_delete),
                                            contentDescription = stringResource(R.string.contact_call_background_remove),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.contact_call_background_remove),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
}


                if (!advanced) {
                    item { DotSettingsCard { CallPresentationSettings(prefs) } }
                    item { DotSettingsCard { DotSettingsNavigationRow(stringResource(R.string.advanced_settings),
                        onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.CallerUIScreenDestination(advanced = true)) }) } }
                }

                // ── Hang Up Button ────────────────────────────────────────
if (advanced) {
                item {
                    SettingsImmediateSection() {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.end_call_button))
                            RillExpressiveCard(modifier = Modifier.testTag("end-call-width-section")) {
                                Column(
                                    modifier = Modifier
                                        .background(color = cardColor)
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = color_call_end.copy(alpha = 0.15f),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    DotDialerHangupIcon,
                                                    contentDescription = null,
                                                    tint = color_call_end,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                stringResource(R.string.customize_width),
                                                modifier = Modifier.settingsSearchTarget(stringResource(R.string.customize_width)),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                stringResource(R.string.customize_width_subtitle),
                                                style = MaterialTheme.typography.bodySmall,
                                                lineHeight = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(24.dp))

                                    // Live preview
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BoxWithConstraints(contentAlignment = Alignment.Center) {
                                            val geometry = hangupButtonGeometry(
                                                availableWidthDp = maxWidth.value,
                                                savedSetting = hangupWidth,
                                            )
                                            Surface(
                                                shape = if (geometry.isCircle) {
                                                    CircleShape
                                                } else {
                                                    RoundedCornerShape((HANGUP_BUTTON_HEIGHT_DP / 2f).dp)
                                                },
                                                color = color_call_end,
                                                modifier = Modifier
                                                    .width(geometry.widthDp.dp)
                                                    .height(geometry.heightDp.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        DotDialerHangupIcon,
                                                        null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    // Slider
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Slider(
                                            value = hangupWidth,
                                            onValueChange = { hangupWidth = it },
                                            onValueChangeFinished = {
                                                prefs.setFloat(
                                                    PreferenceManager.KEY_HANGUP_WIDTH,
                                                    hangupWidth
                                                )
                                            },
                                            valueRange = 0.1f..1.0f,
                                            steps = 8,
                                            modifier = Modifier.weight(1f).testTag("end-call-width-slider"),
                                            colors = SliderDefaults.colors(
                                                thumbColor = color_call_end,
                                                activeTrackColor = color_call_end,
                                                inactiveTrackColor = color_call_end.copy(alpha = 0.3f),
                                                activeTickColor = Color.White.copy(alpha = 0.65f),
                                                inactiveTickColor = MaterialTheme.colorScheme.error,
                                            )
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "10%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${(hangupWidth * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.testTag("end-call-width-value").clickable(
                                                indication = ripple(
                                                    bounded = false,
                                                    radius = 24.dp,
                                                    color = MaterialTheme.colorScheme.error,
                                                ),
                                                interactionSource = null,
                                            ) {
                                                hangupWidth = PreferenceManager.DEFAULT_HANGUP_WIDTH
                                                prefs.setFloat(
                                                    PreferenceManager.KEY_HANGUP_WIDTH,
                                                    PreferenceManager.DEFAULT_HANGUP_WIDTH,
                                                )
                                            }
                                        )
                                        Text(
                                            "100%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
}


                if (advanced) {
                item {
                    DotSettingsCard { AdvancedCallVisualControls(prefs) }
                }
            }
            item { SettingsBottomPadding() }
            }

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                visible = showSnackbar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Snackbar(
                    modifier = Modifier.navigationBarsPadding().padding(24.dp),
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    action = {
                        TextButton(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            onClick = {
                                showSnackbar = false
                                navigator.navigate(DonateScreenDestination)
                            }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)
                        ) {
                            Text(stringResource(R.string.continue_support), color = MaterialTheme.colorScheme.accentText)
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.support_project_to_unlock),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
