package dev.goodwy.rphone.view.screen

import android.content.ClipboardManager
import android.util.Log
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** A conservative suggestion, not a claim that the operator accepts the number. */
internal fun clipboardPhoneNumber(text: CharSequence?): String? {
    val input = text?.toString()?.trim()?.takeIf { it.length in 7..128 } ?: return null
    if (input.any { !it.isDigit() && it !in "+ ()-.\u00a0" }) return null
    val compact = buildString {
        input.forEach { char ->
            if (char.isDigit()) append(Character.digit(char, 10))
            else if (char == '+') append(char)
        }
    }
    val digits = compact.removePrefix("+")
    return compact.takeIf { digits.length in 7..15 && digits.all { it in '0'..'9' } }
}

internal fun readClipboardPhoneNumber(manager: ClipboardManager): String? = try {
    // Do not coerce URIs/Intents into text or resolve external clipboard content.
    if (manager.primaryClipDescription?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) null
    else manager.primaryClip?.takeIf {
        it.itemCount == 1 && it.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") != true
    }?.getItemAt(0)?.text?.let(::clipboardPhoneNumber)
} catch (denied: SecurityException) {
    Log.d("DialpadClipboard", "Suggestion unavailable: ${denied.javaClass.simpleName}")
    null
}

internal class DialpadClipboardSuggestionState {
    var number by mutableStateOf<String?>(null)
        private set
    private var dismissed: String? = null

    fun refresh(draftEmpty: Boolean, focused: Boolean, resumed: Boolean, read: () -> String?) {
        if (!draftEmpty || !focused || !resumed) { number = null; return }
        val current = read()
        if (current != null && current != dismissed) dismissed = null
        number = current?.takeUnless { it == dismissed }
    }

    fun dismiss() {
        dismissed = number
        number = null
    }

    fun use(draft: String): String? {
        if (draft.isNotEmpty()) return null
        val selected = number ?: return null
        dismiss()
        return selected
    }
}

@Composable
internal fun rememberDialpadClipboardSuggestion(draft: String): DialpadClipboardSuggestionState {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { DialpadClipboardSuggestionState() }
    val draftEmpty = draft.isEmpty()
    DisposableEffect(view, lifecycle, draftEmpty) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        fun refresh(focused: Boolean = view.hasWindowFocus(), resumed: Boolean = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            state.refresh(draftEmpty, focused, resumed) { readClipboardPhoneNumber(manager) }
        }
        val clipListener = ClipboardManager.OnPrimaryClipChangedListener { refresh() }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { refresh(focused = it) }
        val lifecycleListener = LifecycleEventObserver { _, event ->
            refresh(resumed = event == Lifecycle.Event.ON_RESUME)
        }
        val observer = view.viewTreeObserver
        manager.addPrimaryClipChangedListener(clipListener)
        observer.addOnWindowFocusChangeListener(focusListener)
        lifecycle.addObserver(lifecycleListener)
        refresh()
        onDispose {
            manager.removePrimaryClipChangedListener(clipListener)
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focusListener)
            lifecycle.removeObserver(lifecycleListener)
        }
    }
    return state
}
