package dev.goodwy.rphone.controller

import android.telecom.Call
import android.telecom.CallAudioState
import dev.goodwy.rphone.modal.`interface`.ICallRepository

/** Diagnostics and notification consume the same confirmed snapshot as the call screen. */
internal fun publishCallAudioState(
    repository: ICallRepository,
    record: (CallAudioState?) -> Unit,
    notify: (Call) -> Unit,
) {
    record(repository.audioState.value)
    repository.currentCallSession.value?.call?.let(notify)
}
