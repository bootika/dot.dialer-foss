package dev.goodwy.rphone.liquidglass

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path

internal fun Canvas.clipOutline(outline: Outline, path: Path?) {
    when (outline) {
        is Outline.Rectangle -> clipRect(outline.rect)
        is Outline.Rounded -> {
            val roundedPath = requireNotNull(path) { "Rounded outlines require a reusable path" }
            roundedPath.rewind()
            roundedPath.addRoundRect(outline.roundRect)
            clipPath(roundedPath)
        }

        is Outline.Generic -> clipPath(outline.path)
    }
}
