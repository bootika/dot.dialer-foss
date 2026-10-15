package dev.goodwy.rphone.view.components

import androidx.compose.runtime.staticCompositionLocalOf
import coil.request.CachePolicy
import coil.request.ImageRequest

/** Null outside call UI: ordinary lists retain their existing image-cache behavior. */
internal val LocalCallPhotoRevision = staticCompositionLocalOf<Int?> { null }

internal fun ImageRequest.Builder.callPhotoRevision(uri: String?, revision: Int?): ImageRequest.Builder = apply {
    if (uri != null && revision != null) {
        memoryCacheKey("$uri:call-photo:$revision")
        diskCachePolicy(CachePolicy.DISABLED)
    }
}
