package dev.goodwy.rphone.controller.util

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GooglePeoplePhotoClientTest {
    @Test
    fun nationalAndInternationalFormsMatchWithoutAcceptingShortSuffixes() {
        assertTrue(numbersMatch("0722 484 134", "+40 722 484 134"))
        assertTrue(numbersMatch("+44 7700 900123", "07700 900123"))
        assertEquals(false, numbersMatch("1234567", "991234567"))
        assertEquals(false, numbersMatch("0722484134", "0722484135"))
    }

    @Test
    fun exactPersonLoadsContactSourcePhotoAtRequestedSize() = runTest {
        val image = pngPixel()
        val transport = RecordingTransport(
            peopleBodies = listOf(
                """
                {
                  "connections": [{
                    "resourceName": "people/123",
                    "phoneNumbers": [{"value": "+40 722 484 134"}],
                    "photos": [
                      {"url": "https://lh3.googleusercontent.com/profile", "default": false,
                       "metadata": {"source": {"type": "PROFILE"}}},
                      {"url": "https://lh3.googleusercontent.com/contact", "default": false,
                       "metadata": {"sourcePrimary": true, "source": {"type": "CONTACT"}}}
                    ]
                  }]
                }
                """.trimIndent(),
            ),
            photo = image,
        )

        val result = GooglePeoplePhotoClient(transport).loadPhoto("token", "0722484134")

        assertTrue(result.toString(), result is GooglePeoplePhotoResult.Found)
        assertTrue(transport.urls.last().startsWith("https://lh3.googleusercontent.com/contact"))
        assertTrue(transport.urls.last().contains("sz=1024"))
        assertEquals(image.toList(), (result as GooglePeoplePhotoResult.Found).bytes.toList())
    }

    @Test
    fun suffixSizedGooglePhotoReplacesSizeWithoutAddingConflictingQuery() = runTest {
        val transport = transportForPhoto("https://lh3.googleusercontent.com/photo=s100-c")

        GooglePeoplePhotoClient(transport).loadPhoto("token", "0722484134")

        assertEquals("https://lh3.googleusercontent.com/photo=s1024-c", transport.urls.last())
    }

    @Test
    fun widthHeightSuffixKeepsCropModifier() = runTest {
        val transport = transportForPhoto("https://lh3.googleusercontent.com/photo=w100-h100-c")

        GooglePeoplePhotoClient(transport).loadPhoto("token", "0722484134")

        assertEquals("https://lh3.googleusercontent.com/photo=s1024-c", transport.urls.last())
    }

    @Test
    fun pathSizedGooglePhotoReplacesPathSegment() = runTest {
        val transport = transportForPhoto("https://lh3.googleusercontent.com/s100/photo.jpg")

        GooglePeoplePhotoClient(transport).loadPhoto("token", "0722484134")

        assertEquals("https://lh3.googleusercontent.com/s1024/photo.jpg", transport.urls.last())
    }

    @Test
    fun querySizedGooglePhotoReplacesExistingSizeWithoutDuplicatingIt() = runTest {
        val transport = transportForPhoto("https://lh3.googleusercontent.com/photo?token=abc&sz=100")

        GooglePeoplePhotoClient(transport).loadPhoto("token", "0722484134")

        assertEquals(
            "https://lh3.googleusercontent.com/photo?token=abc&sz=1024",
            transport.urls.last(),
        )
    }

    @Test
    fun duplicateExactPhoneIsRejectedInsteadOfGuessing() = runTest {
        val body = """
            {"connections": [
              {"resourceName": "people/1", "phoneNumbers": [{"value": "0722484134"}]},
              {"resourceName": "people/2", "phoneNumbers": [{"value": "+40722484134"}]}
            ]}
        """.trimIndent()
        val result = GooglePeoplePhotoClient(
            RecordingTransport(listOf(body), pngPixel()),
        ).loadPhoto("token", "0722484134")

        assertTrue(result is GooglePeoplePhotoResult.Ambiguous)
    }

    @Test
    fun everyPageIsExaminedBeforeReportingNotFound() = runTest {
        val transport = RecordingTransport(
            peopleBodies = listOf(
                """{"connections": [], "nextPageToken": "next"}""",
                """{"connections": [{"resourceName": "people/2", "phoneNumbers": [{"value": "0722484134"}], "photos": [{"url": "https://lh3.googleusercontent.com/photo", "default": false}]}]}""",
            ),
            photo = pngPixel(),
        )

        val result = GooglePeoplePhotoClient(transport).loadPhoto("token", "0722484134")

        assertTrue(result is GooglePeoplePhotoResult.Found)
        assertTrue(transport.urls[1].contains("pageToken=next"))
    }

    @Test
    fun scanSessionReadsConnectionsOnlyOnceAcrossContacts() = runTest {
        val transport = transportForPhoto("https://lh3.googleusercontent.com/photo=s100")
        val source = GooglePeoplePhotoClient(transport, cacheConnections = true)
        assertTrue(source.loadPhoto("token", "0722484134") is GooglePeoplePhotoResult.Found)
        assertTrue(source.loadPhoto("token", "0722484135") is GooglePeoplePhotoResult.NotFound)
        assertTrue(source.loadPhoto("token", "0722484134") is GooglePeoplePhotoResult.Found)
        assertEquals(1, transport.urls.count { it.startsWith("https://people.googleapis.com") })
    }

    private fun transportForPhoto(photoUrl: String) = RecordingTransport(
        peopleBodies = listOf(
            """{"connections":[{"resourceName":"people/123","phoneNumbers":[{"value":"0722484134"}],"photos":[{"url":"$photoUrl","default":false}]}]}""",
        ),
        photo = pngPixel(),
    )

    private fun pngPixel(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(20, 120, 80))
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }
}

private class RecordingTransport(
    private val peopleBodies: List<String>,
    private val photo: ByteArray,
) : PeopleApiTransport {
    val urls = mutableListOf<String>()
    private var peopleIndex = 0

    override fun get(
        url: String,
        accessToken: String?,
        maxBytes: Int,
        requireGoogleImageHost: Boolean,
    ): PeopleHttpResponse {
        urls += url
        return if (url.startsWith("https://people.googleapis.com")) {
            PeopleHttpResponse(200, peopleBodies[peopleIndex++].encodeToByteArray())
        } else {
            PeopleHttpResponse(200, photo)
        }
    }
}
