package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccountHandle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.Locale

internal fun reliableContactLookupRegion(countries: List<String?>): String? {
    if (countries.isEmpty()) return null
    val normalized = countries.map { it?.trim()?.uppercase(Locale.ROOT) }
    if (normalized.any { it !in Locale.getISOCountries() }) return null
    return normalized.distinct().singleOrNull()
}

/** A call's account is authoritative; never substitute another SIM, network or UI locale. */
internal fun resolveContactLookupRegion(context: Context, account: PhoneAccountHandle? = null): String? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
        PackageManager.PERMISSION_GRANTED) return null
    return try {
        if (account != null) {
            val telephony = context.getSystemService(TelephonyManager::class.java)
                ?.createForPhoneAccountHandle(account)
            reliableContactLookupRegion(listOf(telephony?.simCountryIso))
        } else {
            val subscriptions = context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList.orEmpty()
            reliableContactLookupRegion(subscriptions.map { it.countryIso })
        }
    } catch (failure: SecurityException) { android.util.Log.w("ContactLookup", "SIM country access unavailable"); null
    } catch (failure: IllegalArgumentException) { android.util.Log.w("ContactLookup", "SIM account unavailable"); null
    } catch (failure: UnsupportedOperationException) { android.util.Log.w("ContactLookup", "SIM country unsupported"); null }
}
