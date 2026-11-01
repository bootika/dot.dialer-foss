package dev.goodwy.rphone.view.screen

import android.telecom.Call
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.CallSimIdentityPreference
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.loadPhoneAccount
import dev.goodwy.rphone.view.components.rememberActiveSimCount

@Composable
internal fun rememberCallScreenPhoneAccount(
    call: Call?, awaitingTelecomAccount: PhoneAccountHandle?, prefs: PreferenceManager,
): PhoneAccount? {
    val context = LocalContext.current
    val activeSimCount = rememberActiveSimCount()
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val alwaysShowSim = remember(version) {
        prefs.getBoolean(CallSimIdentityPreference.KEY, CallSimIdentityPreference.DEFAULT)
    }
    val handle = call?.details?.accountHandle ?: awaitingTelecomAccount
    return remember(context, call, handle, activeSimCount, alwaysShowSim) {
        handle?.let { loadPhoneAccount(context, it) }?.takeIf {
            !it.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) || alwaysShowSim || activeSimCount >= 2
        }
    }
}
