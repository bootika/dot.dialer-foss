package dev.goodwy.rphone.view.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString

/** Small Android clipboard boundary used while Compose's clipboard API is suspend-based. */
internal class ClipboardAccess(private val context: Context) {
    private val manager = context.getSystemService(ClipboardManager::class.java)

    fun getText(): AnnotatedString? {
        val item = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
        return AnnotatedString(item.coerceToText(context).toString())
    }

    fun setText(text: AnnotatedString) {
        manager.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text.text))
    }
}

@Composable
internal fun rememberClipboardAccess(): ClipboardAccess {
    val context = LocalContext.current
    return remember(context) { ClipboardAccess(context.applicationContext) }
}

private const val CLIP_LABEL = "phone number"
