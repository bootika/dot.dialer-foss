package dev.goodwy.rphone.view.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.SocialUtils
import dev.goodwy.rphone.controller.util.preferredCallNumber
import dev.goodwy.rphone.domain.search.DotDialerSearchPayload
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.CallLogFilter
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.CallLogVisual
import io.github.bootika.dotdialer.core.search.SearchDocument
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer

internal fun performHomeAction(
    action: Int,
    context: Context,
    number: String,
    onCall: (String) -> Unit,
    onContact: () -> Unit,
    onDeleteEntry: (() -> Unit)? = null,
) {
    when (dev.goodwy.rphone.controller.util.supportedHomeSwipeAction(action)) {
        PreferenceManager.SWIPE_ACTION_CALL -> onCall(number)
        PreferenceManager.SWIPE_ACTION_MESSAGE -> context.startActivity(
            Intent(Intent.ACTION_VIEW, "sms:$number".toUri()),
        )
        PreferenceManager.SWIPE_ACTION_CONTACT -> onContact()
        PreferenceManager.SWIPE_ACTION_DELETE_ENTRY -> onDeleteEntry?.invoke()
        PreferenceManager.SWIPE_ACTION_WHATSAPP -> SocialUtils.openWhatsApp(context, number)
        PreferenceManager.SWIPE_ACTION_COPY_NUMBER -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", number))
        }
    }
}

internal fun primaryNumber(contact: Contact): String? =
    contact.phoneDetails.firstOrNull { it.isPrimary }?.number
        ?: contact.phoneNumbers.firstOrNull()

internal fun favoriteCallNumber(contact: Contact, savedNumber: String?): String? {
    return preferredCallNumber(contact, savedNumber)
}

internal fun buildContactsByPhone(contacts: List<Contact>): Map<String, Contact> = buildMap {
    contacts.forEach { contact ->
        contact.phoneNumbers.forEach { number ->
            SearchTextNormalizer.phoneIdentityKey(number)?.let { key ->
                val existing = get(key)
                if (existing == null || shouldPreferContactIdentity(contact, existing)) {
                    put(key, contact)
                }
            }
        }
    }
}

/**
 * Uses the Android address-book identity as the canonical presentation when an app-private copy
 * shares the same name and number. This prevents Recents and Contacts from showing two different
 * avatars for what is visibly the same person, while keeping genuinely shared numbers separate.
 */
internal fun canonicalContactPhotoUri(
    contact: Contact,
    contactsByPhone: Map<String, Contact>,
): String? {
    val normalizedName = SearchTextNormalizer.normalize(contact.displayName, foldDiacritics = true)
    val canonical = contact.phoneNumbers.asSequence()
        .mapNotNull(SearchTextNormalizer::phoneIdentityKey)
        .mapNotNull(contactsByPhone::get)
        .firstOrNull { candidate ->
            SearchTextNormalizer.normalize(candidate.displayName, foldDiacritics = true) == normalizedName
        }
    return canonical?.photoUri ?: contact.photoUri
}

private fun shouldPreferContactIdentity(candidate: Contact, existing: Contact): Boolean = when {
    existing.isPrivate && !candidate.isPrivate -> true
    !existing.isPrivate && candidate.isPrivate -> false
    existing.photoUri.isNullOrBlank() && !candidate.photoUri.isNullOrBlank() -> true
    else -> false
}

internal fun resolveCurrentContact(
    call: CallLogEntry,
    contactsById: Map<String, Contact>,
    contactsByPhone: Map<String, Contact>,
): Contact? = call.contactId?.let(contactsById::get)
    ?: SearchTextNormalizer.phoneIdentityKey(call.number)?.let(contactsByPhone::get)

internal data class HomeRecentRow(
    val document: SearchDocument<DotDialerSearchPayload>,
    val call: CallLogEntry,
    val contact: Contact?,
    val visual: CallLogVisual,
    val title: String,
    val formattedTime: String,
)

internal sealed interface HomeContextTarget {
    data class Recent(val call: CallLogEntry, val contact: Contact?) : HomeContextTarget
    data class ContactTarget(val contact: Contact) : HomeContextTarget
    data class Favorite(val contact: Contact) : HomeContextTarget
}

internal enum class ContactNumberMenuAction { Call, Message, WhatsApp, EditBeforeCall, Copy, CopyIdentity }

internal data class PendingContactNumberAction(
    val contact: Contact,
    val action: ContactNumberMenuAction,
)

internal data class PendingCallDeletion(
    val call: CallLogEntry,
    val group: Boolean,
)

@Composable
internal fun HomeMenuAction(
    label: String,
    icon: ImageVector,
    subtitle: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = (if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface).copy(alpha = if (locked) .45f else 1f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (destructive) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeViewContactsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Person, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.view_contacts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

internal fun callMatchesFilter(call: CallLogEntry, filter: CallLogFilter): Boolean = when (filter) {
    CallLogFilter.All, CallLogFilter.Contacts -> true
    CallLogFilter.Missed -> call.type == android.provider.CallLog.Calls.MISSED_TYPE
    CallLogFilter.Incoming -> call.type == android.provider.CallLog.Calls.INCOMING_TYPE
    CallLogFilter.Outgoing -> call.type == android.provider.CallLog.Calls.OUTGOING_TYPE
    CallLogFilter.Rejected -> call.type == android.provider.CallLog.Calls.REJECTED_TYPE
}

internal const val HOME_SEARCH_DEBOUNCE_MILLIS = 100L
internal const val BROWSE_CONTACTS_TITLE_TOP_PADDING_DP = 2
internal const val HOME_RESULT_AVATAR_DP = 46
internal const val HOME_RESULT_VERTICAL_PADDING_DP = 12
