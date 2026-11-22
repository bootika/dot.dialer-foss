package dev.goodwy.rphone.view.screen

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DefaultDialerScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RecentScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.SetupPermissions
import dev.goodwy.rphone.controller.util.getDefaultDialerIntent
import dev.goodwy.rphone.controller.util.isAlreadyDefaultDialer
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import dev.goodwy.rphone.view.screen.onboarding.OnboardingPhoneArt
import dev.goodwy.rphone.view.screen.onboarding.OnboardingWordmark
import dev.goodwy.rphone.view.screen.onboarding.onboardingPrimaryButtonColors
import org.koin.compose.koinInject

@Destination<RootGraph>()
@Composable
fun DefaultDialerScreen(navController: NavController, navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var started by rememberSaveable { mutableStateOf(false) }
    var roleDenied by rememberSaveable { mutableStateOf(false) }
    var rolePending by rememberSaveable { mutableStateOf(false) }
    var roleRetry by rememberSaveable { mutableStateOf(false) }
    var requested by rememberSaveable { mutableStateOf<String?>(null) }
    var denied by rememberSaveable { mutableStateOf<String?>(null) }
    var skipped by rememberSaveable { mutableStateOf(listOf<String>()) }
    var fullScreenAttempted by rememberSaveable { mutableStateOf(false) }
    var fullScreenPending by rememberSaveable { mutableStateOf(false) }
    var fullScreenSkipped by rememberSaveable { mutableStateOf(false) }
    var completed by rememberSaveable { mutableStateOf(false) }
    val api = Build.VERSION.SDK_INT
    val roleHeld = remember(revision) { isAlreadyDefaultDialer(context) }
    val next = remember(revision, skipped) {
        SetupPermissions.next(api, { SetupPermissions.granted(context, it) }, skipped.toSet())
    }
    fun fullScreenAllowed(): Boolean = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val needsFullScreen = roleHeld && next == null && Build.VERSION.SDK_INT >= 34 && !fullScreenSkipped &&
        SetupPermissions.granted(context, android.Manifest.permission.POST_NOTIFICATIONS) && !fullScreenAllowed()
    fun complete() {
        if (completed || !isAlreadyDefaultDialer(context) || !SetupPermissions.coreGranted(context)) return
        completed = true
        prefs.setBoolean(PreferenceManager.KEY_PERMISSION_SETUP_REVIEWED, true)
        val route = if (prefs.getInt(PreferenceManager.KEY_DEFAULT_BOTTOM_NAV, 0) == 0) RecentScreenDestination else ContactScreenDestination
        navigator.navigate(route) { popUpTo(DefaultDialerScreenDestination) { inclusive = true } }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val permission = requested
        requested = null
        denied = permission?.takeUnless { SetupPermissions.granted(context, it) }
        revision++
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        rolePending = false
        started = isAlreadyDefaultDialer(context)
        roleDenied = !started
        revision++
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        revision++
        // AppInfo's restricted-setting confirmation is not itself a role or runtime grant.
        if (isAlreadyDefaultDialer(context)) { started = true; denied = null }
        else roleRetry = true
    }
    val fullScreenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        fullScreenPending = false
        revision++
    }
    fun requestRole() {
        if (rolePending) return
        rolePending = true
        roleLauncher.launch(getDefaultDialerIntent(context))
    }
    fun openAppInfo() {
        settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
    fun requestFullScreen() {
        if (Build.VERSION.SDK_INT < 34) return
        fullScreenAttempted = true
        fullScreenPending = true
        try {
            fullScreenLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.fromParts("package", context.packageName, null)))
        } catch (error: android.content.ActivityNotFoundException) {
            fullScreenPending = false
            android.util.Log.w("SetupPermissions", "fullscreen-settings: ${error.javaClass.simpleName}")
        }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { if (roleHeld) started = true }
    LaunchedEffect(started, revision, skipped, fullScreenSkipped, roleRetry) {
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || completed) return@LaunchedEffect
        if (roleRetry) {
            roleRetry = false
            if (!isAlreadyDefaultDialer(context)) { requestRole(); return@LaunchedEffect }
            started = true
        }
        if (!started) return@LaunchedEffect
        if (!isAlreadyDefaultDialer(context)) { started = false; roleDenied = true; return@LaunchedEffect }
        if (requested != null || fullScreenPending) return@LaunchedEffect
        val remaining = SetupPermissions.next(api, { SetupPermissions.granted(context, it) }, skipped.toSet())
        if (remaining != null) {
            if (denied == remaining.name) return@LaunchedEffect
            denied = null
            requested = remaining.name
            permissionLauncher.launch(remaining.name)
        } else if (needsFullScreen) {
            if (!fullScreenAttempted) requestFullScreen()
        } else complete()
    }
    if (!started || (next == null && !needsFullScreen)) {
        DefaultPhoneRoleContent(rolePending, roleDenied, ::requestRole, ::openAppInfo)
        return
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(DotDialerHandsetIcon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(24.dp))
            val title = if (next != null) context.packageManager.getPermissionInfo(next.name, 0).loadLabel(context.packageManager).toString()
                else stringResource(R.string.fullscreen_calls)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(next?.reason ?: R.string.setup_fullscreen), textAlign = TextAlign.Center)
            if (denied != null || (needsFullScreen && fullScreenAttempted && !fullScreenPending)) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = { if (next != null) { denied = null; revision++ } else requestFullScreen() }) {
                    Text(stringResource(R.string.setup_retry))
                }
                TextButton(onClick = ::openAppInfo) { Text(stringResource(R.string.open_app_settings)) }
                if (next?.required == false || needsFullScreen) TextButton(onClick = {
                    if (next != null) skipped = skipped + next.name else fullScreenSkipped = true
                    denied = null; revision++
                }) { Text(stringResource(R.string.setup_continue_limited)) }
            }
        }
    }
}

@Composable
internal fun DefaultPhoneRoleContent(
    pending: Boolean,
    denied: Boolean,
    onRequestRole: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxHeight < 580.dp
            Column(Modifier.widthIn(max = 460.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp, Alignment.CenterVertically),
                ) {
                    OnboardingPhoneArt(keypad = false, size = if (compact) 70.dp else 116.dp, roleCheck = true)
                    OnboardingWordmark()
                    Text(stringResource(R.string.onboarding_role_title), Modifier.semantics { heading() },
                        style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center)
                    Text(stringResource(R.string.onboarding_role_summary), style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                    if (denied) RestrictedSetupHelp(onOpenSettings = onOpenSettings)
                }
                Button(
                    onClick = onRequestRole,
                    enabled = !pending,
                    shape = CircleShape,
                    colors = onboardingPrimaryButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).heightIn(min = 56.dp)
                        .testTag("default-role-continue"),
                ) {
                    Text(stringResource(R.string.onboarding_role_action), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
internal fun RestrictedSetupHelp(onOpenSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.default_dialer_restricted_settings_help), textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(
                containerColor = Color.White, contentColor = Color(0xFF17221D))) {
                Text(stringResource(R.string.open_app_settings))
            }
        }
    }
}
