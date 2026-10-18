package dev.goodwy.rphone.controller

import android.app.SearchManager
import android.telecom.TelecomManager
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.goodwy.rphone.MainActivity
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.WhatsAppAvatarSyncProcessor
import dev.goodwy.rphone.controller.util.isAlreadyDefaultDialer
import dev.goodwy.rphone.controller.util.makeCall

const val ACTION_VIEW_CONTACT = "dev.goodwy.rphone.ACTION_VIEW_CONTACT"

sealed class NavigationTarget {
    object Recents : NavigationTarget()
    object OngoingCall : NavigationTarget()
    data class Search(val query: String) : NavigationTarget()
    data class Dialpad(val number: String) : NavigationTarget()
    data class ContactDetails(val contactId: String) : NavigationTarget()
    data class ContactEdit(val contactId: String? = null, val initialName: String? = null, val initialPhone: String? = null) : NavigationTarget()
    data class ContactPhotos(val account: String?) : NavigationTarget()
    object ContactManagement : NavigationTarget()
    object ContactUnavailable : NavigationTarget()
}

class MainViewModel(private val prefs: PreferenceManager) : ViewModel() {

    suspend fun resolveNavigationTarget(intent: Intent, context: Context): NavigationTarget? =
        if (intent.action in setOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT) &&
            intent.data?.let(ExternalContactUriResolver::accepts) == true) {
            withContext(Dispatchers.IO) { getNavigationTarget(intent, context) }
        } else getNavigationTarget(intent, context)

    fun getNavigationTarget(intent: Intent, context: Context): NavigationTarget? {
        val data = intent.data
        val action = intent.action ?: return null

        return when (action) {
            Intent.ACTION_PROCESS_TEXT -> {
                if (!prefs.getBoolean(PreferenceManager.KEY_SELECTED_TEXT_DIALER, false) ||
                    !dev.goodwy.rphone.controller.util.SelectedTextDialerIntegration.isEnabled(context)) return null
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty().take(500)
                dev.goodwy.rphone.controller.util.selectedPhoneNumber(text)?.let(NavigationTarget::Dialpad)
                    ?: NavigationTarget.Search(text)
            }
            WhatsAppAvatarSyncProcessor.ACTION_REVIEW_WHATSAPP_AVATARS -> NavigationTarget.ContactPhotos(
                intent.getStringExtra(dev.goodwy.rphone.controller.util.ContactOptimizationNotifications.ACCOUNT_EXTRA))
            "dev.goodwy.rphone.ACTION_VIEW_RECENTS" -> NavigationTarget.Recents
            ACTION_VIEW_CONTACT -> {
                intent.getStringExtra("contact_id")?.let(NavigationTarget::ContactDetails)
            }
            Intent.ACTION_VIEW -> {
                val mimeType = intent.type
                if (mimeType in setOf("vnd.android.cursor.dir/calls", "vnd.android.cursor.item/calls") ||
                    (data?.scheme == "content" && data.authority == "call_log" &&
                        data.pathSegments.firstOrNull() == "calls")) {
                    NavigationTarget.Recents
                } else if (data?.scheme == "tel") {
                    NavigationTarget.Dialpad(data.schemeSpecificPart)
                } else if (data?.let(ExternalContactUriResolver::accepts) == true) {
                    ExternalContactUriResolver.resolve(context.contentResolver, data)?.let(NavigationTarget::ContactDetails)
                        ?: NavigationTarget.ContactUnavailable
                } else if (data == null) {
                    intent.getStringExtra("contact_id")?.let(NavigationTarget::ContactDetails)
                } else null
            }
            Intent.ACTION_DIAL -> {
                when {
                    data?.scheme == "tel" -> NavigationTarget.Dialpad(data.schemeSpecificPart)
                    intent.getBooleanExtra(MainActivity.EXTRA_RETURN_TO_CALL_ON_BACK, false) -> {
                        NavigationTarget.Recents
                    }
                    else -> NavigationTarget.Dialpad("")
                }
            }
            Intent.ACTION_CALL_BUTTON -> {
                val inCall = try { context.getSystemService(TelecomManager::class.java)?.isInCall == true }
                    catch (error: SecurityException) {
                        android.util.Log.w("CallRouting", "Phone state permission is unavailable", error)
                        false
                    }
                if (inCall) NavigationTarget.OngoingCall else NavigationTarget.Dialpad("")
            }
            Intent.ACTION_SEARCH -> NavigationTarget.Search(intent.getStringExtra(SearchManager.QUERY).orEmpty())
            Intent.ACTION_CALL -> {
                if (data?.scheme == "tel") {
                    val number = data.schemeSpecificPart
                    if (isAlreadyDefaultDialer(context)) {
                        makeCall(context, number = number)
                        null
                    } else {
                        NavigationTarget.Dialpad(number)
                    }
                } else null
            }
            Intent.ACTION_INSERT -> {
                val name = intent.getStringExtra(ContactsContract.Intents.Insert.NAME)
                val phone = intent.getStringExtra(ContactsContract.Intents.Insert.PHONE)
                NavigationTarget.ContactEdit(initialName = name, initialPhone = phone)
            }
            Intent.ACTION_EDIT -> {
                when {
                    data?.let(ExternalContactUriResolver::accepts) == true ->
                        ExternalContactUriResolver.resolve(context.contentResolver, data)?.let {
                            NavigationTarget.ContactEdit(contactId = it)
                        } ?: NavigationTarget.ContactUnavailable
                    data == null -> intent.getStringExtra("contact_id")?.let { NavigationTarget.ContactEdit(contactId = it) }
                    else -> null
                }
            }
            else -> null
        }
    }
}
