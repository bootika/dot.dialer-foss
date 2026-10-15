package dev.goodwy.rphone.view.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Two separate calls join from the left; unlike Swap, there is only one outgoing arrow. */
internal val DotDialerMergeIcon: ImageVector by lazy {
    ImageVector.Builder("DotDialerMergeRight", 29.dp, 29.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = CallControlIconStyle.StrokeWidthDp * 24f / 29f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 5f)
            lineTo(11f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 19f)
            lineTo(11f, 12f)
            moveTo(16f, 8f)
            lineTo(20f, 12f)
            lineTo(16f, 16f)
        }
    }.build()
}
