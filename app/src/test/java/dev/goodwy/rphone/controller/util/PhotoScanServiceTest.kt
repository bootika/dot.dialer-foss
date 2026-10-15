package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PhotoScanServiceTest {
    private val account = "photos@example.test"
    @After fun cleanup() { stopKoin() }

    private fun preferences(context: Context): PreferenceManager {
        val prefs = PreferenceManager(context)
        prefs.setString(GoogleContactPhotoPreferences.ACCOUNT, account)
        prefs.setBoolean(GoogleContactPhotoPreferences.ENABLED, true)
        runBlocking { withTimeout(5_000) {
            while (prefs.getString(GoogleContactPhotoPreferences.ACCOUNT, null) != account || !prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED, false)) delay(10)
        } }
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        return prefs
    }

    @Test fun serviceKeepsRunningAfterActivityStopsAndTimeoutLeavesResumableProgress() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = preferences(context)
        val arrived = CountDownLatch(1)
        val gate = CompletableDeferred<Unit>()
        val auth = object : GoogleContactsAuthorization {
            override val isAvailable = true
            override suspend fun authorize(accountName: String?): GoogleContactsAuthorizationResult {
                arrived.countDown(); gate.await()
                return GoogleContactsAuthorizationResult.Granted(account, "test-access-token")
            }
            override fun completeAuthorization(data: Intent?, requestedAccountName: String?) = GoogleContactsAuthorizationResult.Unavailable
            override suspend fun revoke(accountName: String) = true
        }
        val source = object : GooglePeoplePhotoSource {
            override suspend fun loadPhoto(accessToken: String, phoneNumber: String): GooglePeoplePhotoResult = error("No network expected")
        }
        val manager = CallPhotoHydrationManager(context, prefs, auth, source)
        val scan = GoogleContactPhotoScan(context, prefs, auth, manager, candidateLoader = { listOf(PhotoScanCandidate(1, "07700900123")) })
        stopKoin()
        startKoin { modules(module { single { scan } }) }
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        scan.start()
        val controller = Robolectric.buildService(GoogleContactPhotoScanService::class.java).create()
        try {
            controller.get().onStartCommand(Intent(context, GoogleContactPhotoScanService::class.java), 0, 1)
            assertTrue(arrived.await(5, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
            activity.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(scan.state.value.running)
            assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
            controller.get().onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            assertEquals(PhotoScanStatus.Stopped, scan.state.value.status)
        } finally {
            controller.destroy()
            gate.cancel()
        }
        val restored = requireNotNull(PhotoScanCheckpointStore(context).read())
        assertTrue(restored.state.canResume)
        assertEquals(1, restored.state.total)
        assertEquals(0, restored.state.checked)
    }

    @Test fun legacyRetryKeepsHistoricalCountersButCannotReadOrWriteContactPhotos() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = preferences(context)
        val readIds = mutableListOf<Long>()
        val large = ByteArrayOutputStream().use { out ->
            val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray()
        }
        val small = ByteArrayOutputStream().use { out ->
            Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray()
        }
        val auth = object : GoogleContactsAuthorization {
            override val isAvailable = true
            override suspend fun authorize(accountName: String?) = GoogleContactsAuthorizationResult.Granted(account, "test-access-token")
            override fun completeAuthorization(data: Intent?, requestedAccountName: String?) = GoogleContactsAuthorizationResult.Unavailable
            override suspend fun revoke(accountName: String) = true
        }
        val source = object : GooglePeoplePhotoSource {
            override suspend fun loadPhoto(accessToken: String, phoneNumber: String): GooglePeoplePhotoResult = error("Already-large photo must not download")
        }
        val store = object : GoogleContactPhotoStore {
            override suspend fun snapshot(phoneNumber: String, googleAccountName: String): GoogleContactPhotoSnapshot {
                val id = phoneNumber.last().digitToInt().toLong()
                readIds += id
                return GoogleContactPhotoSnapshot(id, id, null, "Test Alexandru Butica", listOf(phoneNumber), listOf(id), listOf(id), account, "com.google", id, small, large)
            }
            override suspend fun hydrate(request: GoogleContactPhotoHydrationRequest): GoogleContactPhotoHydrationResult = error("No write expected")
        }
        PhotoScanCheckpointStore(context).save(PhotoScanCheckpoint(photoScanAccountHash(account),
            PhotoScanState(PhotoScanStatus.Completed, total = 3, checked = 3, updated = 1, skipped = 1, failed = 1),
            setOf(1L, 2L, 3L), mapOf(3L to PhotoScanFailureKind.InvalidImage)))
        val manager = CallPhotoHydrationManager(context, prefs, auth, source, hydrator = store)
        val scan = GoogleContactPhotoScan(context, prefs, auth, manager, candidateLoader = { (1L..3L).map { PhotoScanCandidate(it, "0770090012$it") } })
        scan.executeFromService(retryOnly = true)
        assertTrue("Legacy scan has no post-D107 photo mutation authority", readIds.isEmpty())
        assertEquals(2, scan.state.value.checked)
        assertEquals(1, scan.state.value.updated)
        assertEquals(1, scan.state.value.skipped)
        assertEquals(0, scan.state.value.failed)
        assertEquals(PhotoScanStatus.ConsentRequired, scan.state.value.status)
    }
}
