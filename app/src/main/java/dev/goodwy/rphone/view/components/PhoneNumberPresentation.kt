package dev.goodwy.rphone.view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em
import dev.goodwy.rphone.controller.util.PhoneNumberDisplay
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.contactNumberRegion
import org.koin.core.context.GlobalContext

private data class DisplayRegion(val iso: String?)
private val LocalDisplayRegion = androidx.compose.runtime.staticCompositionLocalOf<DisplayRegion?> { null }

/** One preference observer per themed surface, never one observer or SIM lookup per row. */
@Composable
internal fun ProvidePhoneDisplayRegion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { GlobalContext.getOrNull()?.getOrNull<PreferenceManager>() }
    val revision = prefs?.settingsChanged?.collectAsState()?.value
    val region = remember(context, revision) { DisplayRegion(contactNumberRegion(context, prefs)) }
    androidx.compose.runtime.CompositionLocalProvider(LocalDisplayRegion provides region, content = content)
}

@Composable
internal fun phoneDisplayRegion(): String? {
    val provided = LocalDisplayRegion.current
    if (provided != null) return provided.iso
    val context = LocalContext.current
    return remember(context) { contactNumberRegion(context) }
}
@Composable
internal fun phoneDisplayText(value: String): AnnotatedString {
    if (!remember(value) { PhoneNumberDisplay.candidate(value) }) return remember(value) { AnnotatedString(value) }
    val region = phoneDisplayRegion()
    return remember(value, region) { phoneDisplayAnnotated(value, region) }
}

internal fun phoneDisplayAnnotated(value: String, region: String?): AnnotatedString {
    val displayed = PhoneNumberDisplay.format(value, region)
    return buildAnnotatedString {
        append(displayed)
        if (displayed != value || PhoneNumberDisplay.candidate(value)) {
            displayed.forEachIndexed { index, ch ->
                if (ch == ' ') addStyle(SpanStyle(fontSize = PhoneNumberDisplay.GROUP_SPACE_SCALE.em), index, index + 1)
            }
        }
    }
}

@Composable
internal fun phoneDisplayNumbers(values: List<String>): AnnotatedString {
    val region = phoneDisplayRegion()
    return remember(values, region) { buildAnnotatedString {
        values.forEachIndexed { index, value ->
            if (index > 0) append(", ")
            append(phoneDisplayAnnotated(value, region))
        }
    } }
}
