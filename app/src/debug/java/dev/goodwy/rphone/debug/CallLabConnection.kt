package dev.goodwy.rphone.debug

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.os.Build
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.TelecomManager

internal class CallLabConnection(
    number: String,
    name: String,
    private val autoConnect: Boolean,
    quality: String?,
    private val outgoing: Boolean,
) : Connection() {
    private val handler = Handler(Looper.getMainLooper())
    private val autoConnectAction = Runnable {
        if (state == STATE_DIALING) setLabActive()
    }

    init {
        setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), TelecomManager.PRESENTATION_ALLOWED)
        setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED)
        connectionCapabilities = CAPABILITY_HOLD or
            CAPABILITY_SUPPORT_HOLD or
            CAPABILITY_MUTE or
            CAPABILITY_MERGE_CONFERENCE or
            CAPABILITY_DISCONNECT_FROM_CONFERENCE
        setAudioModeIsVoip(false)
        if (quality == CallLabContract.QUALITY_HD_PLUS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            putExtras(Bundle().apply {
                putInt(EXTRA_AUDIO_CODEC, AUDIO_CODEC_EVS_SWB)
            })
        }
        if (outgoing) {
            setDialing()
            if (autoConnect) handler.postDelayed(autoConnectAction, AUTO_CONNECT_DELAY_MS)
        } else {
            setRinging()
        }
    }

    override fun onAnswer() = setLabActive()

    override fun onAnswer(videoState: Int) {
        setVideoState(videoState)
        setLabActive()
    }

    override fun onReject() = disconnectFromLab(DisconnectCause.REJECTED)

    override fun onReject(rejectReason: Int) = disconnectFromLab(DisconnectCause.REJECTED)

    override fun onReject(replyMessage: String?) = disconnectFromLab(DisconnectCause.REJECTED)

    override fun onDisconnect() = disconnectFromLab()

    override fun onAbort() = disconnectFromLab(DisconnectCause.CANCELED)

    override fun onHold() = setLabOnHold()

    override fun onUnhold() = setLabActive()

    override fun onPlayDtmfTone(c: Char) = Unit

    override fun onStopDtmfTone() = Unit

    fun setLabActive() {
        handler.removeCallbacks(autoConnectAction)
        setActive()
        CallLabRegistry.changed()
    }

    fun setLabOnHold() {
        handler.removeCallbacks(autoConnectAction)
        setOnHold()
        CallLabRegistry.changed()
    }

    fun disconnectFromLab(code: Int = DisconnectCause.LOCAL) {
        handler.removeCallbacks(autoConnectAction)
        if (state != STATE_DISCONNECTED) setDisconnected(DisconnectCause(code))
        (conference as? CallLabConference)?.detach(this)
        destroy()
        CallLabRegistry.remove(this)
    }

    companion object {
        private const val AUTO_CONNECT_DELAY_MS = 900L
    }
}
