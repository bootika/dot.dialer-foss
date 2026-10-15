package dev.goodwy.rphone.controller.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Explicit, opt-in device verification for a real Google contact.
 *
 * The test is skipped unless every confirmation argument is supplied. This prevents ordinary
 * connected-test runs from ever mutating a user's contact provider.
 */
@RunWith(AndroidJUnit4::class)
class GoogleContactPhotoHydratorDeviceTest {
    @Test
    fun hydrateExplicitlyApprovedGooglePhoto() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString(ARG_RUN) == "true")

        val phoneNumber = requireNotNull(arguments.getString(ARG_PHONE))
        val accountName = requireNotNull(arguments.getString(ARG_ACCOUNT))
        val photoFileName = requireNotNull(arguments.getString(ARG_PHOTO_FILE))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val approvedPhotoFile = File(context.filesDir, photoFileName)
        assertTrue("Approved photo is missing from target app files", approvedPhotoFile.isFile)
        val approvedPhoto = approvedPhotoFile.readBytes()
        val evidenceDirectory = File(context.filesDir, EVIDENCE_DIRECTORY).apply { mkdirs() }
        approvedPhotoFile.copyTo(File(evidenceDirectory, "approved-source.jpg"), overwrite = true)

        val hydrator = GoogleContactPhotoHydrator(context)
        val before = hydrator.snapshot(phoneNumber, accountName)
        persistSnapshot(evidenceDirectory, "before", before)

        val request = GoogleContactPhotoHydrationRequest(
            phoneNumber = phoneNumber,
            googleAccountName = accountName,
            approvedPhoto = approvedPhoto,
            expectedContactId = before.contactId,
            expectedRawContactId = before.rawContactId,
            approvedReferencePhoto = requireNotNull(before.highResolutionPhoto ?: before.thumbnail),
            canWrite = { before.displayName.startsWith("Test Alexandru Butica") },
        )
        val firstResult = hydrator.hydrate(request)
        val after = when (firstResult) {
            is GoogleContactPhotoHydrationResult.Hydrated -> firstResult.after
            is GoogleContactPhotoHydrationResult.AlreadyHydrated -> firstResult.snapshot
            else -> error("Hydration failed: $firstResult")
        }
        persistSnapshot(evidenceDirectory, "after", after)
        persistResult(evidenceDirectory, firstResult)

        assertEquals(before.contactId, after.contactId)
        assertEquals(before.rawContactId, after.rawContactId)
        assertEquals(before.displayName, after.displayName)
        assertEquals(before.phoneNumbers, after.phoneNumbers)
        assertEquals(before.rawContactIds, after.rawContactIds)
        assertEquals("com.google", after.accountType)
        assertEquals(accountName, after.accountName)
        assertTrue("PHOTO_FILE_ID must be non-null", after.photoFileId != null)
        assertTrue("High-resolution photo must be readable", after.highResolutionPhoto != null)
        assertTrue(
            "High-resolution photo must be larger than the thumbnail",
            requireNotNull(after.highResolutionInfo).width.toLong() *
                requireNotNull(after.highResolutionInfo).height >
                requireNotNull(after.thumbnailInfo).width.toLong() *
                requireNotNull(after.thumbnailInfo).height,
        )

        val secondResult = hydrator.hydrate(request)
        assertTrue(
            "The second run must be idempotent, but was $secondResult",
            secondResult is GoogleContactPhotoHydrationResult.AlreadyHydrated,
        )
        persistResult(evidenceDirectory, secondResult, "idempotency-result.json")
    }

    private fun persistSnapshot(
        directory: File,
        prefix: String,
        snapshot: GoogleContactPhotoSnapshot,
    ) {
        snapshot.thumbnail?.let { File(directory, "$prefix-thumbnail.bin").writeBytes(it) }
        snapshot.highResolutionPhoto?.let {
            File(directory, "$prefix-high-resolution.bin").writeBytes(it)
        }
        File(directory, "$prefix-snapshot.json").writeText(
            JSONObject()
                .put("contactId", snapshot.contactId)
                .put("rawContactId", snapshot.rawContactId)
                .put("photoDataRowId", snapshot.photoDataRowId)
                .put("displayName", snapshot.displayName)
                .put("phoneNumbers", JSONArray(snapshot.phoneNumbers))
                .put("matchingContactIds", JSONArray(snapshot.matchingContactIds))
                .put("rawContactIds", JSONArray(snapshot.rawContactIds))
                .put("accountName", snapshot.accountName)
                .put("accountType", snapshot.accountType)
                .put("photoFileId", snapshot.photoFileId)
                .put("thumbnail", snapshot.thumbnail.describeImage())
                .put("highResolution", snapshot.highResolutionPhoto.describeImage())
                .toString(2),
        )
    }

    private fun persistResult(
        directory: File,
        result: GoogleContactPhotoHydrationResult,
        fileName: String = "hydration-result.json",
    ) {
        val payload = JSONObject().put("resultType", result::class.java.simpleName)
        when (result) {
            is GoogleContactPhotoHydrationResult.Hydrated -> {
                payload.put("photoFileId", result.after.photoFileId)
                payload.put("perceptualHashDistance", result.perceptualHashDistance)
                payload.put("approvedPhoto", result.approvedPhotoInfo.toJson())
                payload.put("highResolution", result.after.highResolutionInfo?.toJson())
            }
            is GoogleContactPhotoHydrationResult.AlreadyHydrated -> {
                payload.put("photoFileId", result.snapshot.photoFileId)
                payload.put("perceptualHashDistance", result.perceptualHashDistance)
            }
            is GoogleContactPhotoHydrationResult.PermissionMissing ->
                payload.put("permissions", JSONArray(result.permissions.toList()))
            is GoogleContactPhotoHydrationResult.Rejected ->
                payload.put("reason", result.reason.name)
            is GoogleContactPhotoHydrationResult.Failed ->
                payload.put("message", result.message)
        }
        File(directory, fileName).writeText(payload.toString(2))
    }

    private fun ByteArray?.describeImage(): Any = if (this == null) {
        JSONObject.NULL
    } else {
        val info = toContactPhotoImageInfo()
        JSONObject()
            .put("bytes", size)
            .put("sha256", sha256())
            .put("width", info?.width)
            .put("height", info?.height)
            .put("perceptualHash", info?.perceptualHash)
    }

    private fun ContactPhotoImageInfo.toJson(): JSONObject = JSONObject()
        .put("bytes", byteCount)
        .put("width", width)
        .put("height", height)
        .put("perceptualHash", perceptualHash)

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val ARG_RUN = "runGoogleContactPhotoHydration"
        const val ARG_PHONE = "contactPhone"
        const val ARG_ACCOUNT = "googleAccountName"
        const val ARG_PHOTO_FILE = "approvedPhotoFile"
        const val EVIDENCE_DIRECTORY = "contact-photo-hydration-evidence"
    }
}
