package dev.goodwy.rphone.controller.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.get
import androidx.core.graphics.scale

internal fun ByteArray.toContactPhotoImageInfo(): ContactPhotoImageInfo? {
    if (isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 64) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size,
        BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val hash = try {
        bitmap.differenceHash()
    } finally {
        bitmap.recycle()
    }
    return ContactPhotoImageInfo(
        byteCount = size,
        width = bounds.outWidth,
        height = bounds.outHeight,
        perceptualHash = hash,
    )
}

internal fun perceptualHashDistance(first: Long, second: Long): Int =
    (first xor second).countOneBits()

private fun Bitmap.differenceHash(): Long {
    val scaled = scale(9, 8)
    return try {
        var result = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (scaled[x, y].luminance() > scaled[x + 1, y].luminance()) {
                    result = result or (1L shl bit)
                }
                bit++
            }
        }
        result
    } finally {
        if (scaled !== this) scaled.recycle()
    }
}

private fun Int.luminance(): Int {
    val red = this shr 16 and 0xFF
    val green = this shr 8 and 0xFF
    val blue = this and 0xFF
    return red * 299 + green * 587 + blue * 114
}
