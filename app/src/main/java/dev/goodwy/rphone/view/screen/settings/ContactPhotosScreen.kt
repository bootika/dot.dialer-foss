package dev.goodwy.rphone.view.screen.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Destination<RootGraph>
@Composable
fun ContactPhotosScreen(navigator: DestinationsNavigator, requestedAccount: String? = null, openReview: Boolean = false) {
    val context = LocalContext.current
    val processor = koinInject<WhatsAppAvatarSyncProcessor>()
    val reviews=koinInject<WhatsAppAvatarReviewStore>()
    val queued by reviews.items.collectAsState()
    val scan by processor.scanProgress.collectAsState()
    val applying by processor.applying.collectAsState()
    val prefs = koinInject<PreferenceManager>()
    val revision by prefs.settingsChanged.collectAsState()
    val profilesRevision by processor.profiles.changes.collectAsState()
    var resumed by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumed++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var accounts by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember(revision, profilesRevision) { mutableStateOf(processor.currentAccount()) }
    var notificationHandled by remember { mutableStateOf(false) }
    LaunchedEffect(resumed) {
        selected?.let { processor.profiles.preserveExistingGoogleConsent(it,
            prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED, false)) }
        accounts = runCatching { GoogleContactAccounts(context).load() }.getOrDefault(emptyList())
        if (!notificationHandled && requestedAccount != null && requestedAccount in accounts) {
            processor.selectAccount(requestedAccount); selected = requestedAccount
            notificationHandled = true
        }
    }
    var showReview by remember(selected) { mutableStateOf(false) }
    LaunchedEffect(selected,notificationHandled,openReview) { if(openReview && notificationHandled && selected==requestedAccount)showReview=true }
    val reviewUi=rememberContactPhotoReview(queued,reviews,processor,scan?.running==true || applying)
    val listState=rememberSettingsListState()
    LaunchedEffect(reviewUi.source) { if(showReview && listState.firstVisibleItemIndex>=2)listState.scrollToItem(2) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resumed++ }
    Scaffold(topBar = { DotSettingsTopBar(stringResource(R.string.contact_photos_title), navigator::navigateUp) }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).testTag("contactReviewList"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                DotSettingsCard {
                    if (!ContactOptimizationAccess.contactsGranted(context)) {
                        Text(stringResource(R.string.contact_photos_permission), Modifier.padding(16.dp))
                        Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) },
                            modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.allow)) }
                    }
                    if (accounts.isNotEmpty()) DotSettingsStringChoiceRow(stringResource(R.string.contact_photos_account),
                        selected ?: stringResource(R.string.high_res_call_photos_choose_account), accounts.map { it to it }, selected.orEmpty(),
                        onValueChange = { processor.selectAccount(it); selected = it })
                    else DotSettingsNavigationRow(stringResource(R.string.contact_photos_account),
                        value = stringResource(R.string.contact_photos_no_account), onClick = {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ADD_ACCOUNT)
                                .putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google")))
                        })
                }
            }
            item { key(selected) {
                selected?.let { WhatsAppAvatarSyncControl(it, resumed, onReviewRequested={showReview=true}) }
            } }
            if(showReview)contactPhotoReviewItems(reviewUi)
            if (dev.goodwy.rphone.BuildConfig.FLAVOR != "gplay" && selected != null) item {
                DotSettingsCard { Text(stringResource(R.string.contact_photos_google_foss), Modifier.padding(16.dp)) }
            }
            item { SettingsBottomPadding(80.dp) }
        }
    }
    if(showReview)ContactReviewDetails(reviewUi)
}
