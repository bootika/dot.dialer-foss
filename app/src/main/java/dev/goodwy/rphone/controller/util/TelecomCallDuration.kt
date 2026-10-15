package dev.goodwy.rphone.controller.util

import android.telecom.Call

/** Connected elapsed time, including hold; never fabricate a start time for a ringing call. */
internal fun elapsedTelecomCallSeconds(state: Int, connectTimeMillis: Long, nowMillis: Long): Long? {
    if (state != Call.STATE_ACTIVE && state != Call.STATE_HOLDING) return null
    if (connectTimeMillis <= 0L) return null
    return (nowMillis - connectTimeMillis).coerceAtLeast(0L) / 1_000
}
