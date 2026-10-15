package dev.goodwy.rphone.controller.util

/** Dimensions refer to what the provider can actually store, not the requested download size. */
internal fun optimizerAccepts(policy: ContactPhotoAutomaticPolicy, previous: ContactPhotoImageInfo?,
    next: ContactPhotoImageInfo, maximum: Int?): Boolean {
    if (policy == ContactPhotoAutomaticPolicy.Manual || !supportedContactPhotoSize(next) || minOf(next.width,next.height) < 48) return false
    if (previous == null) return true
    if (policy == ContactPhotoAutomaticPolicy.FillMissing) return false
    if (policy == ContactPhotoAutomaticPolicy.Latest) return true
    if (maximum == null) return false
    val scale = minOf(1.0, maximum.toDouble() / maxOf(next.width, next.height))
    val width = (next.width * scale).toInt(); val height = (next.height * scale).toInt()
    if (width < previous.width || height < previous.height) return false
    return policy == ContactPhotoAutomaticPolicy.Newest ||
        ((previous.width < 512 || previous.height < 512) &&
            width.toLong() * height > previous.width.toLong() * previous.height)
}

internal fun optimizerEffectiveSize(next: ContactPhotoImageInfo, maximum: Int?): Pair<Int, Int> {
    val scale = if (maximum == null) 1.0 else minOf(1.0, maximum.toDouble() / maxOf(next.width, next.height))
    return (next.width * scale).toInt() to (next.height * scale).toInt()
}

internal fun optimizerPhotoChanged(previous: ByteArray?, candidate: ByteArray, maximum: Int?): Boolean {
    if (previous == null) return true
    if (!sameNullablePhoto(previous, candidate) && !contactPhotoReadbackMatches(candidate, previous)) return true
    val old = previous.toContactPhotoImageInfo() ?: return false
    val next = candidate.toContactPhotoImageInfo() ?: return false
    val (width, height) = optimizerEffectiveSize(next, maximum)
    return width > old.width && height > old.height && isMeaningfulPhotoUpgrade(previous, candidate)
}
