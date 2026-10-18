package dev.goodwy.rphone.controller.util

import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_RECENT_TAP_ACTION
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_DIRECT_CALL_ON_TAP
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.DEFAULT_DIRECT_CALL_ON_TAP
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_SWIPE_RIGHT_ACTION
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.DEFAULT_SWIPE_RIGHT_ACTION
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_SWIPE_LEFT_ACTION
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.DEFAULT_SWIPE_LEFT_ACTION

/** Persisted IDs are stable: removed actions must never become a different action. */
internal val HOME_ROW_ACTIONS = listOf(
    PreferenceManager.SWIPE_ACTION_NONE,
    PreferenceManager.SWIPE_ACTION_CALL,
    PreferenceManager.SWIPE_ACTION_MESSAGE,
    PreferenceManager.SWIPE_ACTION_WHATSAPP,
    PreferenceManager.SWIPE_ACTION_CONTACT,
    PreferenceManager.SWIPE_ACTION_COPY_NUMBER,
)

internal val HOME_SWIPE_ACTIONS = HOME_ROW_ACTIONS + PreferenceManager.SWIPE_ACTION_DELETE_ENTRY

internal fun supportedHomeSwipeAction(action: Int): Int =
    action.takeIf { it in HOME_SWIPE_ACTIONS } ?: PreferenceManager.SWIPE_ACTION_NONE

/** Deletion targets the contact itself; number-based directions stay disabled without a number. */
internal fun contactSwipeAction(action: Int, hasNumber: Boolean): Int =
    if (hasNumber || action == PreferenceManager.SWIPE_ACTION_DELETE_ENTRY) supportedHomeSwipeAction(action)
    else PreferenceManager.SWIPE_ACTION_NONE

internal fun resolvedRecentTapAction(savedAction: Int, legacyDirectCall: Boolean): Int =
    savedAction.takeIf { it in HOME_ROW_ACTIONS }
        ?: if (legacyDirectCall) PreferenceManager.SWIPE_ACTION_CALL else PreferenceManager.SWIPE_ACTION_CONTACT

fun PreferenceManager.getRecentTapAction(): Int = resolvedRecentTapAction(
    getInt(KEY_RECENT_TAP_ACTION, -1),
    getBoolean(KEY_DIRECT_CALL_ON_TAP, DEFAULT_DIRECT_CALL_ON_TAP),
)

fun PreferenceManager.setRecentTapAction(action: Int) = setInt(KEY_RECENT_TAP_ACTION, resolvedRecentTapAction(action, false))
fun PreferenceManager.getSwipeRightAction(): Int = supportedHomeSwipeAction(getInt(KEY_SWIPE_RIGHT_ACTION, DEFAULT_SWIPE_RIGHT_ACTION))
fun PreferenceManager.setSwipeRightAction(action: Int) = setInt(KEY_SWIPE_RIGHT_ACTION, supportedHomeSwipeAction(action))
fun PreferenceManager.getSwipeLeftAction(): Int = supportedHomeSwipeAction(getInt(KEY_SWIPE_LEFT_ACTION, DEFAULT_SWIPE_LEFT_ACTION))
fun PreferenceManager.setSwipeLeftAction(action: Int) = setInt(KEY_SWIPE_LEFT_ACTION, supportedHomeSwipeAction(action))
