package dev.goodwy.rphone.view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Raw text never contains the inserted group spaces, including during selection/deletion. */
internal class PhoneNumberVisualTransformation(private val region: String?) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text.any { it !in "0123456789+" }) return TransformedText(text, OffsetMapping.Identity)
        val displayed = phoneDisplayAnnotated(text.text, region)
        if (displayed.text == text.text) return TransformedText(displayed, OffsetMapping.Identity)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var original = 0
                for (i in displayed.indices) {
                    if (displayed[i] == ' ') continue
                    if (original == offset) return i
                    original++
                }
                return displayed.length
            }
            override fun transformedToOriginal(offset: Int): Int =
                displayed.text.take(offset.coerceIn(0, displayed.length)).count { it != ' ' }
        }
        return TransformedText(displayed, mapping)
    }
}

@Composable
internal fun rememberPhoneNumberTransformation(): VisualTransformation {
    val region = phoneDisplayRegion()
    return remember(region) { PhoneNumberVisualTransformation(region) }
}
