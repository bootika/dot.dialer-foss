package io.github.bootika.dotdialer.core.preferences

/** Defines which preferences may cross the trust boundary of an editable backup file. */
object PreferenceBackupPolicy {
    private val protectedKeys = setOf(
        DotDialerPreferenceDefaults.INSTALLATION_PROFILE_KEY,
        // Device-specific setup progress cannot stand in for grants on a restored installation.
        "permission_setup_reviewed",
        // Entitlements must come only from the corresponding store/provider.
        "is_pro_iap",
        "is_pro_sub",
        "is_pro_foss",
        "is_supporter",

        // Authentication secrets must never be written to a plaintext ZIP backup.
        "app_lock_pin",
        "biometrics_pin",
        "biometrics_password",
    )

    fun isPortable(key: String): Boolean = key !in protectedKeys &&
        key != "authentication_enabled" && !key.startsWith("biometrics_") && !key.startsWith("app_lock_")
}

/** Validates archive paths before a call note is written to app storage. */
object BackupEntryPolicy {
    fun safeNoteFileName(entryName: String): String? {
        if (!entryName.startsWith(NOTES_PREFIX)) return null

        val fileName = entryName.removePrefix(NOTES_PREFIX)
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return null
        if (!fileName.endsWith(".txt", ignoreCase = true)) return null
        return fileName
    }

    private const val NOTES_PREFIX = "notes/"
}
