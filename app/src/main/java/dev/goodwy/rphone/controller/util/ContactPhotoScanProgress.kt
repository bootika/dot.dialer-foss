package dev.goodwy.rphone.controller.util

internal enum class ContactPhotoScanPhase { Preparing, Searching, Complete, Stopped, Failed }

/** Transient per-account search state; never exported or persisted with contact data. */
internal data class ContactPhotoScanProgress(
    val account: String,
    val source: String,
    val phase: ContactPhotoScanPhase = ContactPhotoScanPhase.Preparing,
    val checked: Int = 0,
    val total: Int? = null,
) {
    val running: Boolean get() = phase == ContactPhotoScanPhase.Preparing || phase == ContactPhotoScanPhase.Searching
}
