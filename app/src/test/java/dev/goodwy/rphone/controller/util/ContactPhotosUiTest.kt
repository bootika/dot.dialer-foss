package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.screen.settings.ContactPhotoProposalList
import dev.goodwy.rphone.view.screen.settings.ContactPhotosScreen
import dev.goodwy.rphone.view.screen.settings.GoogleContactPhotoSettingsControl
import dev.goodwy.rphone.view.screen.settings.WhatsAppAvatarSyncControl
import dev.goodwy.rphone.view.screen.settings.ContactPhotoFindSection
import dev.goodwy.rphone.view.screen.settings.ContactPhotoReadinessRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.background
import dev.goodwy.rphone.view.theme.DotDialerTheme
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows
import java.io.File
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactPhotosUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private lateinit var provider: OptimizationContactsProvider
    private lateinit var prefs: PreferenceManager
    private lateinit var reviews: WhatsAppAvatarReviewStore
    private lateinit var processor: WhatsAppAvatarSyncProcessor
    private lateinit var navigator: DestinationsNavigator
    private val account = "first@example.test"
    private var googleAvailable = false
    @Volatile private var authorizationRequests = 0
    @Volatile private var sourceRequests = 0
    private var authorizationResult: GoogleContactsAuthorizationResult = GoogleContactsAuthorizationResult.Unavailable
    private var authorizedAccount: String? = null
    @Before fun prepare() {
        val app = rule.activity.application
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        val directory = File(rule.activity.cacheDir, "contact-ui-" + UUID.randomUUID()).apply { mkdirs() }
        context = object : ContextWrapper(rule.activity) {
            override fun getApplicationContext(): Context = this
            override fun createDeviceProtectedStorageContext(): Context = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "private").apply { mkdirs() }
        }
        Shadows.shadowOf(context.getSystemService(android.telephony.TelephonyManager::class.java)).setSimCountryIso("ro")
        provider = OptimizationContactsProvider(directory).also { it.install(context) }
        prefs = PreferenceManager(context)
        runBlocking { prefs.restoreAllPreferences(mapOf(GoogleContactPhotoPreferences.ACCOUNT to account,
            GoogleContactPhotoPreferences.ENABLED to false,
            PreferenceManager.KEY_IGNORE_SYSTEM_FONT_SCALE to false, PreferenceManager.KEY_LIQUID_GLASS to false)) }
        reviews = WhatsAppAvatarReviewStore(context).also { it.selectAccount(account) }
        val auth = object : GoogleContactsAuthorization {
            override val isAvailable get() = googleAvailable
            override suspend fun authorize(accountName: String?): GoogleContactsAuthorizationResult {
                authorizationRequests++; authorizedAccount = accountName; return authorizationResult
            }
            override fun completeAuthorization(data: Intent?, requestedAccountName: String?) = GoogleContactsAuthorizationResult.Unavailable
            override suspend fun revoke(accountName: String) = true
        }
        val hydration = CallPhotoHydrationManager(context, prefs, auth, object : GooglePeoplePhotoSource {
            override suspend fun loadPhoto(accessToken: String, phoneNumber: String): GooglePeoplePhotoResult = error("No network")
        })
        processor = WhatsAppAvatarSyncProcessor(context, prefs, reviews, WhatsAppAvatarContactStore(context), hydration,
            gravatar = object : ContactAvatarSource {
                override val sourceName = "Gravatar"
                override suspend fun load(email: String): ByteArray { sourceRequests++; return image(256, Color.BLUE) }
            }, companies = object : CompanyLogoSource {
                override suspend fun load(website: String): ByteArray { sourceRequests++; return image(256, Color.BLUE) }
            })
        processor.profiles.update(account) { it.copy(enabled = false, normalize = false, gravatar = false,
            business = false, google = false) }
        navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, method, _ ->
            if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        } as DestinationsNavigator
        startKoin { modules(module {
            single { prefs }; single { reviews }; single { processor }
            single<GoogleContactsAuthorization> { auth }; single { hydration }
            single { GoogleContactAccounts(context) }
            single { GoogleContactPhotoScan(context, prefs, auth, hydration) }
        }) }
    }
    @After fun cleanup() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(context).build())
        stopKoin()
    }
    @Test fun readinessExplainsEveryStateAndSetupIsExplicitAtLargeFonts() {
        var state by mutableStateOf(ContactPhotoReadiness.ConsentRequired)
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var setupClicks = 0
        show { CompositionLocalProvider(LocalDensity provides Density(1f,font)) {
            DotDialerTheme(systemDark = dark) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                        ContactPhotoReadinessRow(state) { setupClicks++ }
                    }
                }
            }
        } }
        for (theme in listOf(false,true)) for (scale in listOf(1f,2f)) for (value in ContactPhotoReadiness.entries) {
            rule.runOnIdle { dark=theme; font=scale; state=value }
            rule.waitForIdle()
            rule.onNodeWithTag("contactPhotoReadiness").assertIsDisplayed()
            if(value in listOf(ContactPhotoReadiness.ConsentRequired,ContactPhotoReadiness.NotificationAccessRequired)) {
                rule.onNodeWithTag("contactPhotoSetup").assertIsDisplayed()
            } else rule.onNodeWithTag("contactPhotoSetup").assertDoesNotExist()
            capture("readiness-${value.name}-${if(theme) "dark" else "light"}-$scale")
        }
        assertEquals(0,setupClicks)
        rule.runOnIdle { state=ContactPhotoReadiness.ConsentRequired }
        rule.onNodeWithTag("contactPhotoSetup").performClick()
        assertEquals(1,setupClicks)
        assertEquals(0,provider.writes); assertEquals(0,sourceRequests)
    }
    @Test fun selectedWhatsAppWithoutConsentOffersSetupAndCancelPreservesDefaults() {
        processor.profiles.update(account) { it.copy(enabled=true,disclosed=false) }
        show { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { WhatsAppAvatarSyncControl(account) } }
        rule.onNodeWithTag("contactPhotoSetup").performScrollTo().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_disclosure)).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.cancel)).performClick()
        assertTrue(processor.profiles.settings(account).enabled)
        assertFalse(processor.profiles.settings(account).disclosed)
        assertNull(Shadows.shadowOf(rule.activity).nextStartedActivity)
        assertEquals(0,provider.writes); assertEquals(0,sourceRequests)
    }
    private fun show(content: @Composable () -> Unit) {
        val software = object : android.view.View(context) { override fun isHardwareAccelerated() = false }
        rule.runOnIdle { (rule.activity.window.decorView as android.view.ViewGroup).addView(software,
            android.view.ViewGroup.LayoutParams(0, 0)) }
        rule.setContent { CompositionLocalProvider(LocalContext provides context, LocalView provides software) {
            DotDialerTheme { content() }
        } }
        rule.mainClock.advanceTimeBy(1200); rule.waitForIdle()
    }
    @Test fun freshPhotoSourceSelectionsAreOnWithoutConsentNetworkOrWrites() {
        val fresh = "fresh@example.test"
        googleAvailable = true
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        show {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                DotDialerTheme(systemDark = dark) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        WhatsAppAvatarSyncControl(fresh)
                        if (dev.goodwy.rphone.BuildConfig.FLAVOR != "gplay")
                            GoogleContactPhotoSettingsControl(showScan = false, selected = true)
                    }
                }
            }
        }
        val settings = processor.profiles.settings(fresh)
        assertTrue(settings.enabled && settings.normalize && settings.gravatar && settings.business &&
            settings.google && settings.autoUpdateExisting && settings.onlySameOrLarger)
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_only_same_or_larger)).assertDoesNotExist()
        rule.onAllNodes(isToggleable()).assertCountEquals(5).assertAll(isOn())
        for (theme in listOf(false, true)) for (font in listOf(1f, 1.5f, 2f)) {
            rule.runOnIdle { dark = theme; scale = font }
            rule.mainClock.advanceTimeBy(1200); rule.waitForIdle()
            rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_auto_policy))
                .performScrollTo().assertIsDisplayed()
            capture("defaults-size-" + (if (theme) "dark" else "light") + "-" + font)
            rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_google_connect_action))
                .performScrollTo().assertIsDisplayed()
            capture("defaults-google-" + (if (theme) "dark" else "light") + "-" + font)
        }
        assertFalse(settings.disclosed || settings.gravatarDisclosed || settings.businessDisclosed || settings.googleAuthorized)
        assertEquals(0, sourceRequests)
        assertEquals(0, authorizationRequests)
        assertEquals(0, provider.writes)
        assertNull(Shadows.shadowOf(rule.activity).nextStartedActivity)
    }
    @Test fun defaultGravatarRequiresConsentAndCancelNeverStartsLookup() {
        processor.profiles.update(account) { it.copy(gravatar = true) }
        show { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { WhatsAppAvatarSyncControl(account) } }
        rule.onNodeWithTag("contactFindAction-gravatar").performScrollTo().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_gravatar_disclosure)).assertIsDisplayed()
        assertEquals(0, sourceRequests)
        rule.onNodeWithText(rule.activity.getString(R.string.cancel)).performClick()
        assertFalse(processor.profiles.settings(account).gravatarDisclosed)
        assertEquals(0, sourceRequests)
    }
    @Test fun gravatarAgreementEnablesOnlyThatSourceAndScanStillDoesNotWrite() {
        processor.profiles.update(account) { it.copy(gravatar = true) }
        show { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { WhatsAppAvatarSyncControl(account) } }
        rule.onNodeWithTag("contactFindAction-gravatar").performScrollTo().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_agree)).performClick()
        rule.waitUntil(10_000) { sourceRequests == 1 }
        assertTrue(processor.profiles.settings(account).gravatarDisclosed)
        assertFalse(processor.profiles.settings(account).disclosed)
        assertFalse(processor.profiles.settings(account).businessDisclosed)
        assertFalse(processor.profiles.settings(account).googleAuthorized)
        assertEquals(0, provider.writes)
    }
    @Test fun googleIsSelectedWhileDisconnectedAndOpensAuthorizationOnlyAtConnect() {
        googleAvailable = true
        var selected by mutableStateOf(true)
        show { GoogleContactPhotoSettingsControl(showScan = false, selected = selected,
            onSelectionChanged = { selected = it }) }
        rule.onNodeWithText(rule.activity.getString(R.string.high_res_call_photos_title)).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_google_connect_action)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.high_res_call_photos_connected, account)).assertDoesNotExist()
        assertEquals(0, authorizationRequests)
        assertFalse(prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED, false))
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_google_connect_action)).performClick()
        rule.waitUntil(10_000) { authorizationRequests == 1 }
        assertFalse(prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED, false))
        assertEquals(0, provider.writes)
    }
    @Test fun disclosureMustBeConfirmedBeforeOpeningAndroidSettings() {
        show { WhatsAppAvatarSyncControl(account) }
        rule.onNodeWithText(rule.activity.getString(R.string.whatsapp_avatar_sync_title)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_disclosure)).assertIsDisplayed()
        assertFalse(processor.profiles.settings(account).enabled)
        assertNull(Shadows.shadowOf(rule.activity).nextStartedActivity)
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_agree)).performClick()
        assertTrue(processor.profiles.settings(account).enabled)
        assertTrue(processor.profiles.settings(account).disclosed)
        assertEquals(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS,
            Shadows.shadowOf(rule.activity).nextStartedActivity.action)
        assertEquals(0, provider.writes)
    }
    @Test fun automaticPolicyStoresOnlySelectedProfileWithoutGrantingPermissionOrWriting() {
        show { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { WhatsAppAvatarSyncControl(account) } }
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_only_same_or_larger)).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_auto_policy)).performScrollTo().performClick()
        rule.onNodeWithTag("optimizerPolicy-optimal").performScrollTo().performClick()
        assertEquals(ContactPhotoAutomaticPolicy.Optimal,ContactOptimizationProfiles(context).settings(account).automaticPhotoPolicy)
        assertEquals(ContactPhotoAutomaticPolicy.Newest,processor.profiles.settings("second@example.test").automaticPhotoPolicy)
        assertTrue(processor.profiles.settings(account).autoUpdateExisting)
        assertFalse(processor.profiles.settings(account).enabled)
        assertEquals(0, provider.writes)
        assertNull(Shadows.shadowOf(rule.activity).nextStartedActivity)
    }
    @Test fun automaticPolicyIsReadableInBothThemesAtLargeFonts() {
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        show {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                DotDialerTheme(systemDark = dark) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        WhatsAppAvatarSyncControl(account)
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (font in listOf(1f, 1.5f, 2f)) {
            rule.runOnIdle { dark = theme; scale = font }
            rule.mainClock.advanceTimeBy(1200); rule.waitForIdle()
            rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_auto_policy))
                .performScrollTo().assertIsDisplayed()
            capture("size-limit-" + (if (theme) "dark" else "light") + "-" + font)
            assertEquals(0, provider.writes)
            assertNull(Shadows.shadowOf(rule.activity).nextStartedActivity)
        }
    }
    @Test fun photoPolicyDefaultsToNewWithoutLossAndManualChoiceIsPerAccount() {
        show { WhatsAppAvatarSyncControl(account) }
        assertEquals(ContactPhotoAutomaticPolicy.Newest, processor.profiles.settings(account).effectivePhotoPolicy)
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_auto_policy)).performClick()
        rule.onNodeWithTag("optimizerPolicy-manual").performClick()
        assertEquals(ContactPhotoAutomaticPolicy.Manual, processor.profiles.settings(account).effectivePhotoPolicy)
        assertEquals(ContactPhotoAutomaticPolicy.Newest, processor.profiles.settings("second@example.test").effectivePhotoPolicy)
        assertFalse(processor.profiles.settings(account).enabled)
        assertEquals(0, provider.writes)
        assertNull(Shadows.shadowOf(rule.activity).nextStartedActivity)
    }
    @Test fun automaticUpdateControlRemainsReadableAtLargeFontsInBothThemes() {
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        show {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                DotDialerTheme(systemDark = dark) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        WhatsAppAvatarSyncControl(account)
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (font in listOf(1f, 1.5f, 2f)) {
            rule.runOnIdle { dark = theme; scale = font }
            rule.mainClock.advanceTimeBy(1200); rule.waitForIdle()
            rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_auto_policy)).performScrollTo().assertIsDisplayed()
            capture("auto-update-" + (if (theme) "dark" else "light") + "-" + font)
            assertEquals(0, provider.writes)
            assertTrue(processor.profiles.settings(account).autoUpdateExisting)
        }
    }
    @Test fun cancelingDisclosureLeavesCaptureDisabled() {
        show { WhatsAppAvatarSyncControl(account) }
        rule.onNodeWithText(rule.activity.getString(R.string.whatsapp_avatar_sync_title)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.cancel)).performClick()
        assertFalse(processor.profiles.settings(account).enabled)
        assertNull(Shadows.shadowOf(rule.activity).nextStartedActivity)
    }
    @Test fun proposalImageDoesNotRestartWhenProgressChangesOrGroupsMove() {
        val original = seedPhotos()
        val previousName = original.sortedByDescending { it.createdAtMillis }.first { it.previousFileName != null }.previousFileName
        val starts = java.util.concurrent.atomic.AtomicInteger()
        val successes = java.util.concurrent.atomic.AtomicInteger()
        val candidateSuccesses = java.util.concurrent.atomic.AtomicInteger()
        val previewError = java.util.concurrent.atomic.AtomicReference<String>()
        val previewStage = java.util.concurrent.atomic.AtomicReference("not_started")
        val candidateName = original.first { it.source == "Google" }.candidateFileName
        // Finish decoding after the initial UI idle so a fast file cannot hide a missing Android callback pump.
        val decodeRelease = kotlinx.coroutines.CompletableDeferred<Unit>()
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(context).components {
            add(object : coil.intercept.Interceptor {
                override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
                    decodeRelease.await()
                    return chain.proceed(chain.request)
                }
            })
        }.eventListener(object : coil.EventListener {
            override fun onStart(request: coil.request.ImageRequest) {
                if ((request.data as? File)?.name == previousName) { starts.incrementAndGet(); previewStage.set("started-${request.lifecycle.currentState}") }
            }
            override fun resolveSizeEnd(request: coil.request.ImageRequest, size: coil.size.Size) {
                if ((request.data as? File)?.name == previousName) previewStage.set("size_resolved")
            }
            override fun fetchStart(request: coil.request.ImageRequest, fetcher: coil.fetch.Fetcher, options: coil.request.Options) {
                if ((request.data as? File)?.name == previousName) previewStage.set("fetch_started")
            }
            override fun decodeStart(request: coil.request.ImageRequest, decoder: coil.decode.Decoder, options: coil.request.Options) {
                if ((request.data as? File)?.name == previousName) previewStage.set("decode_started")
            }
            override fun transformEnd(request: coil.request.ImageRequest, output: Bitmap) {
                if ((request.data as? File)?.name == previousName) previewStage.set("transform_finished")
            }
            override fun onCancel(request: coil.request.ImageRequest) {
                if ((request.data as? File)?.name == previousName) previewStage.set("cancelled")
            }
            override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) {
                if ((request.data as? File)?.name == previousName) previewError.set(result.throwable.toString())
            }
            override fun onSuccess(request: coil.request.ImageRequest, result: coil.request.SuccessResult) {
                if ((request.data as? File)?.name == previousName) successes.incrementAndGet()
                if ((request.data as? File)?.name == candidateName) candidateSuccesses.incrementAndGet()
            }
        }).build())
        var progress by mutableIntStateOf(0)
        var displayed by mutableStateOf(original)
        show { Column {
            androidx.compose.material3.Text("Checked $progress")
            ContactPhotoProposalList(displayed, reviews, processor)
        } }
        val previous=reviews.previousPreviewFile(original.sortedByDescending { it.createdAtMillis }.first { it.previousFileName != null })
        assertNotNull(previous)
        decodeRelease.complete(Unit)
        try { rule.waitUntil(10_000) { rule.waitForIdle(); starts.get() == 1 && successes.get() == 1 } }
        catch (error: androidx.compose.ui.test.ComposeTimeoutException) { throw AssertionError("preview starts=${starts.get()}, successes=${successes.get()}, readable=${previous?.canRead()}, bytes=${previous?.length()}, stage=${previewStage.get()}, activity=${rule.activity.lifecycle.currentState}, error=${previewError.get()}",error) }
        capture("stable-review-initial")
        repeat(10) { step -> rule.runOnIdle { progress = step + 1; displayed = displayed.map { it.copy() } }; rule.waitForIdle() }
        val id = UUID.randomUUID().toString()
        val extra = original.last().copy(id=id,contactId=11,rawContactId=21,
            candidateFileName=WhatsAppAvatarReviewStore.newFileName(id,"candidate"),
            previousFileName=WhatsAppAvatarReviewStore.newFileName(id,"previous"))
        reviews.enqueue(extra,image(640,Color.BLUE),image(96,Color.RED))
        rule.runOnIdle { displayed = listOf(extra) + original }
        rule.waitForIdle()
        rule.runOnIdle { displayed = original + extra }
        rule.waitForIdle()
        assertEquals("Unchanged avatar should keep one Coil request despite progress and row movement",1,starts.get())
        rule.onNodeWithTag("contactPhotoSelect-10:20").performClick()
        rule.onNodeWithText("Google").performClick()
        rule.waitUntil(10_000) { rule.waitForIdle(); candidateSuccesses.get() > 0 }
        rule.onNodeWithText("640 × 640").assertExists()
        rule.onNodeWithTag("contactPhotoApply").assertIsEnabled()
        rule.runOnIdle { progress++ }
        rule.waitForIdle()
        rule.onNodeWithText("640 × 640").assertExists()
        assertEquals(0,provider.writes)
        capture("stable-review-after-progress")
    }
    @Test fun googleFindConnectUsesSelectedAccountAndSharesTheGrantWithFind() {
        Assume.assumeTrue(dev.goodwy.rphone.BuildConfig.FLAVOR == "gplay")
        googleAvailable = true
        processor.profiles.update(account) { it.copy(google=true,googleAuthorized=false) }
        prefs.setString(GoogleContactPhotoPreferences.ACCOUNT,"other@example.test")
        prefs.setBoolean(GoogleContactPhotoPreferences.ENABLED,true)
        authorizationResult=GoogleContactsAuthorizationResult.Granted(account,"test-token")
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        show { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            DotDialerTheme(systemDark=dark) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                    WhatsAppAvatarSyncControl(account)
                }
            }
        } }
        for (theme in listOf(false,true)) for (font in listOf(1f,2f)) {
            rule.runOnIdle { dark=theme;scale=font }
            rule.onNodeWithTag("contactGoogleConnect").performScrollTo().assertIsDisplayed()
            capture("google-find-disconnected-$theme-$font")
        }
        rule.runOnIdle { dark=false;scale=1f }
        rule.onNodeWithTag("contactFindAction-google").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("contactGoogleConnect").assert(hasAnyAncestor(hasTestTag("contactFindCard-google")))
            .performScrollTo().performClick()
        rule.waitUntil(10_000) { authorizationRequests == 1 && processor.profiles.settings(account).googleAuthorized &&
            prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null)==account }
        assertEquals(account,authorizedAccount)
        assertEquals(account,prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null))
        rule.onNodeWithTag("contactGoogleConnect").assertDoesNotExist()
        rule.onNodeWithTag("contactFindAction-google").assertIsEnabled()
        assertEquals(0,provider.writes);assertEquals(0,sourceRequests)
        capture("google-find-connected")
    }
    @Test fun googleFindExpiredAuthorizationOffersReconnectWithoutLosingSelectionOrReviews() {
        Assume.assumeTrue(dev.goodwy.rphone.BuildConfig.FLAVOR == "gplay")
        googleAvailable=true
        val pending=seedPhotos()
        authorizationResult=GoogleContactsAuthorizationResult.Unavailable
        show { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { WhatsAppAvatarSyncControl(account) } }
        rule.onNodeWithTag("contactFindAction-google").performScrollTo().assertIsEnabled()
        rule.waitForIdle()
        rule.onNodeWithTag("contactFindAction-google").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        rule.waitUntil(10_000) { !processor.profiles.settings(account).googleAuthorized &&
            processor.profiles.result(account).outcome == "consent_required" }
        rule.onNodeWithTag("contactGoogleConnect").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("contactFindAction-google").assertIsNotEnabled()
        assertTrue(processor.profiles.settings(account).google)
        assertEquals(pending.map { it.id },reviews.forAccount(account).map { it.id })
        assertEquals("consent_required",processor.profiles.result(account).outcome)
        authorizationResult=GoogleContactsAuthorizationResult.Granted(account,"test-token")
        rule.onNodeWithTag("contactGoogleConnect").performClick()
        rule.waitUntil(10_000) { processor.profiles.settings(account).googleAuthorized }
        rule.onNodeWithTag("contactFindAction-google").assertIsEnabled()
        assertEquals(0,provider.writes)
    }
    @Test fun googleFindWrongAccountGrantDoesNotEnableSelectedAccount() {
        Assume.assumeTrue(dev.goodwy.rphone.BuildConfig.FLAVOR == "gplay")
        googleAvailable=true
        processor.profiles.update(account) { it.copy(google=true,googleAuthorized=false) }
        authorizationResult=GoogleContactsAuthorizationResult.Granted("other@example.test","test-token")
        show { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { WhatsAppAvatarSyncControl(account) } }
        rule.onNodeWithTag("contactGoogleConnect").performScrollTo().performClick()
        rule.waitUntil(10_000) { authorizationRequests == 1 }
        rule.onNodeWithText(rule.activity.getString(R.string.high_res_call_photos_error)).assertExists()
        assertFalse(prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED,false))
        assertFalse(processor.profiles.settings(account).googleAuthorized)
        rule.onNodeWithTag("contactFindAction-google").assertIsNotEnabled()
        assertEquals(0,provider.writes);assertEquals(0,sourceRequests)
    }
    @Test fun organizedSingleSourceCardGroupsActionsAndAvoidsDuplicatedReviewStatus() {
        val base=seedPhotos().first();reviews.clear(account)
        for(n in 1..4) {
            val id=UUID.randomUUID().toString();val phone="+4072100000$n"
            reviews.enqueue(base.copy(id=id,contactId=0,rawContactId=0,phoneDataId=null,matchedNumber=phone,displayNumber=null,
                displayName=phone,source="WhatsApp",candidateWidth=192,candidateHeight=192,previousWidth=null,previousHeight=null,
                candidateFileName=WhatsAppAvatarReviewStore.newFileName(id,"candidate"),previousFileName=null,
                matchStrength=WhatsAppAvatarMatchStrength.Ambiguous),image(192,Color.BLUE),null)
        }
        processor.profiles.record(account,ContactOptimizationResult(System.currentTimeMillis(),"ambiguous"))
        var dark by mutableStateOf(false);var font by mutableFloatStateOf(1f)
        show { CompositionLocalProvider(LocalDensity provides Density(1f,font)) { DotDialerTheme(systemDark=dark) {
            ContactPhotosScreen(navigator)
        } } }
        val list=rule.onNodeWithTag("contactReviewList")
        val title=rule.activity.getString(R.string.contact_photos_review_results,4)
        list.performScrollToNode(hasText(title));rule.onNodeWithText(title).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_review_required)).assertDoesNotExist()
        for(theme in listOf(false,true)) for(scale in listOf(1f,2f)) {
            rule.runOnIdle { dark=theme;font=scale }
            list.performScrollToIndex(2)
            rule.onNodeWithTag("contactReviewSource").assertIsEnabled().assertHasNoClickAction()
            val card=rule.onNodeWithTag("contactReviewUtilityCard").getUnclippedBoundsInRoot()
            for(tag in listOf("contactPhotoProposalsTitle","contactReviewSelectedCount","contactReviewSource",
                "contactPhotoPresets","contactPhotoSelectAll","contactPhotoClearPhotos")) {
                val bounds=rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                assertTrue("$tag must stay within the shared card",bounds.left>=card.left && bounds.right<=card.right && bounds.top>=card.top && bounds.bottom<=card.bottom)
            }
            val all=rule.onNodeWithTag("contactPhotoSelectAll").getUnclippedBoundsInRoot()
            val none=rule.onNodeWithTag("contactPhotoClearPhotos").getUnclippedBoundsInRoot()
            val presets=rule.onNodeWithTag("contactPhotoPresets").getUnclippedBoundsInRoot()
            assertTrue(kotlin.math.abs((all.right-all.left).value-(none.right-none.left).value)<=1f)
            assertEquals(all.top,none.top);assertEquals(all.bottom,none.bottom)
            assertTrue(presets.bottom<all.top)
            capture("review-organized-single-$theme-$scale")
        }
        rule.onNodeWithTag("contactPhotoSelectAll").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText(rule.activity.getString(R.string.review_selected_count,4)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("contactPhotoClearPhotos").assertIsEnabled().performClick()
        rule.onNodeWithTag("contactReviewSelectedCount").assertTextEquals(rule.activity.getString(R.string.review_selected_count,0))
        assertEquals(0,provider.writes);assertEquals(4,reviews.items.value.size)
        rule.runOnIdle { processor.profiles.record(account,ContactOptimizationResult(System.currentTimeMillis(),"processing_failed")) }
        val failureText=rule.activity.getString(R.string.contact_photos_network_error)
        list.performScrollToNode(hasText(failureText));rule.onNodeWithText(failureText).assertIsDisplayed()
        rule.runOnIdle {
            reviews.beginSearch(account,"numbers");assertTrue(reviews.finishBatch(account))
            processor.profiles.record(account,ContactOptimizationResult(System.currentTimeMillis(),"ambiguous"))
        }
        val pendingText=rule.activity.getString(R.string.contact_photos_review_required)
        list.performScrollToNode(hasText(pendingText));rule.onNodeWithText(pendingText).assertIsDisplayed()
        assertEquals(0,provider.writes);assertEquals(4,reviews.forAccount(account).size)
    }
    @Test fun allAndNoneCoverEightHundredNumbersInContinuousLazyScreenAtLargeFonts() {
        val base=seedPhotos().first();processor.profiles.update(account) { it.copy(normalize=true) }
        reviews.beginSearch(account,"numbers")
        val numbers=(1..800).map { n -> base.copy(id=UUID.randomUUID().toString(),contactId=n+100L,rawContactId=n+1000L,
            kind="number",source="Phone number",previousFileName=null,displayName="Test Alexandru Butica $n",
            originalNumber="0720033833",proposedNumber="+40720033833") }
        numbers.forEach { reviews.enqueue(it,null,null) };assertTrue(reviews.finishBatch(account))
        var dark by mutableStateOf(false);var font by mutableFloatStateOf(1f)
        show { CompositionLocalProvider(LocalDensity provides Density(1f,font)) { DotDialerTheme(systemDark=dark) {
            ContactPhotosScreen(navigator)
        } } }
        val list=rule.onNodeWithTag("contactReviewList")
        val reviewTitle=rule.activity.getString(R.string.contact_photos_review_results,800)
        list.performScrollToNode(hasText(reviewTitle));rule.onNodeWithText(reviewTitle).performClick()
        list.performScrollToNode(hasTestTag("contactPhotoSelectAll"))
        rule.onNodeWithTag("contactPhotoPresets").assertDoesNotExist()
        rule.onNodeWithTag("contactPhotoGroup-${reviewGroupKey(numbers[25])}").assertDoesNotExist()
        rule.onNodeWithTag("contactPhotoSelectAll").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText(rule.activity.getString(R.string.review_selected_count,800)).fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasTestTag("contactNumberSelect-${numbers.last().id}"))
        rule.onNodeWithTag("contactNumberSelect-${numbers.last().id}").assertIsOn()
        rule.onNodeWithTag("contactNumberSelect-${numbers.first().id}").assertDoesNotExist()
        for(theme in listOf(false,true)) for(scale in listOf(1f,2f)) {
            rule.runOnIdle { dark=theme;font=scale }
            list.performScrollToIndex(2)
            rule.onNodeWithTag("contactReviewSelectedCount").assertIsDisplayed()
            rule.onNodeWithTag("contactPhotoSelectAll").assertIsDisplayed()
            capture("review-800-continuous-$theme-$scale")
        }
        list.performScrollToNode(hasTestTag("contactPhotoClearPhotos"));rule.onNodeWithTag("contactPhotoClearPhotos").performClick()
        list.performScrollToNode(hasTestTag("contactNumberSelect-${numbers.last().id}"))
        rule.onNodeWithTag("contactNumberSelect-${numbers.last().id}").assertIsOff()
        list.performScrollToNode(hasTestTag("contactPhotoApply"));rule.onNodeWithTag("contactPhotoApply").assertIsNotEnabled()
        list.performScrollToNode(hasTestTag("contactReviewSource"));rule.onNodeWithTag("contactReviewSource").performClick()
        rule.onNodeWithTag("contactReviewSource-gravatar").performScrollTo().assertIsDisplayed()
        capture("review-continuous-source-picker-font200")
        rule.onNodeWithTag("contactReviewSource-gravatar").performClick()
        rule.waitUntil(10000) { reviews.selectedSource.value=="gravatar" }
        rule.onNodeWithTag("contactReviewPage").assertDoesNotExist()
        list.performScrollToNode(hasTestTag("contactPhotoSelect-10:20"));rule.onNodeWithTag("contactPhotoSelect-10:20").performClick()
        rule.onNodeWithText("WhatsApp").assertIsEnabled()
        assertEquals(0,provider.writes);assertEquals(803,reviews.forAccount(account).size)
    }
    @Test fun availablePhotoCanBeSelectedAfterPermissionRevocationButApplyCannotWrite() {
        val items=seedPhotos();Shadows.shadowOf(rule.activity.application).denyPermissions(Manifest.permission.WRITE_CONTACTS)
        show { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { ContactPhotoProposalList(items,reviews,processor) } }
        rule.onNodeWithTag("contactPhotoSelect-10:20").performClick()
        rule.onNodeWithText("WhatsApp").assertIsEnabled().performClick()
        rule.onNodeWithTag("contactPhotoApply").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText(rule.activity.getString(R.string.contact_photos_apply_failed)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0,provider.writes);assertEquals(3,reviews.forAccount(account).size)
    }
    @Test fun quickPresetMenuHasTitlesAndShortDescriptionsInBothThemesAndLargeFonts() {
        val items=seedPhotos()
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        show { CompositionLocalProvider(LocalDensity provides Density(1f,font)) {
            DotDialerTheme(systemDark=dark) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                    ContactPhotoProposalList(items,reviews,processor)
                }
                }
            }
        } }
        var normalTitleHeight=0f
        for(theme in listOf(false,true)) for(scale in listOf(1f,1.5f,2f)) {
            rule.runOnIdle { dark=theme;font=scale }
            rule.onNodeWithTag("contactPhotoSelectAll").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("contactPhotoClearPhotos").assertIsDisplayed()
            assertTrue(rule.onNodeWithTag("contactPhotoSelectionControls").getUnclippedBoundsInRoot().top <
                rule.onNodeWithTag("contactPhotoGroup-10:20").getUnclippedBoundsInRoot().top)
            capture("quick-selection-controls-$theme-$scale")
            rule.onNodeWithTag("contactPhotoPresets").performScrollTo().performClick()
            for(preset in ContactPhotoSelectionPreset.entries) {
                rule.onNodeWithTag("contactPhotoPreset-"+preset.name).performScrollTo().assertIsDisplayed()
            }
            rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_selection_last_found_summary)).assertExists()
            rule.onNodeWithTag("contactPhotoPreset-Optimal").performScrollTo()
            val titleHeight=rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_selection_optimal),useUnmergedTree=true)
                .getUnclippedBoundsInRoot().let { it.bottom.value-it.top.value }
            if(!theme && scale==1f) normalTitleHeight=titleHeight
            if(scale==2f) assertTrue("Popup must honor the inherited font scale",titleHeight>=normalTitleHeight*1.5f)
            capture("quick-preset-descriptions-$theme-$scale")
            rule.onNodeWithTag("contactPhotoPreset-Optimal").performClick()
        }
        assertEquals(0,provider.writes)
    }
    @Test fun quickPresetsKeepNumbersAndNoneClearsAllWithoutWriting() {
        seedPhotos()
        val id=UUID.randomUUID().toString()
        val number=reviews.items.value.first().copy(id=id,kind="number",candidateFileName=WhatsAppAvatarReviewStore.newFileName(id,"candidate"),
            previousFileName=null,originalNumber="0720033833",proposedNumber="+40720033833")
        processor.profiles.update(account) { it.copy(normalize=true) }
        reviews.enqueue(number,null,null)
        val items=reviews.forAccount(account)
        show { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { ContactPhotoProposalList(items,reviews,processor) } }
        rule.onNodeWithTag("contactNumberSelect-"+id).performScrollTo().performClick()
        rule.onNodeWithTag("contactPhotoPresets").performScrollTo().performClick()
        rule.onNodeWithTag("contactPhotoPreset-Optimal").performClick()
        rule.onNodeWithText("640 × 640").assertExists()
        rule.onNodeWithTag("contactPhotoApply").assertIsEnabled()
        rule.onNodeWithTag("contactPhotoSelectAll").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("contactNumberSelect-"+id).assertIsOn()
        capture("all-selection-state")
        rule.onNodeWithText("640 \u00d7 640").assertExists()
        rule.onNodeWithTag("contactPhotoClearPhotos").performScrollTo().performClick()
        rule.onNodeWithText("96 × 96").assertExists()
        rule.onNodeWithTag("contactNumberSelect-"+id).assertIsOff()
        rule.onNodeWithTag("contactPhotoApply").assertIsNotEnabled()
        for(preset in listOf(ContactPhotoSelectionPreset.Largest,ContactPhotoSelectionPreset.LastFound)) {
            rule.onNodeWithTag("contactPhotoPresets").performScrollTo().performClick()
            rule.onNodeWithTag("contactPhotoPreset-"+preset.name).performClick()
            rule.onNodeWithText("640 × 640").assertExists()
        }
        assertEquals(0,provider.writes)
        capture("quick-photo-presets")
    }
    @Test fun manualWhatsAppSelectionMayShrinkAndWritesOnlyAfterApply() {
        val base=seedPhotos().first()
        val id=UUID.randomUUID().toString()
        processor.profiles.update(account) { it.copy(enabled=true,disclosed=true) }
        val item=base.copy(id=id,candidateFileName=WhatsAppAvatarReviewStore.newFileName(id,"candidate"),
            previousFileName=WhatsAppAvatarReviewStore.newFileName(id,"previous"),source="WhatsApp",
            candidateWidth=192,candidateHeight=192,previousWidth=512,previousHeight=512)
        reviews.enqueue(item,image(192,Color.BLUE),image(512,Color.RED))
        provider.pictures[20]=image(512,Color.RED)
        val queued=listOf(item)
        show { CompositionLocalProvider(LocalDensity provides Density(1f,2f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { ContactPhotoProposalList(queued,reviews,processor) }
        } }
        rule.onNodeWithTag("contactPhotoSelectAll").assertIsEnabled()
        rule.onNodeWithTag("contactPhotoSelect-10:20").performScrollTo().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_lower_resolution)).assertExists()
        rule.onNodeWithText("WhatsApp").assertIsEnabled()
        capture("whatsapp-manual-smaller-font200")
        rule.onNodeWithText("WhatsApp").performClick()
        assertEquals(0,provider.writes)
        rule.onNodeWithTag("contactPhotoApply").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(10_000) { reviews.items.value.none { it.id==id } }
        assertEquals(1,provider.writes)
        assertEquals(192,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
    }
    @Test fun applyChangedPhotoRefreshesReviewAndExplainsTheRetryWithoutWriting() {
        seedPhotos()
        var items by mutableStateOf(reviews.items.value)
        show { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ContactPhotoProposalList(items,reviews,processor)
        } }
        rule.onNodeWithTag("contactPhotoSelect-10:20").performScrollTo().performClick()
        rule.onNodeWithText("Gravatar").performClick()
        rule.onNodeWithText("256 \u00d7 256").assertExists()
        rule.runOnIdle {
            val changed=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.GREEN)
                for(x in 0 until 96) for(y in 0 until 96) if((x/8+y/8)%2==0) setPixel(x,y,Color.BLACK)
            }
            provider.pictures[20]=ByteArrayOutputStream().use { changed.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray() }
            changed.recycle()
        }
        rule.onNodeWithTag("contactPhotoApply").performScrollTo().performClick()
        rule.waitUntil(10_000) { reviews.forAccount(account).any { it.source=="Gravatar" && !items.any { old -> old.id==it.id } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(rule.activity.getString(R.string.contact_photos_photo_changed)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_photo_changed)).assertExists()
        assertEquals(0,provider.writes)
        rule.runOnIdle { items=reviews.items.value }
        rule.onNodeWithTag("contactPhotoSelectAll").performScrollTo().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_photo_changed)).assertDoesNotExist()
        assertEquals(0,provider.writes)
        capture("changed-photo-review-retry")
    }
    @Test fun avatarDropdownShowsPreviousAndAllSourceResolutionsWithoutWriting() {
        val items = seedPhotos()
        show { ContactPhotoProposalList(items, reviews, processor) }
        rule.onNodeWithTag("contactPhotoSelect-10:20").performClick()
        rule.onNodeWithText("128 × 128").assertExists()
        rule.onNodeWithText("256 × 256").assertExists()
        rule.onNodeWithText("640 × 640").assertExists()
        rule.onAllNodesWithText(rule.activity.getString(R.string.whatsapp_avatar_previous)).onLast().assertExists()
        capture("dropdown-sources")
        assertEquals(0, provider.writes)
    }
    @Test fun selectingGravatarOnlyWritesAfterApply() {
        seedPhotos()
        provider.pendingPhotoReads = 4
        show { val items by reviews.items.collectAsState(); ContactPhotoProposalList(items, reviews, processor) }
        rule.onNodeWithTag("contactPhotoSelect-10:20").performClick()
        rule.onNodeWithText("Gravatar").performClick()
        assertEquals(0, provider.writes)
        rule.onNodeWithTag("contactPhotoApply").assertIsEnabled().performClick()
        rule.waitUntil(10_000) { provider.writes == 1 }
        rule.waitUntil(10_000) { reviews.forAccount(account).size == 2 }
        rule.waitForIdle()
        assertTrue(sameNullablePhoto(image(256, Color.BLUE), provider.photo(20)))
        assertEquals(2, reviews.forAccount(account).size)
    }
    @Test fun choosingPreviousAgainCancelsThePhotoChange() {
        val items = seedPhotos()
        show { ContactPhotoProposalList(items, reviews, processor) }
        rule.onNodeWithTag("contactPhotoSelect-10:20").performClick()
        rule.onNodeWithText("Gravatar").performClick()
        rule.onNodeWithTag("contactPhotoSelect-10:20").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.whatsapp_avatar_previous)).performClick()
        rule.onNodeWithTag("contactPhotoApply").assertIsNotEnabled()
        assertEquals(0, provider.writes)
    }
    @Test fun missingPermissionDisablesApply() {
        val items = seedPhotos()
        Shadows.shadowOf(rule.activity.application).denyPermissions(Manifest.permission.WRITE_CONTACTS)
        show { ContactPhotoProposalList(items, reviews, processor) }
        rule.onNodeWithTag("contactPhotoApply").assertIsNotEnabled()
        assertEquals(0, provider.writes)
    }
    @Test fun permissionStatusRefreshesOnReturnFromSettings() {
        processor.profiles.update(account) { it.copy(disclosed = true) }
        show { ContactPhotosScreen(navigator) }
        rule.onNodeWithText(rule.activity.getString(R.string.whatsapp_avatar_access_required)).assertExists()
        rule.runOnIdle {
            Shadows.shadowOf(context.getSystemService(NotificationManager::class.java))
                .setNotificationListenerAccessGranted(ComponentName(context, WhatsAppAvatarNotificationListener::class.java), true)
            (rule.activity.lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            (rule.activity.lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        rule.mainClock.advanceTimeBy(1200); rule.waitForIdle()
        rule.onNodeWithText(rule.activity.getString(R.string.whatsapp_avatar_access_allowed)).assertExists()
        assertEquals(0, provider.writes)
    }
    @Test fun sourcesAndQueuesChangeWithSelectedAccount() {
        seedPhotos()
        processor.profiles.update("second@example.test") { it.copy(normalize = true, enabled = false) }
        var selected by mutableStateOf(account)
        show { key(selected) { WhatsAppAvatarSyncControl(selected) } }
        assertEquals(3, reviews.forAccount(account).size)
        rule.runOnIdle { processor.selectAccount("second@example.test"); selected = "second@example.test" }
        rule.waitForIdle()
        assertTrue(reviews.items.value.isEmpty())
        assertFalse(processor.profiles.settings("second@example.test").enabled)
        assertTrue(processor.profiles.settings("second@example.test").normalize)
        assertEquals(0, provider.writes)
    }
    @Test fun selectorIsReadableInBothThemesAtLargeFonts() {
        val items = seedPhotos()
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        show {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                DotDialerTheme(systemDark = dark) {
                    key(dark, scale) { ContactPhotoProposalList(items, reviews, processor) }
                }
            }
        }
        for (theme in listOf(false, true)) for (font in listOf(1f, 1.5f, 2f)) {
            rule.runOnIdle { dark = theme; scale = font }
            rule.mainClock.advanceTimeBy(1200);rule.waitForIdle()
            rule.onNodeWithTag("contactPhotoSelect-10:20").performClick()
            rule.onNodeWithText("640 × 640").performScrollTo().assertIsDisplayed()
            capture("selector-before-assert-" + font)
            for (label in listOf("Google", "Gravatar", "640 × 640")) {
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                rule.onAllNodesWithText(label).onLast().performSemanticsAction(
                    androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                assertEquals("Popup must inherit font scale", font, layouts.single().layoutInput.density.fontScale, 0.001f)
                assertFalse(label + " font=" + font + " size=" + layouts.single().size +
                    " widthOverflow=" + layouts.single().didOverflowWidth + " heightOverflow=" + layouts.single().didOverflowHeight,
                    layouts.single().hasVisualOverflow)
            }
            capture("selector-" + (if (theme) "dark" else "light") + "-" + font)
            rule.onAllNodesWithText(rule.activity.getString(R.string.whatsapp_avatar_previous)).onLast().performClick()
        }
        assertEquals(0, provider.writes)
    }
    @Test @Config(qualifiers = "w800dp-h360dp-land-mdpi")
    fun selectorIsReachableInLandscape() {
        val items = seedPhotos()
        show { ContactPhotoProposalList(items, reviews, processor) }
        rule.onNodeWithTag("contactPhotoSelect-10:20").performClick()
        rule.onNodeWithText("640 × 640").performScrollTo().assertIsDisplayed()
        capture("selector-landscape")
        assertEquals(0, provider.writes)
    }

    private fun seedPhotos(): List<WhatsAppAvatarReviewItem> {
        val old = image(96, Color.RED)
        provider.pictures[20] = old
        runBlocking { prefs.restoreAllPreferences(mapOf(GoogleContactPhotoPreferences.ACCOUNT to account,
            GoogleContactPhotoPreferences.ENABLED to true)) }
        processor.profiles.update(account) { it.copy(enabled = true, gravatar = true, google = true, disclosed = true, gravatarDisclosed = true, googleAuthorized = true) }
        for ((source, size, colour) in listOf(Triple("WhatsApp", 128, Color.GREEN),
            Triple("Gravatar", 256, Color.BLUE), Triple("Google", 640, Color.GREEN))) {
            val id = UUID.randomUUID().toString()
            reviews.enqueue(WhatsAppAvatarReviewItem(id, 10, 20, 30, "Test Alexandru Butica", "+40720033833",
                account, WhatsAppAvatarReviewStore.newFileName(id, "candidate"), WhatsAppAvatarReviewStore.newFileName(id, "previous"),
                size, size, 96, 96, source, WhatsAppAvatarMatchStrength.ExactPhone, System.currentTimeMillis(), source = source),
                image(size, colour), old)
        }
        return reviews.forAccount(account)
    }
    @Test fun findCardsHaveSourceTitlesDescriptionsAndCompactActionsInBothThemesAndLargeFonts() {
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var active by mutableStateOf<String?>(null)
        var lastSource by mutableStateOf("")
        val sources=if(dev.goodwy.rphone.BuildConfig.FLAVOR=="gplay") listOf("numbers","gravatar","business","google") else listOf("numbers","gravatar","business")
        show { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            DotDialerTheme(systemDark=dark) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                    ContactPhotoFindSection(ContactOptimizationSettings(),true,active!=null,active,
                        active?.let { ContactPhotoScanProgress(account,it,ContactPhotoScanPhase.Searching,24,183) },71) { lastSource=it }
                }
                }
            }
        } }
        for (theme in listOf(false,true)) for(font in listOf(1f,1.5f,2f)) {
            rule.runOnIdle { dark=theme;scale=font;active=null }
            rule.mainClock.advanceTimeBy(1200);rule.waitForIdle()
            for(source in sources) {
                rule.onNodeWithTag("contactFindAction-"+source).performScrollTo().assertIsDisplayed().assertIsEnabled()
            }
            rule.onNodeWithTag("contactFindCard-gravatar").performScrollTo()
            capture("find-idle-"+dev.goodwy.rphone.BuildConfig.FLAVOR+"-"+theme+"-"+font)
            rule.runOnIdle { active="gravatar" }
            rule.mainClock.advanceTimeBy(1200);rule.waitForIdle()
            rule.onNodeWithTag("contactFindProgress-gravatar").assertIsDisplayed()
            rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_scan_count,24,183)).assertExists()
            rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_scan_results,71)).assertExists()
            rule.onNodeWithTag("contactFindAction-business").assertIsNotEnabled()
            capture("find-active-"+dev.goodwy.rphone.BuildConfig.FLAVOR+"-"+theme+"-"+font)
        }
        rule.runOnIdle { active=null }
        rule.onNodeWithTag("contactFindAction-gravatar").performScrollTo().performClick()
        assertEquals("gravatar",lastSource)
        assertEquals(0,provider.writes)
        assertEquals(0,sourceRequests)
    }
    @Test fun resetConfirmationCancelsOrClearsPendingResultsWithoutContactWrites() {
        seedPhotos()
        val saved=processor.profiles.settings(account)
        show { ContactPhotosScreen(navigator) }
        rule.onNodeWithTag("contactFindReset").performScrollTo().assertIsDisplayed()
        capture("find-review-page-" + dev.goodwy.rphone.BuildConfig.FLAVOR)
        rule.onNodeWithTag("contactFindReset").performClick()
        capture("find-reset-confirm-" + dev.goodwy.rphone.BuildConfig.FLAVOR)
        rule.onNodeWithText(rule.activity.getString(R.string.cancel)).performClick()
        assertEquals(3,reviews.forAccount(account).size)
        rule.onNodeWithTag("contactFindReset").performScrollTo().performClick()
        rule.onNodeWithTag("contactFindResetConfirm").performClick()
        rule.waitUntil(10_000) { reviews.items.value.isEmpty() }
        rule.waitForIdle()
        rule.onNodeWithTag("contactFindReset").assertIsNotEnabled()
        assertEquals(saved,processor.profiles.settings(account))
        assertEquals(0,provider.writes)
        assertEquals(0,sourceRequests)
    }
    @Test fun searchFailureAndStopAreShownWithinTheSourceCard() {
        var phase by mutableStateOf(ContactPhotoScanPhase.Failed)
        show { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ContactPhotoFindSection(ContactOptimizationSettings(),false,false,null,
                ContactPhotoScanProgress(account,"gravatar",phase,2,4),1) {}
        } }
        rule.onNodeWithTag("contactFindProgress-gravatar").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_network_error)).assertExists()
        rule.runOnIdle { phase=ContactPhotoScanPhase.Stopped }
        rule.waitForIdle()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_stopped)).assertExists()
        if(dev.goodwy.rphone.BuildConfig.FLAVOR=="gplay") rule.onNodeWithTag("contactFindAction-google").assertIsNotEnabled()
    }
    @Test fun legacyMissingLogoIsHiddenAndRealProposalsHaveSectionSpacing() {
        val items = seedPhotos()
        val legacy = items.first().copy(id = UUID.randomUUID().toString(), contactId = 30,
            rawContactId = 40, kind = "missing_logo", displayName = "Test company", source = "Company logo")
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        show { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            DotDialerTheme(systemDark = dark) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                        androidx.compose.material3.Text("Results", Modifier.height(48.dp).testTag("precedingResults"))
                        ContactPhotoProposalList(items + legacy, reviews, processor)
                    }
                }
            }
        } }
        for (theme in listOf(false, true)) for (font in listOf(1f, 1.5f, 2f)) {
            rule.runOnIdle { dark = theme; scale = font }
            rule.waitForIdle()
            val title = rule.onNodeWithTag("contactPhotoProposalsTitle").fetchSemanticsNode().boundsInRoot
            val above = rule.onNodeWithTag("precedingResults").fetchSemanticsNode().boundsInRoot
            assertTrue("The section needs at least 24dp above its heading", title.top - above.bottom >= 24f)
            rule.onNodeWithTag("contactPhotoGroup-30:40").assertDoesNotExist()
            rule.onNodeWithTag("contactPhotoGroup-10:20").assertExists()
            rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_no_site)).assertDoesNotExist()
            capture("proposals-spacing-" + theme + "-" + font)
        }
        assertEquals(0, provider.writes)
        assertEquals(0, sourceRequests)
    }
    @Test fun legacyOnlyResultsDoNotRenderAProposalsHeadingOrApplyButton() {
        val legacy = seedPhotos().first().copy(kind = "missing_logo")
        show { ContactPhotoProposalList(listOf(legacy), reviews, processor) }
        rule.onNodeWithTag("contactPhotoProposalsTitle").assertDoesNotExist()
        rule.onNodeWithTag("contactPhotoApply").assertDoesNotExist()
        assertEquals(0, provider.writes)
        assertEquals(0, sourceRequests)
    }
    @Test fun legacyOnlyQueueHasZeroReviewCountAndResetStillClearsIt() {
        val legacy = seedPhotos().first().copy(kind = "missing_logo", previousFileName = null)
        reviews.clear(account)
        reviews.enqueue(legacy, null, null)
        val savedSettings = processor.profiles.settings(account)
        show { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            WhatsAppAvatarSyncControl(account, initiallyReview = true)
        } }
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_review_results, 0))
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("contactPhotoProposalsTitle").assertDoesNotExist()
        rule.onNodeWithTag("contactFindReset").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag("contactFindResetConfirm").performClick()
        rule.waitUntil(10_000) { reviews.items.value.isEmpty() }
        rule.waitForIdle()
        rule.onNodeWithTag("contactFindReset").assertIsNotEnabled()
        assertEquals(savedSettings, processor.profiles.settings(account))
        assertEquals(0, provider.writes)
        assertEquals(0, sourceRequests)
    }
    private fun image(size: Int, colour: Int): ByteArray = ByteArrayOutputStream().use {
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }
            .compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }
    @Test fun fivePolicyChoicesAndDescriptionsRemainReachableAtLargeFontsInBothThemes() {
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var normalTitleHeight=0f
        show {
            CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
                DotDialerTheme(systemDark=dark) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { WhatsAppAvatarSyncControl(account) } }
            }
        }
        for (theme in listOf(false,true)) for (font in listOf(1f,2f)) {
            rule.runOnIdle { dark=theme; scale=font }; rule.waitForIdle()
            rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_auto_policy)).performScrollTo().performClick()
            for (key in listOf("manual","missing","latest","optimal","newest")) {
                rule.onNodeWithTag("optimizerPolicy-$key").performScrollTo().assertIsDisplayed()
                if (key=="manual") {
                    val bounds=rule.onNodeWithText(rule.activity.getString(R.string.optimizer_manual),useUnmergedTree=true).getUnclippedBoundsInRoot()
                    val height=bounds.bottom.value-bounds.top.value
                    if (font==1f) normalTitleHeight=height else assertTrue(height>=normalTitleHeight*1.5f)
                }
                if (key=="manual" || key=="newest") capture("optimizer-policies-$theme-$font-$key")
            }
            rule.onNodeWithText(rule.activity.getString(R.string.cancel)).performClick()
            assertEquals(ContactPhotoAutomaticPolicy.Newest,processor.profiles.settings(account).effectivePhotoPolicy)
            assertEquals(0,provider.writes)
        }
    }
    @Test fun companyDiscoveryNeedsRenewedConsentAndCancelPreservesLegacyConsent() {
        processor.profiles.update(account) { it.copy(business=true,businessDisclosed=true,businessDiscoveryDisclosed=false) }
        var dark by mutableStateOf(false);var font by mutableFloatStateOf(1f)
        show { CompositionLocalProvider(LocalDensity provides Density(1f,font)) { MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { WhatsAppAvatarSyncControl(account) }
        } } }
        rule.onNodeWithTag("contactFindAction-business").performScrollTo().performClick()
        val text=rule.activity.getString(R.string.contact_photos_business_disclosure)
        rule.onNodeWithText(text).assertIsDisplayed();assertEquals(0,sourceRequests)
        val small=rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.height
        val cancelLabel=rule.activity.getString(R.string.cancel)
        val smallButton=rule.onNodeWithText(cancelLabel,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.height
        for(theme in listOf(false,true)) {
            rule.runOnIdle { dark=theme;font=2f };rule.waitForIdle()
            assertTrue(rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.height>small*1.5f)
            assertTrue(rule.onNodeWithText(cancelLabel,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.height>smallButton*1.5f)
            capture("company-discovery-${if(theme) "dark" else "light"}-font200")
        }
        rule.onNodeWithText(rule.activity.getString(R.string.cancel)).performClick()
        assertFalse(processor.profiles.settings(account).businessDiscoveryDisclosed)
        assertTrue(processor.profiles.settings(account).businessDisclosed)
        assertEquals(0,provider.writes);assertEquals(0,sourceRequests)
        rule.onNodeWithTag("contactFindAction-business").performScrollTo().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.contact_photos_agree)).performClick()
        rule.waitForIdle();assertTrue(processor.profiles.settings(account).businessDiscoveryDisclosed)
    }
    private fun capture(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val manager = org.robolectric.util.ReflectionHelpers.callStaticMethod<Any>(
            Class.forName("android.view.WindowManagerGlobal"), "getInstance")
        val windows = org.robolectric.util.ReflectionHelpers.getField<List<android.view.View>>(manager, "mViews")
        for (window in windows) {
            val location = IntArray(2); window.getLocationOnScreen(location)
            canvas.save(); canvas.translate(location[0].toFloat(), location[1].toFloat())
            window.draw(canvas); canvas.restore()
        }
        val directory = File("build/reports/visual/contact-photos-d107").apply { mkdirs() }
        File(directory, name + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
