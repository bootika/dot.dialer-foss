package dev.goodwy.rphone.controller.util

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log

private const val DIALPAD_TONE_LOG_TAG = "DialpadTonePlayer"

internal fun playDialpadTone(key: String) {
    val toneType = when (key) {
        "0" -> ToneGenerator.TONE_DTMF_0
        "1" -> ToneGenerator.TONE_DTMF_1
        "2" -> ToneGenerator.TONE_DTMF_2
        "3" -> ToneGenerator.TONE_DTMF_3
        "4" -> ToneGenerator.TONE_DTMF_4
        "5" -> ToneGenerator.TONE_DTMF_5
        "6" -> ToneGenerator.TONE_DTMF_6
        "7" -> ToneGenerator.TONE_DTMF_7
        "8" -> ToneGenerator.TONE_DTMF_8
        "9" -> ToneGenerator.TONE_DTMF_9
        "*" -> ToneGenerator.TONE_DTMF_S
        "#" -> ToneGenerator.TONE_DTMF_P
        else -> return
    }

    val generator = try {
        ToneGenerator(AudioManager.STREAM_DTMF, 80)
    } catch (error: RuntimeException) {
        Log.w(DIALPAD_TONE_LOG_TAG, "Unable to create a DTMF tone generator", error)
        return
    }

    try {
        if (!generator.startTone(toneType, 150)) {
            Log.w(DIALPAD_TONE_LOG_TAG, "The audio system declined a DTMF tone")
        }
        Handler(Looper.getMainLooper()).postDelayed({ releaseGenerator(generator) }, 200)
    } catch (error: RuntimeException) {
        releaseGenerator(generator)
        Log.w(DIALPAD_TONE_LOG_TAG, "Unable to play a DTMF tone", error)
    }
}

private fun releaseGenerator(generator: ToneGenerator) {
    try {
        generator.release()
    } catch (error: RuntimeException) {
        Log.w(DIALPAD_TONE_LOG_TAG, "Unable to release a DTMF tone generator", error)
    }
}
