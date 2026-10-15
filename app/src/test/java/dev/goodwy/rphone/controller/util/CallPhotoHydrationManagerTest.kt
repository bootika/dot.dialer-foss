package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallPhotoHydrationManagerTest {
    @Test fun callAndLegacyScanNeverWriteWithoutUserSelection() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FakePhotoStore(bitmapBytes(96))
        val source = FixedPhotoSource(bitmapBytes(640))
        val manager = CallPhotoHydrationManager(context, PreferenceManager(context),
            GrantedAuthorization("photos@example.test"), source, hydrator = store,
            isFeatureEnabled = { true }, selectedAccountName = { "photos@example.test" })
        assertEquals(CallPhotoHydrationOutcome.Disabled, manager.hydrateForCall("0722484134"))
        assertEquals(CallPhotoHydrationOutcome.Disabled,
            manager.hydrateForScan("0722484134", "photos@example.test", 10, "token", source))
        assertEquals(0, store.hydrateCalls)
    }

    @Test fun postSelectionRejectsUpscaledThumbnailAndWrongIdentity() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FakePhotoStore(bitmapBytes(96))
        val manager = CallPhotoHydrationManager(context, PreferenceManager(context),
            GrantedAuthorization("photos@example.test"), FixedPhotoSource(bitmapBytes(640)), hydrator = store,
            isFeatureEnabled = { true }, selectedAccountName = { "photos@example.test" })
        assertEquals(CallPhotoHydrationOutcome.NotEligible,
            manager.hydrateForContactUpdate("0722484134", "photos@example.test", 11, 20))
        assertEquals(CallPhotoHydrationOutcome.AlreadyAvailable,
            manager.hydrateForContactUpdate("0722484134", "photos@example.test", 10, 20))
        assertEquals(0, store.hydrateCalls)
    }

    @Test fun postSelectionStillRequiresSelectedAccountAndConsent() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FakePhotoStore(bitmapBytes(96))
        val manager = CallPhotoHydrationManager(context, PreferenceManager(context),
            GrantedAuthorization("photos@example.test"), FixedPhotoSource(bitmapBytes(640)), hydrator = store,
            isFeatureEnabled = { true }, selectedAccountName = { "other@example.test" })
        assertEquals(CallPhotoHydrationOutcome.ConsentRequired,
            manager.hydrateForContactUpdate("0722484134", "photos@example.test", 10, 20))
        assertEquals(0, store.hydrateCalls)
    }

    @Test fun deniedAccessCannotStartPostSelectionHydration() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FakePhotoStore(bitmapBytes(96))
        val manager = CallPhotoHydrationManager(context, PreferenceManager(context),
            GrantedAuthorization("photos@example.test"), FixedPhotoSource(bitmapBytes(640)), hydrator = store,
            isFeatureEnabled = { true }, selectedAccountName = { "photos@example.test" })
        assertEquals(CallPhotoHydrationOutcome.Disabled,
            manager.hydrateForContactUpdate("0722484134", "photos@example.test", 10, 20) { false })
        assertEquals(0, store.hydrateCalls)
    }

    @Test fun approvedGoogleRawCanUpgradeEvenWhenTheContactHasLinkedWhatsAppAndOtherRaws() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888)
        for (y in 0 until 640) for (x in 0 until 640) {
            val fine = if ((x+y)%2==0) 16 else -16
            bitmap.setPixel(x,y,Color.rgb(40+x*160/640+fine,40+y*160/640+fine,100+fine))
        }
        fun bytes(image: Bitmap): ByteArray = ByteArrayOutputStream().use {
            image.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()
        }
        val big = bytes(bitmap)
        val small = bytes(bitmap.scale(96,96,true))
        assertTrue("Matching approved image", sameNullablePhoto(small,big))
        assertTrue("Actual added detail", isMeaningfulPhotoUpgrade(small,big))
        val store = FakePhotoStore(small, rawIds = listOf(20,21,22,23))
        val manager = CallPhotoHydrationManager(context,PreferenceManager(context),
            GrantedAuthorization("photos@example.test"),FixedPhotoSource(big),hydrator=store,
            isFeatureEnabled={true},selectedAccountName={"photos@example.test"})
        assertEquals(CallPhotoHydrationOutcome.Updated,
            manager.hydrateForContactUpdate("0722484134","photos@example.test",10,20))
        assertEquals(1,store.hydrateCalls)
        assertNotNull(store.lastRequest?.approvedReferencePhoto)
        assertTrue(store.lastRequest?.canWrite?.invoke() == true)
        assertEquals(20L,store.lastRequest?.expectedRawContactId)
        bitmap.recycle()
    }

    private fun bitmapBytes(size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(35, 135, 95))
        return ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }
}

private class GrantedAuthorization(private val accountName: String) : GoogleContactsAuthorization {
    override val isAvailable: Boolean = true

    override suspend fun authorize(accountName: String?): GoogleContactsAuthorizationResult =
        GoogleContactsAuthorizationResult.Granted(this.accountName, "test-access-token")

    override fun completeAuthorization(
        data: Intent?,
        requestedAccountName: String?,
    ): GoogleContactsAuthorizationResult = error("Not used")

    override suspend fun revoke(accountName: String): Boolean = true
}

private class FixedPhotoSource(private val bytes: ByteArray) : GooglePeoplePhotoSource {
    override suspend fun loadPhoto(
        accessToken: String,
        phoneNumber: String,
    ): GooglePeoplePhotoResult = GooglePeoplePhotoResult.Found(bytes)
}

private class FakePhotoStore(private val thumbnail: ByteArray, private val existingPhoto: ByteArray? = null,
    private val rawIds: List<Long> = listOf(20L)) : GoogleContactPhotoStore {
    var hydrateCalls = 0
    var lastRequest: GoogleContactPhotoHydrationRequest? = null

    override suspend fun snapshot(
        phoneNumber: String,
        googleAccountName: String,
    ): GoogleContactPhotoSnapshot = snapshotFor(thumbnail = thumbnail, highResolution = existingPhoto,
        photoFileId = if (existingPhoto != null) 42L else null)

    override suspend fun hydrate(
        request: GoogleContactPhotoHydrationRequest,
    ): GoogleContactPhotoHydrationResult {
        hydrateCalls++
        lastRequest = request
        val before = snapshotFor(thumbnail = thumbnail)
        val after = snapshotFor(
            thumbnail = thumbnail,
            highResolution = request.approvedPhoto,
            photoFileId = 42L,
        )
        val info = checkNotNull(request.approvedPhoto.toContactPhotoImageInfo())
        return GoogleContactPhotoHydrationResult.Hydrated(before, after, info, 0)
    }

    private fun snapshotFor(
        thumbnail: ByteArray,
        highResolution: ByteArray? = null,
        photoFileId: Long? = null,
    ) = GoogleContactPhotoSnapshot(
        contactId = 10L,
        rawContactId = 20L,
        photoDataRowId = 30L,
        displayName = "Test Alexandru Butica",
        phoneNumbers = listOf("0722484134"),
        matchingContactIds = listOf(10L),
        rawContactIds = rawIds,
        accountName = "photos@example.test",
        accountType = "com.google",
        photoFileId = photoFileId,
        thumbnail = thumbnail,
        highResolutionPhoto = highResolution,
    )
}

