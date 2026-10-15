package dev.goodwy.rphone.controller

import android.telecom.Call
import android.telecom.TelecomManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import dev.goodwy.rphone.controller.util.currentTelecomState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.isTelecomConference
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CallIdentity(
    val number: String,
    val name: String,
    val company: String?,
    val photoUri: String?,
    val backgroundUri: String?,
    val photoLookupComplete: Boolean = true,
)

internal class CallIdentityResolver(
    private val contactsRepository: IContactsRepository,
) {
    private data class CacheEntry(val identity: CallIdentity, val settingsVersion: Int)

    private val cache = mutableMapOf<Pair<String, android.telecom.PhoneAccountHandle?>, CacheEntry>()

    @Composable
    fun rememberIdentity(
        call: Call, settingsVersion: Int,
        metadata: dev.goodwy.rphone.domain.model.CallerMetadata? = null,
    ): CallIdentity {
        val context = LocalContext.current
        val unknownLabel = stringResource(R.string.label_unknown)
        val conferenceLabel = stringResource(R.string.conference_call)
        val isConference = call.isTelecomConference()
        val number = remember(call, call.details?.handle, call.details?.handlePresentation, isConference) {
            android.net.Uri.decode(notificationCallerNumber(call))
        }
        val cnam = remember(
            call.details?.callerDisplayName,
            call.details?.callerDisplayNamePresentation,
        ) {
            call.details?.callerDisplayName
                ?.takeIf {
                    call.details?.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED
                }
                ?.takeIf(String::isNotBlank)
        }

        val lookupKey = number to call.details?.accountHandle
        val matchingMetadata = metadata?.forLookupRegion(
            dev.goodwy.rphone.controller.util.resolveContactLookupRegion(context, call.details?.accountHandle))?.takeIf { !isConference && number.isNotBlank() && it.number == number }
        val immediatePhoto = matchingMetadata?.photoUri?.takeIf(String::isNotBlank) ?: telecomCallerPhotoUri(call)
        // Revisions refresh data, but must not erase a photograph already shown for this call.
        var identity by remember(call, lookupKey, isConference, unknownLabel, conferenceLabel) {
            val base = if (isConference) {
                CallIdentity(
                    number = number,
                    name = conferenceLabel,
                    company = null,
                    photoUri = null,
                    backgroundUri = null,
                )
            } else {
                cached(lookupKey, settingsVersion) ?: CallIdentity(
                    number = number,
                    name = baseCallIdentityName(
                        isConference = false,
                        conferenceLabel = conferenceLabel,
                        callerDisplayName = cnam,
                        number = number,
                        unknownLabel = unknownLabel,
                    ),
                    company = null,
                    photoUri = null,
                    backgroundUri = null,
                    photoLookupComplete = number.isBlank(),
                )
            }
            mutableStateOf(
                base.copy(
                    photoUri = base.photoUri ?: immediatePhoto,
                    backgroundUri = base.backgroundUri ?: CallBackgroundStore.defaultModel(context),
                ),
            )
        }

        // Service metadata can disappear before the Activity's final frame. Retain accepted imagery.
        SideEffect {
            matchingMetadata?.photoUri?.takeIf(String::isNotBlank)?.let { photo ->
                if (identity.photoUri != photo || !identity.photoLookupComplete) {
                    identity = identity.copy(name = matchingMetadata.name, photoUri = photo, photoLookupComplete = true)
                    cache(lookupKey, identity, settingsVersion)
                }
            }
        }
        LaunchedEffect(call, lookupKey, cnam, isConference, conferenceLabel, settingsVersion, matchingMetadata) {
            if (call.currentTelecomState() in listOf(Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED)) return@LaunchedEffect
            if (isConference) {
                identity = CallIdentity(
                    number = number,
                    name = conferenceLabel,
                    company = null,
                    photoUri = null,
                    backgroundUri = identity.backgroundUri
                        ?: CallBackgroundStore.defaultModelAsync(context),
                )
                return@LaunchedEffect
            }
            val handle = number.ifEmpty { null }
            val handleResult = CallBackgroundStore.resolveResult(context, handle, null)
            val handleBackground = handleResult.uri
            if (handleBackground != null && handleBackground != identity.backgroundUri) {
                identity = identity.copy(backgroundUri = handleBackground)
            }

            val lookup = handle?.let {
                withContext(Dispatchers.IO) {
                    runCatching { contactsRepository.getContactByNumberInRegion(number,
                        dev.goodwy.rphone.controller.util.resolveContactLookupRegion(context, call.details?.accountHandle)) }
                }
            }
            val contact = lookup?.getOrNull()
            val contactFailed = lookup?.isFailure == true
            val contactId = contact?.id?.takeIf(String::isNotBlank)
            val idResult = if (handleBackground == null && contactId != null) {
                CallBackgroundStore.resolveResult(context, handle, contactId)
            } else {
                null
            }

            val resolvedBackground = handleBackground ?: idResult?.uri
            val resolveFailed = handleResult.failed || idResult?.failed == true || contactFailed
            val retainedBackground = identity.backgroundUri.takeIf { resolveFailed }
            val background = resolvedBackground
                ?: retainedBackground
                ?: CallBackgroundStore.defaultModelAsync(context)
            val resolved = CallIdentity(
                number = number,
                name = matchingMetadata?.name ?: contact?.displayName?.takeIf(String::isNotBlank)
                    ?: cnam
                    ?: identity.name.takeIf { contact == null && it.isNotBlank() }
                    ?: number.ifEmpty { unknownLabel },
                company = contact?.company?.takeIf(String::isNotBlank)
                    ?: identity.company.takeIf { contact == null },
                photoUri = matchingMetadata?.photoUri?.takeIf(String::isNotBlank) ?:
                    dev.goodwy.rphone.controller.util.ReadableCallPhotoResolver(context).resolve(
                        contact?.photoUri ?: identity.photoUri, telecomCallerPhotoUri(call)) ?:
                    if (contact == null && !contactFailed) dev.goodwy.rphone.controller.util.UnsavedNumberAvatars.resolve(context, number) else null,
                backgroundUri = background,
            )
            cache(lookupKey, resolved, settingsVersion)
            identity = resolved
        }

        // Service metadata has already completed its local photo lookup. Use it in this
        // composition rather than waiting for the background/contact-detail effect again.
        return if (matchingMetadata != null) {
            identity.copy(name = matchingMetadata.name, photoUri = matchingMetadata.photoUri?.takeIf(String::isNotBlank) ?: identity.photoUri, photoLookupComplete = true)
        } else identity
    }

    private fun cached(number: Pair<String, android.telecom.PhoneAccountHandle?>, settingsVersion: Int): CallIdentity? =
        cache[number]
            ?.takeIf { it.settingsVersion == settingsVersion }
            ?.identity

    private fun cache(number: Pair<String, android.telecom.PhoneAccountHandle?>, identity: CallIdentity, settingsVersion: Int) {
        cache.entries.removeAll { it.value.settingsVersion != settingsVersion }
        if (number.first.isNotEmpty()) cache[number] = CacheEntry(identity, settingsVersion)
    }
}

internal fun baseCallIdentityName(
    isConference: Boolean,
    conferenceLabel: String,
    callerDisplayName: String?,
    number: String,
    unknownLabel: String,
): String = when {
    isConference -> conferenceLabel
    !callerDisplayName.isNullOrBlank() -> callerDisplayName
    number.isNotBlank() -> number
    else -> unknownLabel
}
