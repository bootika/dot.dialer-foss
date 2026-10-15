package io.github.bootika.dotdialer.feature.diagnostics

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DiagnosticReportBlock(text: String, first: Boolean, last: Boolean) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("diagnosticReportBlock"),
        shape = shape.copy(
            topStart = if (first) shape.topStart else CornerSize(0.dp),
            topEnd = if (first) shape.topEnd else CornerSize(0.dp),
            bottomStart = if (last) shape.bottomStart else CornerSize(0.dp),
            bottomEnd = if (last) shape.bottomEnd else CornerSize(0.dp),
        ),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            // A boundary newline starts the next block; don't add a second empty visual line.
            text = if (last) text else text.removeSuffix("\n"),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp,
                top = if (first) 16.dp else 0.dp, bottom = if (last) 16.dp else 0.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
