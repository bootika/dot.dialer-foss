package dev.goodwy.rphone.view.screen

import android.util.Log
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.controller.util.selectedPhoneNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Query outgoing rows directly: grouped/filtered Recents can hide the last outgoing event. */
internal fun readLastOutgoingNumber(context: Context): String? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return null
    return try {
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER),
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NUMBER_PRESENTATION} = ?",
            arrayOf(CallLog.Calls.OUTGOING_TYPE.toString(), CallLog.Calls.PRESENTATION_ALLOWED.toString()),
            "${CallLog.Calls.DATE} DESC")?.use { cursor ->
            val column = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            if (column < 0) return@use null
            while (cursor.moveToNext()) selectedPhoneNumber(cursor.getString(column))?.let { return@use it }
            null
        }
    } catch (failure: RuntimeException) {
        Log.w("LastOutgoingCall", "History unavailable: ${failure.javaClass.simpleName}")
        null
    }
}

@Composable
internal fun rememberLastOutgoingFill(number: String, onFill: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentNumber by rememberUpdatedState(number)
    val fill by rememberUpdatedState(onFill)
    var loading by remember { mutableStateOf(false) }
    return {
        if (!loading && currentNumber.isEmpty()) {
            loading = true
            scope.launch {
                try {
                    val latest = withContext(Dispatchers.IO) { readLastOutgoingNumber(context) }
                    if (currentNumber.isEmpty() && latest != null) fill(latest)
                } finally { loading = false }
            }
        }
    }
}
