package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ContactPhotoDefaultsTest {
    private lateinit var context: Context
    private lateinit var profiles: ContactOptimizationProfiles
    private lateinit var prefs: PreferenceManager
    private lateinit var reviews: WhatsAppAvatarReviewStore
    private lateinit var provider: OptimizationContactsProvider
    private lateinit var processor: WhatsAppAvatarSyncProcessor
    private var requests = 0
    private val account = "first@example.test"

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        val directory = File(app.cacheDir, "photo-defaults-" + UUID.randomUUID()).apply { mkdirs() }
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun createDeviceProtectedStorageContext(): Context = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "private").apply { mkdirs() }
        }
        prefs = PreferenceManager(context)
        runBlocking { prefs.restoreAllPreferences(mapOf(GoogleContactPhotoPreferences.ACCOUNT to account,
            GoogleContactPhotoPreferences.ENABLED to false)) }
        profiles = ContactOptimizationProfiles(context)
        reviews = WhatsAppAvatarReviewStore(context).also { it.selectAccount(account) }
        provider = OptimizationContactsProvider(directory).also { it.install(context) }
        val auth = object : GoogleContactsAuthorization {
            override val isAvailable = true
            override suspend fun authorize(accountName: String?): GoogleContactsAuthorizationResult {
                requests++; return GoogleContactsAuthorizationResult.Unavailable
            }
            override fun completeAuthorization(data: Intent?, requestedAccountName: String?) = GoogleContactsAuthorizationResult.Unavailable
            override suspend fun revoke(accountName: String) = true
        }
        val hydration = CallPhotoHydrationManager(context, prefs, auth, object : GooglePeoplePhotoSource {
            override suspend fun loadPhoto(accessToken: String, phoneNumber: String): GooglePeoplePhotoResult {
                requests++; return GooglePeoplePhotoResult.AuthorizationExpired
            }
        })
        processor = WhatsAppAvatarSyncProcessor(context, prefs, reviews, WhatsAppAvatarContactStore(context), hydration,
            profiles = profiles, gravatar = object : ContactAvatarSource {
                override val sourceName = "Gravatar"
                override suspend fun load(email: String): ByteArray? { requests++; return null }
            }, companies = object : CompanyLogoSource {
                override suspend fun load(website: String): ByteArray? { requests++; return null }
            }, notificationAccess = { true })
    }
    @Test fun allSelectionsDefaultOnButGrantsAndConsentsDefaultOff() {
        val settings = profiles.settings(account)
        assertTrue(settings.enabled && settings.normalize && settings.gravatar && settings.business &&
            settings.google && settings.autoUpdateExisting && settings.onlySameOrLarger)
        assertFalse(settings.disclosed || settings.gravatarDisclosed || settings.businessDisclosed || settings.googleAuthorized)
        assertFalse(profiles.gravatarAllowed(account))
        assertFalse(profiles.businessAllowed(account))
        assertFalse(profiles.googleAllowed(account))
    }
    @Test fun defaultSelectionAndAccountSwitchNeverAuthorizeOrCallExternalSources() = runBlocking {
        provider.companies[20] = "Test company" to "https://example.com/"
        processor.selectAccount(account)
        for (source in listOf("gravatar", "business", "google")) processor.scanAccount(source)
        val event = Notification.Builder(context, "test-messages").setSmallIcon(android.R.drawable.sym_action_call).build()
        assertNull(processor.capture("com.whatsapp", event, null))
        assertEquals(0, requests)
        assertEquals(0, provider.writes)
        assertTrue(reviews.items.value.isEmpty())
        assertFalse(prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED, false))
        processor.selectAccount("second@example.test")
        assertFalse(profiles.googleAllowed("second@example.test"))
        assertEquals(0, requests)
    }
    @Test fun oneSourceConsentNeverAuthorizesAnotherSource() = runBlocking {
        provider.companies[20] = "Test company" to "https://example.com/"
        profiles.update(account) { it.copy(gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        assertEquals(1, requests)
        processor.scanAccount("business"); processor.scanAccount("google")
        assertEquals(1, requests)
        assertFalse(profiles.settings(account).disclosed)
        assertFalse(profiles.settings(account).businessDisclosed)
        profiles.update(account) { it.copy(businessDisclosed = true) }
        processor.scanAccount("business")
        assertEquals(2, requests)
        assertEquals(0, provider.writes)
    }
    @Test fun explicitOffChoicesAndConsentsRemainPerAccountAfterReload() {
        profiles.update(account) { it.copy(enabled = false, normalize = false, gravatar = false,
            business = false, google = false, autoUpdateExisting = false, onlySameOrLarger = false) }
        val saved = ContactOptimizationProfiles(context).settings(account)
        assertFalse(saved.enabled || saved.normalize || saved.gravatar || saved.business || saved.google ||
            saved.autoUpdateExisting || saved.onlySameOrLarger)
        val fresh = profiles.settings("second@example.test")
        assertTrue(fresh.enabled && fresh.normalize && fresh.gravatar && fresh.business && fresh.google &&
            fresh.autoUpdateExisting && fresh.onlySameOrLarger)
        assertFalse(fresh.disclosed || fresh.googleAuthorized || fresh.gravatarDisclosed || fresh.businessDisclosed)
    }
    @Test fun legacyExplicitSourceOptInsMigrateWithoutInventingMissingConsent() {
        val file = File(profiles.directory(account), "settings.json")
        file.writeText("""{"enabled":false,"gravatar":true,"business":false,"google":true}""")
        val old = profiles.settings(account)
        assertFalse(old.enabled || old.disclosed || old.businessDisclosed)
        assertTrue(old.gravatarDisclosed && old.googleAuthorized)
        assertFalse(profiles.settings("second@example.test").googleAuthorized)
        assertFalse(profiles.settings("second@example.test").gravatarDisclosed)
    }
}
