package dev.goodwy.rphone.view.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation

/** Display-only compositing shared by contact photos. Stored images and exports stay unchanged. */
internal object WhiteCallPhotoBackground : Transformation {
    override val cacheKey = "full-photo-white-v1"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if(!input.hasAlpha()) return input
        return createBitmap(input.width,input.height).also {
            it.eraseColor(Color.WHITE)
            Canvas(it).drawBitmap(input,0f,0f,Paint(Paint.FILTER_BITMAP_FLAG))
            it.setHasAlpha(false)
        }
    }
}

/** Coil's explicit call revision key must also distinguish the composited bitmap. */
internal fun ImageRequest.Builder.whiteContactPhotoBackground(
    uri: String? = null,
    revision: Int? = null,
): ImageRequest.Builder = apply {
    transformations(WhiteCallPhotoBackground)
    callPhotoRevision(uri, revision)
    if (uri != null && revision != null) {
        memoryCacheKey("$uri:call-photo:$revision:${WhiteCallPhotoBackground.cacheKey}")
    }
}

@Composable
internal fun rememberWhiteContactPhotoRequest(data: Any): ImageRequest {
    val context = LocalContext.current
    val revision = LocalCallPhotoRevision.current
    return remember(context, data, revision) {
        ImageRequest.Builder(context).data(data)
            .whiteContactPhotoBackground(data.toString(), revision).build()
    }
}
