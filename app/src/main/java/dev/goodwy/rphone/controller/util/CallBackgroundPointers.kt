package dev.goodwy.rphone.controller.util

import io.github.bootika.dotdialer.core.license.PlanFeature

/** Maintenance preserves owned pointers at any tier; importing a new background rechecks rights at commit. */
internal suspend fun writeCallBackgroundPointers(
        manager: PreferenceManager,
        contactId: String?,
        keys: List<String>,
        value: String,
        requireAppearance: Boolean = false
    ): Boolean {
        val updates = mutableMapOf<String, String?>()
        if (!contactId.isNullOrBlank() && manager.getContactBackground(contactId) != value) {
            updates[manager.contactBackgroundIdKey(contactId)] = value
        }
        keys.forEach { key ->
            if (manager.getContactBackgroundForNumber(key) != value) {
                updates[manager.contactBackgroundNumberKey(key)] = value
            }
        }
        if (updates.isEmpty()) return !requireAppearance || manager.allowsFeature(PlanFeature.PREMIUM_APPEARANCE)
        return manager.updateContactBackgroundEntriesSuspend(updates,requireAppearance)
    }
