package dev.goodwy.rphone.controller

import android.content.Context
import android.provider.CallLog

/** Dates include acknowledged rows, so delayed callbacks cannot revive a seen call. */
internal data class MissedCallLogSnapshot(val allStarts: Set<Long>, val unreadStarts: List<Long>)

internal fun readMissedCallLog(context: Context): MissedCallLogSnapshot? {
    fun query(withRead: Boolean): MissedCallLogSnapshot? {
        val columns = listOf(CallLog.Calls.DATE, CallLog.Calls.NEW) +
            if (withRead) listOf(CallLog.Calls.IS_READ) else emptyList()
        return context.contentResolver.query(CallLog.Calls.CONTENT_URI, columns.toTypedArray(),
            "${CallLog.Calls.TYPE} = ?", arrayOf(CallLog.Calls.MISSED_TYPE.toString()), null)?.use { cursor ->
            val all = mutableSetOf<Long>()
            val unread = mutableListOf<Long>()
            while (cursor.moveToNext()) {
                val date = cursor.getLong(0)
                all.add(date)
                if (cursor.getInt(1) == 1 || (withRead && cursor.getInt(2) == 0)) unread.add(date)
            }
            MissedCallLogSnapshot(all, unread)
        }
    }
    return try { query(true) } catch (_: IllegalArgumentException) {
        try { query(false) } catch (error: RuntimeException) {
            android.util.Log.w("CallNotification", "Missed-call history unavailable (${error.javaClass.simpleName})")
            null
        }
    } catch (error: RuntimeException) {
        android.util.Log.w("CallNotification", "Missed-call history unavailable (${error.javaClass.simpleName})")
        null
    }
}
