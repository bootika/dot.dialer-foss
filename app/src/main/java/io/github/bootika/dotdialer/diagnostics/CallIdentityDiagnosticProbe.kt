package io.github.bootika.dotdialer.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import dev.goodwy.rphone.controller.util.currentTelecomState
import io.github.bootika.dotdialer.core.diagnostics.*

/** Metadata only: never export the handle, names, account ID, URI or exception message. */
internal fun diagnosticNumberForm(number: String?, allowed: Boolean): DiagnosticNumberForm {
    if (!allowed) return DiagnosticNumberForm.HIDDEN
    if (number == null) return DiagnosticNumberForm.UNKNOWN
    val compact = number.filterNot { it.isWhitespace() || it in "()-" }
    val digits = compact.removePrefix("+")
    if (digits.length !in 7..15 || digits.any { it !in '0'..'9' }) return DiagnosticNumberForm.OTHER
    return when {
        compact.startsWith("+") -> DiagnosticNumberForm.INTERNATIONAL_PLUS
        compact.startsWith("00") -> DiagnosticNumberForm.INTERNATIONAL_PREFIX
        else -> DiagnosticNumberForm.NATIONAL
    }
}
internal fun diagnosticCallService(packageName: String?): DiagnosticCallService = when(packageName) {
    null -> DiagnosticCallService.UNKNOWN
    "com.android.phone" -> DiagnosticCallService.SYSTEM_TELEPHONY
    "com.whatsapp" -> DiagnosticCallService.WHATSAPP
    "com.whatsapp.w4b" -> DiagnosticCallService.WHATSAPP_BUSINESS
    else -> DiagnosticCallService.OTHER
}
internal fun readCallIdentityDiagnostic(context: Context, call: Call, session: Int): DiagnosticDetail.CallIdentity {
    fun presence(value: String?): DiagnosticPresence = if(value.isNullOrBlank()) DiagnosticPresence.ABSENT else DiagnosticPresence.PRESENT
    return try {
        val details = call.details ?: return unknownIdentity(session)
        val handleAllowed = details.handlePresentation == TelecomManager.PRESENTATION_ALLOWED
        val callerAllowed = details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED
        val contactsAllowed = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val telHandle = details.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        DiagnosticDetail.CallIdentity(session, call.currentTelecomState(), details.callDirection,
            diagnosticNumberForm(telHandle, handleAllowed),
            if(handleAllowed && telHandle != null) PhoneNumberUtils.toaFromString(telHandle) else -1,
            details.handlePresentation, details.callerDisplayNamePresentation,
            if(callerAllowed) presence(details.callerDisplayName) else DiagnosticPresence.UNAVAILABLE,
            if(Build.VERSION.SDK_INT >= 30 && contactsAllowed && handleAllowed) presence(details.contactDisplayName) else DiagnosticPresence.UNAVAILABLE,
            if(Build.VERSION.SDK_INT >= 34 && contactsAllowed && handleAllowed) {
                if(details.contactPhotoUri == null) DiagnosticPresence.ABSENT else DiagnosticPresence.PRESENT
            } else DiagnosticPresence.UNAVAILABLE,
            diagnosticCallService(details.accountHandle?.componentName?.packageName),
            if(details.hasProperty(Call.Details.PROPERTY_SELF_MANAGED)) DiagnosticPresence.PRESENT else DiagnosticPresence.ABSENT, details.creationTimeMillis.takeIf { it > 0 } ?: -1)
    } catch(failure: RuntimeException) { unknownIdentity(session) }
}
private fun unknownIdentity(session: Int) = DiagnosticDetail.CallIdentity(session,-1,-1,
    DiagnosticNumberForm.UNKNOWN,-1,-1,-1,DiagnosticPresence.UNAVAILABLE,DiagnosticPresence.UNAVAILABLE,
    DiagnosticPresence.UNAVAILABLE,DiagnosticCallService.UNKNOWN,DiagnosticPresence.UNAVAILABLE,-1)
