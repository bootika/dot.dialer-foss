package dev.goodwy.rphone.controller.util

import android.provider.ContactsContract
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly approved single-contact scan. Never run automatically on a user phone. */
@RunWith(AndroidJUnit4::class)
class GooglePhotoHydrationLiveWriteTest {
    @Test fun improvesApprovedPhotoUsingProductionScanAndPreservesIdentity() {
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            assumeTrue(args.getString("runApprovedPhotoWrite") == "true")
            val id = requireNotNull(args.getString("contactId")?.toLongOrNull())
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val numbers = mutableSetOf<String>()
            context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id.toString()), null,
            )?.use { cursor -> while (cursor.moveToNext()) numbers += cursor.getString(0).filter(Char::isDigit) }
            require(numbers.size == 1)
            val phone = numbers.single()
            val prefs = PreferenceManager(context)
            val account = requireNotNull(prefs.getString(GoogleContactPhotoPreferences.ACCOUNT, null))
            val authService = PlatformGoogleContactsAuthorization(context)
            val auth = authService.authorize(account)
            assertTrue(auth is GoogleContactsAuthorizationResult.Granted)
            val token = (auth as GoogleContactsAuthorizationResult.Granted).accessToken
            val hydrator = GoogleContactPhotoHydrator(context)
            val before = hydrator.snapshot(phone, account)
            assertEquals(id, before.contactId)
            val directory = File(context.filesDir, "temporary-photo-rollback/contact-$id-${Instant.now().epochSecond}").apply { mkdirs() }
            val rollback = before.highResolutionPhoto ?: before.thumbnail
            rollback?.let { File(directory, "before-photo.bin").writeBytes(it) }
            File(directory, "manifest.json").writeText(JSONObject()
                .put("codexManagedTemporaryBackup", true).put("createdAt", Instant.now().toString())
                .put("cleanupAfter", Instant.now().plusSeconds(14 * 86400L).toString())
                .put("reason", "Approved photo hydration end-to-end test")
                .put("contactId", id).put("rawContactId", before.rawContactId)
                .put("sha256", rollback?.let { bytes -> MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } })
                .toString(2))
            val source = GooglePeoplePhotoClient(cacheConnections = true)
            val manager = CallPhotoHydrationManager(context, prefs, authService, source)
            val result = withTimeout(60_000L) {
                manager.hydrateForContactUpdate(phone, account, id, before.rawContactId) {
                    before.displayName.startsWith("Test Alexandru Butica")
                }
            }
            assertTrue(result == CallPhotoHydrationOutcome.Updated || result == CallPhotoHydrationOutcome.AlreadyAvailable)
            val after = hydrator.snapshot(phone, account)
            assertEquals(before.contactId, after.contactId)
            assertEquals(before.rawContactId, after.rawContactId)
            assertEquals(before.displayName, after.displayName)
            assertEquals(before.phoneNumbers, after.phoneNumbers)
            assertEquals(before.rawContactIds, after.rawContactIds)
            assertNotNull(after.photoFileId)
            val image = requireNotNull(after.highResolutionInfo)
            assertTrue(image.width >= 512 && image.height >= 512)
            assertEquals(CallPhotoHydrationOutcome.Disabled,
                manager.hydrateForScan(phone, account, id, token, source))
            Log.i("PhotoLiveWriteTest", "Post-approval photo verified; identity unchanged; no automatic legacy scan write")
        }
    }
}
