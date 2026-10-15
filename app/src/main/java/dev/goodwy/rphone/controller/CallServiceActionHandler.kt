package dev.goodwy.rphone.controller

import android.content.Intent
import android.provider.Settings
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.ICallRepository

internal fun handleCallServiceAction(
    service: CallService,
    intent: Intent?,
    preferenceManager: PreferenceManager,
    callRepository: ICallRepository,
    launchBiometric: (String) -> Unit,
) {
    when (intent?.action) {
        "ANSWER_CALL" -> {
            val phoneNumber = callRepository.currentCallSession.value
                ?.call?.details?.handle?.schemeSpecificPart
            if (preferenceManager.shouldGateCallWithBiometric(phoneNumber)) {
                launchBiometric("ANSWER")
            } else {
                callRepository.answerCall()
                service.startActivity(
                    Intent(service, CallActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        putExtra("ANSWERED_FROM_NOTIFICATION", true)
                    },
                )
            }
        }

        "DECLINE_CALL" -> {
            val phoneNumber = callRepository.currentCallSession.value
                ?.call?.details?.handle?.schemeSpecificPart
            if (preferenceManager.shouldGateCallWithBiometric(phoneNumber)) {
                launchBiometric("DECLINE")
            } else {
                callRepository.declineCall()
            }
        }

        "TOGGLE_MUTE" -> callRepository.toggleMute()
        "TOGGLE_SPEAKER" -> callRepository.cycleAudioRoute()
        "NOTES_CALL" -> {
            val name = intent.getStringExtra("contact_name") ?: "Unknown"
            val number = intent.getStringExtra("phone_number") ?: ""
            if (Settings.canDrawOverlays(service)) {
                FloatingNotesService.start(service, name, number)
            }
        }
    }
}
