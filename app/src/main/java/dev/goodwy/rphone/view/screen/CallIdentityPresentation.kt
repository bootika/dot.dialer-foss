package dev.goodwy.rphone.view.screen

/** Full-photo fallback appears only after lookup confirms no photograph, never during lookup. */
internal fun showCallIdentityAvatar(
    fullPhoto: Boolean,
    hasPhoto: Boolean,
    showAvatar: Boolean,
    hideWithBackground: Boolean,
    hasBackground: Boolean,
    photoLookupComplete: Boolean = true,
): Boolean = if (fullPhoto) !hasPhoto && photoLookupComplete
else showAvatar && !(hideWithBackground && hasBackground && hasPhoto)
