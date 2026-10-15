package dev.goodwy.rphone.controller.util

import android.provider.ContactsContract
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, read-only check of the already-authorized Google People photo path. */
@RunWith(AndroidJUnit4::class)
class GooglePeoplePhotoLiveReadTest {
    @Test
    fun readsApprovedContactsPhotoWithoutChangingContacts() {
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            assumeTrue(args.getString("runGooglePeoplePhotoLive") == "true")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val phoneNumber = args.getString("contactId")?.toLongOrNull()?.let { contactId ->
                val numbers = mutableSetOf<String>()
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId.toString()),
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        cursor.getString(0)?.filter(Char::isDigit)?.let(numbers::add)
                    }
                }
                require(numbers.size == 1) { "Expected exactly one number for approved contact" }
                numbers.single()
            } ?: requireNotNull(args.getString("contactPhone"))
            val preferences = PreferenceManager(context)
            assertTrue(
                "Google photo feature must be enabled",
                preferences.getBoolean(GoogleContactPhotoPreferences.ENABLED, false),
            )
            val accountName = requireNotNull(
                preferences.getString(GoogleContactPhotoPreferences.ACCOUNT, null),
            )

            val authorization = PlatformGoogleContactsAuthorization(context).authorize(accountName)
            assertTrue(
                "Silent Google authorization failed: ${authorization::class.simpleName}",
                authorization is GoogleContactsAuthorizationResult.Granted,
            )
            val token = (authorization as GoogleContactsAuthorizationResult.Granted).accessToken
            val transport = CapturingTransport()
            val photo = GooglePeoplePhotoClient(transport).loadPhoto(token, phoneNumber)
            assertTrue(
                "Google People photo lookup returned ${photo::class.simpleName}",
                photo is GooglePeoplePhotoResult.Found,
            )
            val image = (photo as GooglePeoplePhotoResult.Found).bytes.toContactPhotoImageInfo()
            assertNotNull("Google returned an unreadable image", image)
            assertTrue("Google photo has no usable dimensions", image!!.width > 0 && image.height > 0)
            val snapshot = GoogleContactPhotoHydrator(context).snapshot(phoneNumber, accountName)
            val local = snapshot.highResolutionInfo ?: snapshot.thumbnailInfo
            val distance = local?.let { perceptualHashDistance(it.perceptualHash, image.perceptualHash) }
            android.util.Log.i(
                "PhotoLiveReadTest",
                "Google=${image.width}x${image.height}; local=${local?.width}x${local?.height}; distance=$distance",
            )
            if (args.getString("diagnoseSizing") == "true") {
                val requestedUrl = requireNotNull(transport.photoUrl)
                val originalUrl = requestedUrl.removeSuffix("&sz=1024").removeSuffix("?sz=1024")
                val uri = Uri.parse(originalUrl)
                val path = uri.encodedPath.orEmpty()
                android.util.Log.i(
                    "PhotoLiveReadTest",
                    "URL sizing: existingSz=${uri.getQueryParameters("sz").size}; " +
                        "suffix=${Regex("=s\\d+").containsMatchIn(path)}; " +
                        "pathSegment=${Regex("/s\\d+(?=/)").containsMatchIn(path)}",
                )

                val queryBuilder = uri.buildUpon().clearQuery()
                uri.queryParameterNames.filter { it != "sz" }.forEach { name ->
                    uri.getQueryParameters(name).forEach { value ->
                        queryBuilder.appendQueryParameter(name, value)
                    }
                }
                val alternatives = linkedMapOf(
                    "query-replaced" to queryBuilder.appendQueryParameter("sz", "1024").build().toString(),
                    "suffix-replaced" to originalUrl.replace(
                        Regex("=s\\d+(?:-[a-z0-9]+)*(?=\\?|$)"), "=s1024",
                    ),
                    "path-segment-replaced" to originalUrl.replace(
                        Regex("/s\\d+(?=/)"), "/s1024",
                    ),
                )
                alternatives.filterValues { it != originalUrl && it != requestedUrl }
                    .forEach { (variant, url) ->
                        val response = runCatching {
                            UrlConnectionPeopleApiTransport().get(
                                url = url,
                                accessToken = null,
                                maxBytes = 8 * 1024 * 1024,
                                requireGoogleImageHost = true,
                            )
                        }.getOrNull()
                        val info = response?.body?.toContactPhotoImageInfo()
                        android.util.Log.i(
                            "PhotoLiveReadTest",
                            "variant=$variant; http=${response?.code}; " +
                                "image=${info?.width}x${info?.height}",
                        )
                    }
            }
        }
    }

    private class CapturingTransport : PeopleApiTransport {
        private val delegate = UrlConnectionPeopleApiTransport()
        var photoUrl: String? = null

        override fun get(
            url: String,
            accessToken: String?,
            maxBytes: Int,
            requireGoogleImageHost: Boolean,
        ): PeopleHttpResponse {
            if (requireGoogleImageHost) photoUrl = url
            return delegate.get(url, accessToken, maxBytes, requireGoogleImageHost)
        }
    }
}
