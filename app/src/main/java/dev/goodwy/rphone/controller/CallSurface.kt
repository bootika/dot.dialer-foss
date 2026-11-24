package dev.goodwy.rphone.controller

import android.telecom.Call
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun CallSurface(call: Call?, deferredRequestId: String? = null, content: @Composable () -> Unit) {
    // Only the same outgoing request may retain its composition when Telecom attaches.
    val presentationKey = if (call == null) deferredRequestId else directCallRequestId(call) ?: call
    // A translucent starting window must not reveal Home while Telecom supplies its first call.
    Box(Modifier.fillMaxSize().background(if (presentationKey == null) MaterialTheme.colorScheme.surface else Color.Transparent)) {
        key(presentationKey) { content() }
    }
}
