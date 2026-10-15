package dev.goodwy.rphone.controller.util

internal fun PreferenceManager.expandedListDetails(): Boolean = getBoolean(
    PreferenceManager.KEY_EXPANDED_LIST_DETAILS,
    PreferenceManager.DEFAULT_EXPANDED_LIST_DETAILS,
)
