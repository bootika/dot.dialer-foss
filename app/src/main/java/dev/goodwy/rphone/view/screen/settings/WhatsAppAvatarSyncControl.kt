package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

@Composable
internal fun WhatsAppAvatarSyncControl(account: String, resumed: Int = 0, initiallyReview: Boolean = false, onReviewRequested: (() -> Unit)? = null) {
    val disclosureDensity=LocalDensity.current
    val context = LocalContext.current
    val reviews = koinInject<WhatsAppAvatarReviewStore>()
    val processor = koinInject<WhatsAppAvatarSyncProcessor>()
    val applying by processor.applying.collectAsState()
    val revision by processor.profiles.changes.collectAsState()
    val allStoredItems by reviews.allItems.collectAsState()
    val storedItems by reviews.items.collectAsState()
    val queued = storedItems.contactPhotoProposals()
    val profileStorageFailed by processor.profiles.storageFailed.collectAsState()
    val reviewStorageFailed by reviews.storageFailed.collectAsState()
    val storageFailed=profileStorageFailed || reviewStorageFailed
    val prefs = koinInject<PreferenceManager>()
    val prefsRevision by prefs.settingsChanged.collectAsState()
    val googleReady = remember(account, revision, prefsRevision) { processor.profiles.googleAllowed(account) &&
        prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED, false) &&
        prefs.getString(GoogleContactPhotoPreferences.ACCOUNT, null) == account }
    val scope = rememberCoroutineScope()
    val settings = remember(account, revision) { processor.profiles.settings(account) }
    val access = remember(resumed, revision) { ContactOptimizationAccess.granted(context) }
    val readiness = contactPhotoReadiness(ContactOptimizationAccess.supported(context), settings.enabled,
        settings.disclosed, access, ContactOptimizationAccess.contactsGranted(context))
    var disclosure by remember(account) { mutableStateOf<String?>(null) }
    var pendingSource by remember(account) { mutableStateOf<String?>(null) }
    var scanJob by remember(account) { mutableStateOf<Job?>(null) }
    var resetting by remember(account) { mutableStateOf(false) }
    var confirmReset by remember(account) { mutableStateOf(false) }
    var resetFailed by remember(account) { mutableStateOf(false) }
    val scanState by processor.scanProgress.collectAsState()
    val accountProgress = scanState?.takeIf { it.account == account }
    val activeSource = pendingSource ?: accountProgress?.takeIf { it.running }?.source
    val scanning = resetting || activeSource != null
    var showReview by remember(account) { mutableStateOf(initiallyReview) }
    LaunchedEffect(account, resumed) {
        reviews.selectAccount(account); processor.reconcileAppliedPhotos(); processor.updateReviewNotification()
    }
    fun scan(source: String) {
        pendingSource = source
        scanJob = scope.launch { try { if(source=="whatsapp")processor.scanWhatsApp() else processor.scanAccount(source) } finally { pendingSource = null; showReview = true } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DotSettingsCard {
            ContactsOptimizerPolicyControl(settings) { policy ->
                processor.profiles.update(account) { it.copy(automaticPhotoPolicy = policy, autoUpdateExisting = true) }
            }
            Text(stringResource(R.string.contact_photos_auto_fill), Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ManualPhotoChoicesControl(account, revision, processor)
        }

        DotSettingsCard {
            ContactPhotoReadinessRow(readiness) { disclosure = "access" }
            if (readiness != ContactPhotoReadiness.Unavailable) {
                DotSettingsSwitchRow(stringResource(R.string.whatsapp_avatar_sync_title),
                    supporting = stringResource(R.string.contact_photos_whatsapp_summary),
                    checked = settings.enabled, onCheckedChange = { value ->
                        if (value) disclosure = "whatsapp"
                        else processor.profiles.update(account) { it.copy(enabled = false) }
                    })
                DotSettingsNavigationRow(stringResource(R.string.whatsapp_avatar_notification_access),
                    value = stringResource(if (access && settings.disclosed) R.string.whatsapp_avatar_access_allowed else R.string.whatsapp_avatar_access_required),
                    onClick = { disclosure = "access" })

            }
        }
        DotSettingsCard {
            DotSettingsSwitchRow(stringResource(R.string.whatsapp_avatar_normalize_numbers),
                supporting = stringResource(R.string.whatsapp_avatar_normalize_numbers_supporting),
                checked = settings.normalize, onCheckedChange = { value ->
                    processor.profiles.update(account) { it.copy(normalize = value) }
                })
            DotSettingsSwitchRow(stringResource(R.string.contact_photos_gravatar),
                supporting = stringResource(R.string.contact_photos_gravatar_summary),
                checked = settings.gravatar, onCheckedChange = { value ->
                    if (value) disclosure = "gravatar"
                    else processor.profiles.update(account) { it.copy(gravatar = false) }
                })
        }
        DotSettingsCard {
            DotSettingsSwitchRow(stringResource(R.string.contact_photos_business),
                supporting = stringResource(R.string.contact_photos_business_summary), checked = settings.business,
                onCheckedChange = { value ->
                    if (value) disclosure = "business"
                    else processor.profiles.update(account) { it.copy(business = false) }
                })
        }
        ContactPhotoFindSection(settings, googleReady, scanning || applying, activeSource, accountProgress, queued.size,
            googleAuthorization = {
                GoogleContactPhotoSettingsControl(showScan = false, selected = settings.google,
                    requestedAccount = account, embedded = true, interactionEnabled = !scanning,
                    authorized = settings.googleAuthorized,
                    onSelectionChanged = { value -> processor.profiles.update(account) {
                        it.copy(google = value, googleAuthorized = it.googleAuthorized && value)
                    } },
                    onStateChanged = { grantedAccount, enabled ->
                        processor.profiles.update(grantedAccount) { it.copy(google = enabled, googleAuthorized = enabled) }
                    })
            }) { source ->
            when (source) {
                "gravatar" -> if (processor.profiles.gravatarAllowed(account)) scan(source) else disclosure = "gravatar_scan"
                "business" -> if (processor.profiles.businessAllowed(account) && settings.businessDiscoveryDisclosed) scan(source) else disclosure = "business_scan"
                else -> scan(source)
            }
        }
        DotSettingsCard {
            DotSettingsNavigationRow(stringResource(R.string.contact_photos_review_results, queued.size),
                supporting = stringResource(R.string.contact_photos_review_summary), onClick = { if(onReviewRequested!=null)onReviewRequested() else showReview = true })
            TextButton(enabled = !resetting && (allStoredItems.isNotEmpty() || accountProgress != null || processor.profiles.result(account).detectedAt > 0),
                onClick = { confirmReset = true }, modifier = Modifier.padding(horizontal = 12.dp).testTag("contactFindReset")) {
                if (resetting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.contact_photos_reset))
            }
            val result = remember(account, revision) { processor.profiles.result(account) }
            val reviewAlreadyShown=queued.isNotEmpty() && result.outcome in setOf("review_required","ambiguous")
            if (!reviewAlreadyShown && !scanning && result.detectedAt > 0 && (accountProgress == null || result.outcome !in setOf("scanning","unchanged","checked","review_required"))) {
                val label = when (result.outcome) {
                    "updated" -> R.string.contact_photos_updated
                    "normalized" -> R.string.contact_photos_updated
                    "unchanged", "checked" -> R.string.contact_photos_unchanged
                    "review_required", "ambiguous" -> R.string.contact_photos_review_required
                    "permission_missing" -> R.string.contact_photos_permission
                    "consent_required" -> R.string.contact_photos_connect_google
                    "source_consent_required" -> R.string.contact_photos_source_consent_required
                    "company_search_unavailable" -> R.string.company_search_unavailable
                    "processing_failed" -> R.string.contact_photos_network_error
                    "interrupted" -> R.string.contact_photos_stopped
                    "scanning" -> R.string.contact_photos_stopped
                    else -> R.string.contact_photos_retry
                }

                Text(stringResource(label), Modifier.padding(horizontal = 18.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (resetFailed) Text(stringResource(R.string.contact_photos_retry), Modifier.padding(18.dp), color = MaterialTheme.colorScheme.error)
        }
    }
    if (storageFailed) Text(stringResource(R.string.contact_photos_storage_error), color = MaterialTheme.colorScheme.error)
    if (showReview && onReviewRequested==null) ContactPhotoProposalList(queued, reviews, processor, scanning || applying)
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.contact_photos_reset_confirm)) },
        text = { Text(stringResource(R.string.contact_photos_reset_summary)) },
        confirmButton = { TextButton(onClick = {
            confirmReset = false; resetting = true; resetFailed = false; showReview = false
            scope.launch {
                try { scanJob?.cancelAndJoin(); resetFailed = !processor.resetResults(account); showReview = false }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    processor.reportFailure(account, "processing_failed", error)
                    resetFailed = true
                }
                finally { resetting = false }
            }
        }, modifier = Modifier.testTag("contactFindResetConfirm")) { Text(stringResource(R.string.contact_photos_reset)) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } })
    disclosure?.let { action ->
        val source = action.removeSuffix("_scan")
        AlertDialog(onDismissRequest = { disclosure = null },
            title = { CompositionLocalProvider(LocalDensity provides disclosureDensity) { Text(stringResource(when (source) { "gravatar" -> R.string.contact_photos_gravatar; "business" -> R.string.contact_photos_business; else -> R.string.whatsapp_avatar_sync_title })) } },
            text = { CompositionLocalProvider(LocalDensity provides disclosureDensity) { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(when (source) { "gravatar" -> R.string.contact_photos_gravatar_disclosure; "business" -> R.string.contact_photos_business_disclosure; else -> R.string.contact_photos_disclosure }))
            } } },
            confirmButton = { TextButton(onClick = {
                processor.profiles.update(account) { it.copy(disclosed = it.disclosed || source == "whatsapp" || source == "access",
                    gravatarDisclosed = it.gravatarDisclosed || source == "gravatar",
                    businessDisclosed = it.businessDisclosed || source == "business",
                    businessDiscoveryDisclosed = it.businessDiscoveryDisclosed || source == "business",
                    enabled = it.enabled || source == "whatsapp",
                    normalize = it.normalize || source == "normalize",
                    gravatar = it.gravatar || source == "gravatar", business = it.business || source == "business") }
                disclosure = null
                if (source == "whatsapp" || source == "access") ContactOptimizationAccess.open(context)
                if (action.endsWith("_scan")) scan(source)
            }) { CompositionLocalProvider(LocalDensity provides disclosureDensity) { Text(stringResource(R.string.contact_photos_agree)) } } },
            dismissButton = { TextButton(onClick = { disclosure = null }) { CompositionLocalProvider(LocalDensity provides disclosureDensity) { Text(stringResource(R.string.cancel)) } } })
    }
}
