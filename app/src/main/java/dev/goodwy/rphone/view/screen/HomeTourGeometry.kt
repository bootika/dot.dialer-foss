package dev.goodwy.rphone.view.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

internal val HomeTourPopupOriginKey = SemanticsPropertyKey<Offset>("HomeTourPopupOrigin")
internal var SemanticsPropertyReceiver.homeTourPopupOrigin by HomeTourPopupOriginKey

/** Screen coordinates are common to the Home owner and the separate Popup window. */
@Composable
internal fun Modifier.homeTourBounds(onBounds: (Rect) -> Unit): Modifier {
    val view = LocalView.current
    return onGloballyPositioned { coordinates ->
        val screen = IntArray(2)
        val window = IntArray(2)
        view.getLocationOnScreen(screen)
        view.getLocationInWindow(window)
        onBounds(coordinates.boundsInWindow().translate(
            Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat()),
        ))
    }
}

internal fun homeTourSpotlight(target: Rect, step: HomeTourStep, viewport: Rect, margin: Float): Rect {
    // Only the avatar and dialpad controls get a narrow ring outside the real target.
    val padding = if (step.circularSpotlight || step.target == HomeTourTarget.Dialpad) margin else 0f
    return Rect(
        (target.left - padding).coerceAtLeast(viewport.left),
        (target.top - padding).coerceAtLeast(viewport.top),
        (target.right + padding).coerceAtMost(viewport.right),
        (target.bottom + padding).coerceAtMost(viewport.bottom),
    )
}
