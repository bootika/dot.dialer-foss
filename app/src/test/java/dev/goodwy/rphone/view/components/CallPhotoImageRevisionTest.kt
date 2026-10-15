package dev.goodwy.rphone.view.components

import android.app.Application
import coil.request.CachePolicy
import coil.request.ImageRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CallPhotoImageRevisionTest {
    @Test fun whitePhotosNeverReuseRawRevisionCacheAndOrdinaryRequestsKeepAutomaticKeys() {
        val context = RuntimeEnvironment.getApplication()
        val uri = "content://com.android.contacts/contacts/12/photo"
        fun white(revision: Int?) = ImageRequest.Builder(context).data(uri)
            .whiteContactPhotoBackground(uri, revision).build()
        val raw = ImageRequest.Builder(context).data(uri).callPhotoRevision(uri, 1).build()
        assertNotEquals(raw.memoryCacheKey, white(1).memoryCacheKey)
        assertNotEquals(white(1).memoryCacheKey, white(2).memoryCacheKey)
        assertEquals(CachePolicy.DISABLED, white(1).diskCachePolicy)
        assertNull(white(null).memoryCacheKey)
        assertEquals(CachePolicy.ENABLED, white(null).diskCachePolicy)
        assertEquals(listOf(WhiteCallPhotoBackground), white(null).transformations)
    }
    @Test fun sameProviderUriGetsNewCallCacheKeyButOrdinaryListsAreUnchanged() {
        val context = RuntimeEnvironment.getApplication()
        val uri = "content://com.android.contacts/contacts/12/photo"
        fun request(revision: Int?) = ImageRequest.Builder(context).data(uri).callPhotoRevision(uri, revision).build()
        assertNull(request(null).memoryCacheKey)
        assertEquals(CachePolicy.ENABLED, request(null).diskCachePolicy)
        assertEquals(uri, request(1).data)
        assertNotEquals(request(1).memoryCacheKey, request(2).memoryCacheKey)
        assertEquals(CachePolicy.DISABLED, request(2).diskCachePolicy)
    }
}
