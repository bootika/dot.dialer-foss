package dev.goodwy.rphone.view.components

import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.expandedListDetails
import dev.goodwy.rphone.controller.util.getSwipeRightAction
import dev.goodwy.rphone.controller.util.getSwipeLeftAction

internal data class ContactListRowPreferences(
    val swipeEnabled: Boolean,
    val swipeRightAction: Int,
    val swipeLeftAction: Int,
    val haptics: Boolean,
    val hapticsStrength: String,
    val hapticsIntensity: Float,
    val contactNameSize: Int,
    val avatarSize: Int,
    val expandedListDetails: Boolean = PreferenceManager.DEFAULT_EXPANDED_LIST_DETAILS,
    val quickActionsEnabled: Boolean = false,
    val quickActionsOnTap: Boolean = false,
)

internal const val CONTACTS_LIST_NAME_SIZE_SP = 16

internal fun contactsListNameSizeSp(preferredSizeSp: Int): Int =
    preferredSizeSp.coerceAtMost(CONTACTS_LIST_NAME_SIZE_SP)

internal fun PreferenceManager.contactListRowPreferences() = ContactListRowPreferences(
    swipeEnabled = getBoolean(PreferenceManager.KEY_SWIPE_TO_CALL, PreferenceManager.DEFAULT_SWIPE_ENABLED),
    swipeRightAction = getSwipeRightAction(),
    swipeLeftAction = getSwipeLeftAction(),
    haptics = getBoolean(PreferenceManager.KEY_APP_HAPTICS, true),
    hapticsStrength = getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
    hapticsIntensity = getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f),
    contactNameSize = getInt(PreferenceManager.KEY_CONTACT_NAME_SIZE_SP, 18).coerceIn(16, 22),
    avatarSize = contactAvatarSizeDp(),
    expandedListDetails = expandedListDetails(),
    quickActionsOnTap = dev.goodwy.rphone.controller.util.resolvedRecentTapAction(getInt(PreferenceManager.KEY_RECENT_TAP_ACTION, -1), false) == dev.goodwy.rphone.controller.util.HOME_TAP_QUICK_ACTIONS,
    quickActionsEnabled = getBoolean(dev.goodwy.rphone.controller.util.HOME_QUICK_ACTION_GESTURE, false),
)
