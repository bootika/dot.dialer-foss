package dev.goodwy.rphone

import dev.goodwy.rphone.view.components.DotDialerHandsetIcon

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.CallActivity
import dev.goodwy.rphone.view.components.BottomBar
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.layerBackdrop
import dev.goodwy.rphone.view.theme.DotDialerAppTheme
import dev.goodwy.rphone.view.theme.TabTransitionStyle
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.view.Surface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import dev.goodwy.rphone.controller.util.isAlreadyDefaultDialer
import dev.goodwy.rphone.view.screen.onboarding.MorphingOnboardingScreen
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import com.ramcosta.composedestinations.generated.destinations.AboutAppScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AvatarsPreferenceScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BiometricScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BlockedNumbersScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BlurEffectsElementsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CallAccountsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CallSettingScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CallerUIScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactManagementScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactMergeDuplicatesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactUnmergeDuplicatesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactVisibilityScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContributorsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DefaultDialerScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DonateScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FavoritesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InterfaceScreenDestination
import com.ramcosta.composedestinations.generated.destinations.LiquidGlassElementsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.HomeGesturesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.NotesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.QuickResponsesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RecentScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RecentsSearchScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SoundVibrationScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SpamScreenDestination
import dev.goodwy.rphone.controller.CallViewModel
import dev.goodwy.rphone.controller.MainViewModel
import dev.goodwy.rphone.controller.NavigationTarget
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.util.currentDisplayRotation
import dev.goodwy.rphone.controller.lock.AppLockManager
import dev.goodwy.rphone.view.components.AuthenticationSystemPrompt
import dev.goodwy.rphone.view.components.TabSpec
import dev.goodwy.rphone.view.components.LocalRillNeutralSettingsIcons
import dev.goodwy.rphone.view.components.parseTabOrder
import dev.goodwy.rphone.view.components.performAppHaptic
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.isLandscapeMode
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.context.GlobalContext
import io.github.bootika.dotdialer.core.diagnostics.ActivityStage
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticEvent
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics

class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_RETURN_TO_CALL_ON_BACK = "return_to_call_on_back"
    }

    private var defaultDialerRevision by mutableStateOf(0)
    private var intentState by mutableStateOf<Intent?>(null)
    private var invitationLink by mutableStateOf<String?>(null)
    private var returnToCallOnBack by mutableStateOf(false)
    private lateinit var prefs: PreferenceManager
    private val callViewModel: CallViewModel by viewModel()
    private val mainViewModel: MainViewModel by viewModel()
    private var isAppLocked by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppDiagnostics.record(DiagnosticEvent.ActivityLifecycle(ActivityStage.CREATED))
        intentState = intent
        returnToCallOnBack = intent.getBooleanExtra(EXTRA_RETURN_TO_CALL_ON_BACK, false)
        // enableEdgeToEdge() triggers Adreno GPU driver SIGSEGV on first RenderThread draw.
        // Edge-to-edge is set via theme XML instead (windowDrawsSystemBarBackgrounds etc.).
        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs = GlobalContext.get().get<PreferenceManager>()

        if (AppLockManager.isLocked(prefs)) {
            isAppLocked = true
        }

        setContent {
            DotDialerAppTheme {
                dev.goodwy.rphone.view.screen.settings.PlanUpgradeHost()
                val testAccess = remember { dev.goodwy.rphone.controller.license.TestAccessManager.get(this@MainActivity) }
                val accessState by testAccess.state.collectAsStateWithLifecycle()
                if (accessState != dev.goodwy.rphone.controller.license.TestAccessState.ACTIVE) {
                    dev.goodwy.rphone.view.components.TestAccessScreen(accessState, testAccess::retry)
                    return@DotDialerAppTheme
                }
                val defaultRoleHeld = remember(defaultDialerRevision) { isAlreadyDefaultDialer(this@MainActivity) }
                val context = LocalContext.current
                val navController = rememberNavController()
                val settingsVer by prefs.settingsChanged.collectAsStateWithLifecycle()

                val favouritesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, false)
                val contactsEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)
                val notesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, false)
                val searchEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_SEARCH, false)
                val settingsEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_SETTINGS, true)
                // Dot Dialer has one primary surface. Legacy destinations remain available for
                // auxiliary/deep-link flows, but they are no longer exposed as root tabs.
                val showPrimaryNavigation = false
//                val dialpadEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
                val defaultTab = prefs.getString(PreferenceManager.KEY_DEFAULT_TAB, "calls") ?: "calls"

//                val defBar = prefs.getInt(PreferenceManager.KEY_DEFAULT_BOTTOM_NAV, 0)
//                val transitionStyle = prefs.getInt(PreferenceManager.KEY_TRANSITION_STYLE, 0)
                val onboardingShown = remember { prefs.getBoolean(PreferenceManager.KEY_ONBOARDING_SHOWN, false) }
                var showOnboarding by remember { mutableStateOf(!onboardingShown) }
                var planSettingsRoute by remember { mutableStateOf<String?>(null) }
                val appLockEnabled = remember(settingsVer) { prefs.isAppLockEnabled() }
                LaunchedEffect(appLockEnabled) {
                    if (appLockEnabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }

                // ── Biometric overlay (above blur, inside Box) ─────────
                if (isAppLocked && appLockEnabled) {
                    val biometricType = remember(settingsVer) { prefs.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: "" }
                    if (biometricType.isNotEmpty()) {
                        when (biometricType) {
                            "system" -> {
                                val activity = this@MainActivity
                                AuthenticationSystemPrompt(
                                    activity = activity,
                                    title = stringResource(R.string.verify_your_identity_to_continue),
                                    onSuccess = {
                                        AppLockManager.unlock()
                                        isAppLocked = false
                                    },
                                    onError = { _, _ ->
                                        activity.finish()
                                    }
                                )
                            }
                            "pin" -> {
                                dev.goodwy.rphone.view.screen.settings.PinSetupDialog(
                                    title = stringResource(R.string.enter_pin), isVerify = true,
                                    verifySecret = { prefs.verifyAuthentication("pin", it) },
                                    onConfirm = { AppLockManager.unlock(); isAppLocked = false },
                                    onDismiss = { finish() }
                                )
                            }
                            "password" -> {
                                dev.goodwy.rphone.view.screen.settings.PasswordSetupDialog(
                                    title = stringResource(R.string.enter_password), isVerify = true,
                                    verifySecret = { prefs.verifyAuthentication("password", it) },
                                    onConfirm = { AppLockManager.unlock(); isAppLocked = false },
                                    onDismiss = { finish() }
                                )
                            }
                        }
                    }
                }

                if (isAppLocked && appLockEnabled) {
                    // Locked content is not composed: no touch, accessibility or deep-link surface.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
                } else if (showOnboarding) {
                    MorphingOnboardingScreen(
                        onPlanSettings = { tier -> planSettingsRoute = if(tier == io.github.bootika.dotdialer.core.license.AppPlan.PLUS)
                            com.ramcosta.composedestinations.generated.destinations.ContactPhotosScreenDestination().route else InterfaceScreenDestination().route },
                        onFinished = {
                            prefs.setBoolean(PreferenceManager.KEY_ONBOARDING_SHOWN, true)
                            prefs.setBoolean(PreferenceManager.KEY_HOME_UI_TOUR_SHOWN, false)
                            showOnboarding = false
                        }
                    )
                } else {
                    val lastOpenedTab = remember {
                        prefs.getString(PreferenceManager.KEY_LAST_OPENED_TAB, null)
                    }
                    // Compute start destination from prefs — done once so no flash
                    val startDestination = remember {
                        when {
                            dev.goodwy.rphone.controller.util.SetupPermissions.needsSetup(this@MainActivity, prefs) -> DefaultDialerScreenDestination
                            else -> RecentScreenDestination
                        }
                    }

                    // ── Biometric blur + lock ─────────────────────────────────
                    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    val blurRadius by animateDpAsState(
                        targetValue = if (isAppLocked) 26.dp else 0.dp,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "biometricBlur"
                    )
                    val scrimColor by animateColorAsState(
                        targetValue = if (isAppLocked) Color.Black.copy(alpha = 0.95f) else Color.Transparent,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "scrimColor"
                    )

                    // ── Ongoing Call Banner + Main nav host ───────────────────────
                    val callSession by callViewModel.currentCallSession.collectAsStateWithLifecycle()
                    val displayedBannerCall = callSession?.call
                    val hasOngoingCall =
                        callSession?.state?.let { dev.goodwy.rphone.controller.isOngoingBannerState(it) } == true
                    val callHandoff by dev.goodwy.rphone.controller.CallUiHandoff.controller.state.collectAsStateWithLifecycle()
                    val callUiVisible by callViewModel.isCallUiVisible.collectAsStateWithLifecycle()
                    val suppressCallBanner = callHandoff != null || callUiVisible

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Main content — blurred when locked
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isBlurSupported && blurRadius > 0.dp)
                                        Modifier.blur(
                                            blurRadius,
                                            edgeTreatment = BlurredEdgeTreatment.Unbounded
                                        )
                                    else
                                        Modifier
                                )
                        ) {
                            // ── Ongoing Call Banner (above all content) ────────────
                            AnimatedVisibility(
                                visible = dev.goodwy.rphone.controller.showHomeCallBanner(hasOngoingCall, callHandoff != null, callUiVisible),
                                enter = slideInVertically { -it } + fadeIn(),
                                exit = if (suppressCallBanner) ExitTransition.None else slideOutVertically { -it } + fadeOut()
                            ) {
                                dev.goodwy.rphone.view.components.OngoingCallBanner(
                                    onExpand = {
                                        returnToCallOnBack = false
                                        startActivity(Intent(this@MainActivity, CallActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                        })
                                    },
                                    onEnd = {
                                        displayedBannerCall?.let(callViewModel::endBannerCall)
                                    },
                                )
                            }

                            // ── Main nav host + adaptive nav (bottom bar / rail) ───
                            val configuration = LocalConfiguration.current
                            val isLandscape =
                                configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            isLandscapeMode = isLandscape
                            val navBackStack by navController.currentBackStackEntryAsState()
                            val currentDest = navBackStack?.destination
                            BackHandler(
                                enabled = returnToCallOnBack &&
                                    hasOngoingCall &&
                                    currentDest?.route == RecentScreenDestination.route,
                            ) {
                                returnToCallOnBack = false
                                startActivity(
                                    Intent(this@MainActivity, CallActivity::class.java).apply {
                                        addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                                        )
                                    },
                                )
                            }
                            val neutralSettingsRoutes = remember {
                                setOf(
                                    SettingsScreenDestination.route,
                                    DonateScreenDestination.route,
                                    AboutAppScreenDestination.route,
                                    BiometricScreenDestination.route,
                                    BlockedNumbersScreenDestination.route,
                                    BlurEffectsElementsScreenDestination.route,
                                    CallAccountsScreenDestination.route,
                                    CallerUIScreenDestination.route,
                                    CallSettingScreenDestination.route,
                                    ContributorsScreenDestination.route,
                                    InterfaceScreenDestination.route,
                                    LiquidGlassElementsScreenDestination.route,
                                    SoundVibrationScreenDestination.route,
                                    SpamScreenDestination.route,
                                    ContactManagementScreenDestination.route,
                                    ContactVisibilityScreenDestination.route,
                                    ContactMergeDuplicatesScreenDestination.route,
                                    ContactUnmergeDuplicatesScreenDestination.route,
                                    AvatarsPreferenceScreenDestination.route,
                                    HomeGesturesScreenDestination.route,
                                    RecentsSearchScreenDestination.route,
                                    QuickResponsesScreenDestination.route
                                )
                            }
                            val useNeutralSettingsIcons = currentDest?.hierarchy?.any {
                                it.route in neutralSettingsRoutes
                            } == true
                            val prefs2 = remember { GlobalContext.get().get<PreferenceManager>() }
                            val showFavoritesRail = prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, false)
                            val showCallsRail = prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_CALLS,     true)
                            val showContactsRail = prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)
                            val showDialpadRail = prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
                            val showNotesRail = prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, false)
                            val tabOrder = parseTabOrder(prefs2.getString(PreferenceManager.KEY_TAB_ORDER, null))

                            val isFavoritesSelected = currentDest?.hierarchy?.any { it.route == FavoritesScreenDestination.route }  == true
                            val isRecentsSelected   = currentDest?.hierarchy?.any { it.route == RecentScreenDestination.route }     == true
                            val isContactsSelected  = currentDest?.hierarchy?.any { it.route == ContactScreenDestination.route }    == true
                            val isDialpadSelected   = currentDest?.hierarchy?.any { it.route == DialPadScreenDestination.route }    == true
                            val isNotesSelected     = currentDest?.hierarchy?.any { it.route == NotesScreenDestination.route }      == true

                            fun saveCurrentTab(route: String) {
                                when {
                                    route.contains(FavoritesScreenDestination.route) ->
                                        prefs.setString(
                                            PreferenceManager.KEY_LAST_OPENED_TAB,
                                            FavoritesScreenDestination.route
                                        )

                                    route.contains(ContactScreenDestination.route) ->
                                        prefs.setString(
                                            PreferenceManager.KEY_LAST_OPENED_TAB,
                                            ContactScreenDestination.route
                                        )

                                    route.contains(NotesScreenDestination.route) ->
                                        prefs.setString(
                                            PreferenceManager.KEY_LAST_OPENED_TAB,
                                            NotesScreenDestination.route
                                        )

                                    route.contains(DialPadScreenDestination.route) ->
                                        prefs.setString(
                                            PreferenceManager.KEY_LAST_OPENED_TAB,
                                            DialPadScreenDestination.route
                                        )

                                    else ->
                                        prefs.setString(
                                            PreferenceManager.KEY_LAST_OPENED_TAB,
                                            RecentScreenDestination.route
                                        )
                                }
                            }

                            LaunchedEffect(currentDest) {
                                currentDest?.route?.let { route ->
                                    saveCurrentTab(route)
                                }
                            }

                            fun navTo(route: String) {
                                saveCurrentTab(route)
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }

                            fun doHaptic() {
                                if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                    performAppHaptic(
                                        context,
                                        prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                                        prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
                                    )
                                }
                            }

                            if (showPrimaryNavigation && isLandscape && defaultRoleHeld) {
                                val ctx = LocalContext.current

                                val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
                                val rotation = ctx.currentDisplayRotation()
                                val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270
                                val isRotation270 = rotation == if (isLtr) Surface.ROTATION_270 else Surface.ROTATION_90
//                        val railPaddingStart = if (isRotation270) 10.dp else 0.dp
//                        val railPaddingEnd   = if (isRotation90)  10.dp else 0.dp



                                val houseIcon = ImageVector.vectorResource(id = R.drawable.ic_house_fill)
                                val labelFavorites = stringResource(R.string.favorites)
                                val labelRecents =
                                    if (!showFavoritesRail && !showContactsRail) stringResource(R.string.home_tab)
                                    else stringResource(R.string.recents)
                                val labelContacts = stringResource(R.string.contacts)
                                val labelKeypad = stringResource(R.string.keypad)
                                val labelNotes = stringResource(R.string.notes)
                                val orderedTabs: List<TabSpec> = remember(
                                    tabOrder, showFavoritesRail, showCallsRail, showContactsRail, showDialpadRail, showNotesRail,
                                    isFavoritesSelected, isRecentsSelected, isContactsSelected, isDialpadSelected, isNotesSelected
                                ) {
                                    tabOrder.mapNotNull { key ->
                                        when (key) {
                                            "favorites" -> if (showFavoritesRail) TabSpec(
                                                key             = key,
                                                route           = FavoritesScreenDestination.route,
                                                selected        = isFavoritesSelected,
                                                selectedIcon    = Icons.Filled.Assistant,
                                                unselectedIcon  = Icons.Outlined.Assistant,
                                                label           = labelFavorites,
                                                onClick         = { doHaptic(); navTo(FavoritesScreenDestination.route) }
                                            ) else null
                                            "calls" -> if (showCallsRail) TabSpec(
                                                key             = key,
                                                route           = RecentScreenDestination.route,
                                                selected        = isRecentsSelected,
                                                selectedIcon    = if (!showFavoritesRail && !showContactsRail) houseIcon else Icons.Rounded.AccessTimeFilled,
                                                unselectedIcon  = if (!showFavoritesRail && !showContactsRail) houseIcon else Icons.Rounded.AccessTime,
                                                label           = labelRecents,
                                                onClick         = { doHaptic(); navTo(RecentScreenDestination.route) }
                                            ) else null
                                            "contacts" -> if (showContactsRail) TabSpec(
                                                key             = key,
                                                route           = ContactScreenDestination.route,
                                                selected        = isContactsSelected,
                                                selectedIcon    = Icons.Filled.AccountCircle,
                                                unselectedIcon  = Icons.Outlined.AccountCircle,
                                                label           = labelContacts,
                                                onClick         = { doHaptic(); navTo(ContactScreenDestination.route) }
                                            ) else null
                                            "dialpad" -> if (showDialpadRail) TabSpec(
                                                key = key,
                                                route = DialPadScreenDestination(initialNumber = "").route,
                                                selected       = isDialpadSelected,
                                                selectedIcon   = Icons.Filled.Dialpad,
                                                unselectedIcon = Icons.Filled.Dialpad,
                                                label          = labelKeypad,
                                                onClick        = { doHaptic(); navTo(DialPadScreenDestination(initialNumber = "").route) }
                                            ) else null
                                            "notes" -> if (showNotesRail) TabSpec(
                                                key            = key,
                                                route          = NotesScreenDestination().route,
                                                selected       = isNotesSelected,
                                                selectedIcon   = Icons.AutoMirrored.Filled.StickyNote2,
                                                unselectedIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                                                label          = labelNotes,
                                                onClick        = { doHaptic(); navTo(NotesScreenDestination().route) }
                                            ) else null
                                            else -> null
                                        }
                                    }
                                }

                                val liquidGlassBackdropLandscape = rememberLayerBackdrop()
                                CompositionLocalProvider(LocalLiquidGlassBackdrop provides liquidGlassBackdropLandscape) {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        Surface(
                                            color = bottomBarColor,
                                            modifier = Modifier.fillMaxHeight()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .then(
                                                        if (isRotation90) {
                                                            Modifier.windowInsetsPadding(
                                                                WindowInsets.displayCutout/*.union(WindowInsets.systemBars)*/
                                                            )
                                                        } else {
                                                            Modifier.windowInsetsPadding(WindowInsets.systemBars)
                                                        }
                                                    )
                                                    .width(82.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.Center,
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    orderedTabs.forEach { tab ->
                                                        RailItem(
                                                            selected = tab.selected,
                                                            icon = { sel ->
                                                                Icon(
                                                                    if (sel) tab.selectedIcon else tab.unselectedIcon,
                                                                    "Calls",
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            },
                                                            onClick = tab.onClick
                                                        )
                                                    }

                                                    Spacer(Modifier.height(8.dp))
                                                    HorizontalDivider(
                                                        modifier = Modifier.width(32.dp),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.6f
                                                        )
                                                    )
                                                    Spacer(Modifier.height(8.dp))

                                                    RailItem(
                                                        selected = currentDest?.hierarchy?.any {
                                                            it.route?.contains(
                                                                "search",
                                                                ignoreCase = true
                                                            ) == true
                                                        } == true,
                                                        icon = { _ ->
                                                            Icon(
                                                                Icons.Default.Search,
                                                                "Search",
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        },
                                                        onClick = { navTo(com.ramcosta.composedestinations.generated.destinations.SearchScreenDestination.route) }
                                                    )
                                                    dev.goodwy.rphone.view.components.VoiceSearchButton(onResult = { navTo(com.ramcosta.composedestinations.generated.destinations.SearchScreenDestination(initialQuery = it).route) }, prefs = prefs)
                                                    RailItem(
                                                        selected = currentDest?.hierarchy?.any {
//                                                            it.route?.contains("settings", ignoreCase = true) == true ||
                                                                    it.route == SettingsScreenDestination.route || it.route == DonateScreenDestination.route ||
                                                                    it.route == AboutAppScreenDestination.route ||
                                                                    it.route == BiometricScreenDestination.route || it.route == BlockedNumbersScreenDestination.route ||
                                                                    it.route == BlurEffectsElementsScreenDestination.route || it.route == CallAccountsScreenDestination.route ||
                                                                    it.route == CallerUIScreenDestination.route || it.route == CallSettingScreenDestination.route ||
                                                                    it.route == ContributorsScreenDestination.route || it.route == InterfaceScreenDestination.route ||
                                                                    it.route == LiquidGlassElementsScreenDestination.route ||
                                                                    it.route == SoundVibrationScreenDestination.route || it.route == SpamScreenDestination.route ||
                                                                    it.route == ContactManagementScreenDestination.route ||
                                                                    it.route == ContactVisibilityScreenDestination.route || it.route == ContactMergeDuplicatesScreenDestination.route ||
                                                                    it.route == ContactUnmergeDuplicatesScreenDestination.route || it.route == AvatarsPreferenceScreenDestination.route
                                                        } == true,
                                                        icon = { sel ->
                                                            Icon(
                                                                if (sel) Icons.Default.Settings else ImageVector.vectorResource(id = R.drawable.ic_settings),
                                                                stringResource(R.string.settings),
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        },
                                                        onClick = { navTo(SettingsScreenDestination.route) }
                                                    )
                                                }
                                            }
                                        }
                                        // ── Main content fills the rest, edge-to-edge ──────────────────────
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(MaterialTheme.colorScheme.background)
                                                .fillMaxHeight().then(
                                                    if (isRotation270) {
                                                        Modifier.windowInsetsPadding(WindowInsets.displayCutout)
                                                    } else {
                                                        Modifier.windowInsetsPadding(WindowInsets.systemBars)
                                                    }
                                                )
                                        ) {
                                            CompositionLocalProvider(
                                                LocalRillNeutralSettingsIcons provides useNeutralSettingsIcons
                                            ) {
                                                DestinationsNavHost(
                                                    navGraph = NavGraphs.root,
                                                    navController = navController,
                                                    start = startDestination,
                                                    defaultTransitions = TabTransitionStyle
                                                )
                                            }
                                        }
                                    }
                                } // end CompositionLocalProvider landscape
                            } else {
                                val liquidGlassBackdrop = rememberLayerBackdrop()
                                val showBottomBar =
                                    showPrimaryNavigation &&
                                        (showFavoritesRail || showContactsRail || showDialpadRail || notesEnabled || searchEnabled || settingsEnabled)
                                CompositionLocalProvider(LocalLiquidGlassBackdrop provides liquidGlassBackdrop) {
                                    Scaffold(
                                        bottomBar = { if (showBottomBar) BottomBar(navController) },
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentWindowInsets = WindowInsets(0)
                                    ) { scaffoldPadding ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(scaffoldPadding)
                                                .layerBackdrop(liquidGlassBackdrop)
                                                .then(
                                                    if (hasOngoingCall)
                                                        Modifier.consumeWindowInsets(WindowInsets.statusBars)
                                                    else
                                                        Modifier
                                                )
                                        ) {
                                            CompositionLocalProvider(
                                                LocalRillNeutralSettingsIcons provides useNeutralSettingsIcons
                                            ) {
                                                DestinationsNavHost(
                                                    navGraph = NavGraphs.root,
                                                    navController = navController,
                                                    start = startDestination,
                                                    defaultTransitions = TabTransitionStyle
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            LaunchedEffect(defaultDialerRevision) {
                                if ((!defaultRoleHeld || !dev.goodwy.rphone.controller.util.SetupPermissions.coreGranted(this@MainActivity)) &&
                                    navController.currentBackStackEntry?.destination?.route != DefaultDialerScreenDestination.route) {
                                    navController.navigate(DefaultDialerScreenDestination.route) {
                                        popUpTo(ContactScreenDestination.route) {
                                            inclusive = true
                                        }
                                    }
                                }
                            }

                            val intentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                            LaunchedEffect(intentState, intentRoute, defaultDialerRevision) {
                                if (intentRoute != null && intentRoute != DefaultDialerScreenDestination.route &&
                                    defaultRoleHeld && dev.goodwy.rphone.controller.util.SetupPermissions.coreGranted(this@MainActivity)) {
                                    handleIntent(intentState, navController)
                                }
                            }
                        }
                        if (!isBlurSupported) Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(scrimColor)
                        )
                        LaunchedEffect(planSettingsRoute) { planSettingsRoute?.let { navController.navigate(it); planSettingsRoute=null } }
                        invitationLink?.takeUnless {hasOngoingCall}?.let {link -> dev.goodwy.rphone.view.screen.settings.PlanInvitationDialog(link) {invitationLink=null} }
                        if(!hasOngoingCall && invitationLink==null && planSettingsRoute==null && defaultRoleHeld) {
                            dev.goodwy.rphone.view.screen.settings.PlanGuideHost { tier ->
                                navController.navigate(if(tier == io.github.bootika.dotdialer.core.license.AppPlan.PLUS)
                                    com.ramcosta.composedestinations.generated.destinations.ContactPhotosScreenDestination().route else InterfaceScreenDestination().route)
                            }
                        }
                    } // end blurred Column
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        defaultDialerRevision++
        AppDiagnostics.record(DiagnosticEvent.ActivityLifecycle(ActivityStage.RESUMED))
        AppLockManager.onAppForegrounded(prefs)
        if (AppLockManager.isLocked(prefs)) {
            isAppLocked = true
        }
    }

    override fun onStop() {
        AppDiagnostics.record(
            DiagnosticEvent.ActivityLifecycle(
                stage = ActivityStage.STOPPED,
                changingConfiguration = isChangingConfigurations,
            )
        )
        super.onStop()
        if (!isChangingConfigurations) AppLockManager.onAppBackgrounded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppDiagnostics.record(DiagnosticEvent.ActivityLifecycle(ActivityStage.NEW_INTENT))
        setIntent(intent)
        intentState = intent
        returnToCallOnBack = intent.getBooleanExtra(EXTRA_RETURN_TO_CALL_ON_BACK, false)

        val purchaseHelper: PurchaseHelper = GlobalContext.get().get()
        purchaseHelper.handleNewIntent(intent, this)
    }

    override fun onDestroy() {
        AppDiagnostics.record(
            DiagnosticEvent.ActivityLifecycle(
                stage = ActivityStage.DESTROYED,
                changingConfiguration = isChangingConfigurations,
            )
        )
        super.onDestroy()
    }

    private suspend fun handleIntent(intent: Intent?, navController: androidx.navigation.NavController) {
        intent ?: return
        intent.dataString?.let {link ->
            if(intent.action==Intent.ACTION_VIEW && dev.goodwy.rphone.controller.license.PlanInvitationLink.token(link,packageName)!=null) {
                invitationLink=link;intentState=null;return
            }
        }
        val target = mainViewModel.resolveNavigationTarget(intent, this)
        if (intent.action == Intent.ACTION_CALL && target == null && isAlreadyDefaultDialer(this)) {
            // MainActivity only proxied this request. Leaving it in the history would make the
            // call end on Dot instead of the app from which the user initiated the call.
            intentState = null
            finish()
            return
        }
        target ?: return
        intentState = null

        when (target) {
            is NavigationTarget.OngoingCall -> startActivity(Intent(this, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            })
            is NavigationTarget.Search -> navController.navigate(com.ramcosta.composedestinations.generated.destinations.SearchScreenDestination(initialQuery = target.query).route)
//            is NavigationTarget.Recents -> {
//                navController.navigate(RecentScreenDestination.route) {
//                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
//                    launchSingleTop = true
//                }
//            }
            is NavigationTarget.Recents -> {
                navController.navigate(RecentScreenDestination.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                        inclusive = false // Don't remove the home screen
                    }
                    launchSingleTop = true
                    restoreState = true

                    // Disable the animation for instant opening from a notification
                    anim {
                        enter = 0
                        exit = 0
                        popEnter = 0
                        popExit = 0
                    }
                }
            }
            is NavigationTarget.Dialpad -> {
                navController.navigate(DialPadScreenDestination(initialNumber = target.number).route)
            }
            is NavigationTarget.ContactUnavailable -> {
                android.widget.Toast.makeText(this, R.string.no_contacts_found, android.widget.Toast.LENGTH_SHORT).show()
            }
            is NavigationTarget.ContactDetails -> {
                navController.navigate(ContactDetailsScreenDestination(contactId = target.contactId).route)
            }
            is NavigationTarget.ContactEdit -> {
                navController.navigate(ContactEditScreenDestination(
                    contactId = target.contactId,
                    initialName = target.initialName,
                    initialPhone = target.initialPhone
                ).route) {
                    launchSingleTop = true
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                }
            }
            is NavigationTarget.ContactPhotos -> {
                navController.navigate(dev.goodwy.rphone.controller.contactPhotoReviewRoute(target.account)) { launchSingleTop = true }
            }
            is NavigationTarget.ContactManagement -> {
                navController.navigate(dev.goodwy.rphone.controller.contactManagementIntentRoute()) {
                    launchSingleTop = true
                }
            }
        }
    }
}
