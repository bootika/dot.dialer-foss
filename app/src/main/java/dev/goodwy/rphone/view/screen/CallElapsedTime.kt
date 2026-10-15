package dev.goodwy.rphone.view.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import dev.goodwy.rphone.controller.util.elapsedTelecomCallSeconds
import dev.goodwy.rphone.controller.util.formatDuration
import kotlinx.coroutines.delay

/** One duration rule for secondary calls and conference participants, observed from their own call. */
@Composable
internal fun rememberCallElapsedSeconds(state: Int, connectTimeMillis: Long): Long? {
    var now by remember(state, connectTimeMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state, connectTimeMillis) {
        if (elapsedTelecomCallSeconds(state, connectTimeMillis, now) != null) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }
    return elapsedTelecomCallSeconds(state, connectTimeMillis, now)
}

/** Reserve the measured tabular text width plus pixel rounding clearance, even for hour timers. */
@Composable
internal fun CallElapsedTimer(elapsed: Long?, tag: String) {
    val text = elapsed?.let(::formatDuration) ?: "—"
    val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val width = remember(text, style, density, measurer) {
        with(density) { (measurer.measure(text, style, softWrap = false, maxLines = 1).size.width + 2).toDp() }
    }
    Text(text, style = style, maxLines = 1, softWrap = false,
        modifier = Modifier.width(width).testTag(tag))
}
