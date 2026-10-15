package dev.goodwy.rphone.view.screen

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

internal fun Modifier.absorbSupplementalIdentityHeight(heightDp: Int): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val heightPx = heightDp.dp.roundToPx().coerceAtMost(placeable.height)
        layout(placeable.width, callIdentityReportedHeight(placeable.height, heightPx)) {
            placeable.placeRelative(x = 0, y = -heightPx)
        }
    }
