package dev.goodwy.rphone.controller.util

import android.graphics.BitmapFactory
import androidx.core.graphics.scale

/** Hash + colour: flat unrelated media must not compare equal merely because dHash is zero. */
internal fun sameNullablePhoto(first: ByteArray?, second: ByteArray?): Boolean {
    if (first == null || second == null) return first == null && second == null
    val a = first.toContactPhotoImageInfo() ?: return false
    val b = second.toContactPhotoImageInfo() ?: return false
    if (perceptualHashDistance(a.perceptualHash, b.perceptualHash) > 4) return false
    fun colours(bytes: ByteArray): IntArray? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 64) sample *= 2
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val bitmap = original.scale(16, 16)
        return try { IntArray(256).also { bitmap.getPixels(it, 0, 16, 0, 0, 16, 16) } }
        finally { if (bitmap !== original) bitmap.recycle(); original.recycle() }
    }
    val ca = colours(first) ?: return false; val cb = colours(second) ?: return false
    var distance = 0L
    for (i in ca.indices) for (shift in listOf(0, 8, 16)) {
        val delta = ((ca[i] shr shift) and 255) - ((cb[i] shr shift) and 255)
        distance += delta.toLong() * delta
    }
    return distance / (256.0 * 3) <= 400.0
}
