package dev.goodwy.rphone.controller.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.abs

internal fun supportedContactPhotoSize(info: ContactPhotoImageInfo): Boolean =
    info.width in 1..4096 && info.height in 1..4096 && info.width.toLong()*info.height <= 16_777_216

/** Compare to the provider's downscaled JPEG representation, keeping identity comparisons unchanged. */
internal fun contactPhotoReadbackMatches(approved: ByteArray, stored: ByteArray): Boolean {
    if (approved.size > 8*1024*1024 || stored.size > 8*1024*1024) return false
    val expected = approved.toContactPhotoImageInfo() ?: return false
    val actual = stored.toContactPhotoImageInfo() ?: return false
    if (!supportedContactPhotoSize(expected) || !supportedContactPhotoSize(actual) ||
        actual.width > expected.width || actual.height > expected.height) return false
    // One rounded pixel on either axis is allowed; a crop or changed aspect is not.
    if (abs(actual.width.toLong()*expected.height - actual.height.toLong()*expected.width) >
        maxOf(expected.width,expected.height)) return false
    if (sameNullablePhoto(approved,stored)) return true
    val image = BitmapFactory.decodeByteArray(approved,0,approved.size) ?: return false
    val resized = image.scale(actual.width,actual.height,true)
    return try {
        fun matchesJpeg(bitmap: Bitmap): Boolean = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG,95,it) && sameNullablePhoto(it.toByteArray(),stored)
        }
        if (matchesJpeg(resized)) true
        else if (!image.hasAlpha()) false
        else {
            // Samsung composites transparent notification avatars onto white before JPEG encoding.
            // Compare that representation with the same hash/colour limits; never ignore the opaque content.
            val white = createBitmap(actual.width,actual.height,Bitmap.Config.ARGB_8888)
            try {
                Canvas(white).apply { drawColor(Color.WHITE); drawBitmap(resized,0f,0f,null) }
                matchesJpeg(white)
            } finally { white.recycle() }
        }
    } finally {
        if (resized !== image) resized.recycle()
        image.recycle()
    }
}
