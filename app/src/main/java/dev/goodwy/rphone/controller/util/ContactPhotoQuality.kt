package dev.goodwy.rphone.controller.util

import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import kotlin.math.max

/** A larger interpolation is not evidence of a better source. Analyse added spatial detail. */
internal fun isMeaningfulPhotoUpgrade(previous: ByteArray, candidate: ByteArray): Boolean {
    val old = previous.toContactPhotoImageInfo() ?: return false
    val next = candidate.toContactPhotoImageInfo() ?: return false
    if (next.width <= old.width || next.height <= old.height ||
        max(next.width, next.height) > 4096 || next.width.toLong() * next.height > 16_777_216) return false
    val analysisEdge = (max(old.width, old.height) * 2).coerceIn(512, 2048)
    val factor = (max(next.width, next.height) / analysisEdge).coerceAtLeast(1)
    val options = BitmapFactory.Options().apply { inSampleSize = Integer.highestOneBit(factor) }
    val high = BitmapFactory.decodeByteArray(candidate, 0, candidate.size, options) ?: return false
    if (high.width <= old.width || high.height <= old.height) { high.recycle(); return false }
    val smallWidth = old.width.coerceAtLeast(1)
    val smallHeight = old.height.coerceAtLeast(1)
    val low = high.scale(smallWidth, smallHeight, true)
    val interpolated = low.scale(high.width, high.height, true)
    return try {
        val original = IntArray(high.width * high.height)
        val reconstructed = IntArray(original.size)
        high.getPixels(original, 0, high.width, 0, 0, high.width, high.height)
        interpolated.getPixels(reconstructed, 0, high.width, 0, 0, high.width, high.height)
        var error = 0L
        val shifts = intArrayOf(0, 8, 16)
        for (i in original.indices) for (shift in shifts) {
            val delta = ((original[i] shr shift) and 255) - ((reconstructed[i] shr shift) and 255)
            error += delta.toLong() * delta
        }
        error / (original.size.toDouble() * 3) > 16.0
    } finally {
        if (interpolated !== low && interpolated !== high) interpolated.recycle()
        if (low !== high) low.recycle()
        high.recycle()
    }
}
