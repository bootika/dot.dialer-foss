package dev.goodwy.rphone.modal.repository

import android.content.Context
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.PreferenceManager

/** Provider aggregation can change the public ID; retain the primary contact's choices. */
internal suspend fun carryMergedContactPreferences(context: Context, ids: List<String>, mergedId: String) {
    val prefs = PreferenceManager(context)
    val priority = (ids + mergedId).distinct()
    val number = priority.firstNotNullOfOrNull(prefs::getFavoriteNumber)
    val sim = priority.firstNotNullOfOrNull(prefs::getFavoriteSim)
    val email = priority.firstNotNullOfOrNull(prefs::getFavoriteEmail)
    val last = priority.firstNotNullOfOrNull(prefs::getLastUsedNumber)
    prefs.restoreAllPreferences(buildMap {
        number?.let { put("favorite_number_$mergedId",it) }
        sim?.let { put("favorite_sim_$mergedId",it) }
        email?.let { put("favorite_email_$mergedId",it) }
        last?.let { put("last_used_number_$mergedId",it) }
        put(PreferenceManager.KEY_FAVORITES_ORDER,
            prefs.getFavoritesOrder().map { if (it in ids) mergedId else it }.distinct().joinToString(","))
    })
    // Choose before changing pointers, including when Android retains the primary ID.
    val backgroundOwner = priority.firstOrNull { prefs.getContactBackground(it) != null }
    backgroundOwner?.let { CallBackgroundStore.carryBlocking(context,it,mergedId,emptyList()) }
    ids.filter { it != mergedId }.forEach { CallBackgroundStore.clearBlocking(context,it,emptyList()) }
}
