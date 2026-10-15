package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.view.theme.accentText

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.goodwy.rphone.R
import kotlin.math.roundToInt

internal enum class HomeTourTarget { Search, Favorites, RecentRow, RecentAvatar, Dialpad }
internal enum class HomeTourDemo { AvatarLongPress, RowTap, RowSwipe, SearchTyping, FavoritesTap, DialpadTap }

internal data class HomeTourStep(
    val target: HomeTourTarget,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val circularSpotlight: Boolean = false,
    val demo: HomeTourDemo,
)

internal fun homeTourSteps(availableTargets: Set<HomeTourTarget>): List<HomeTourStep> = buildList {
    if (HomeTourTarget.RecentAvatar in availableTargets) {
        add(HomeTourStep(HomeTourTarget.RecentAvatar, R.string.ui_tour_contact_title, R.string.ui_tour_contact_description, true, HomeTourDemo.AvatarLongPress))
    }
    if (HomeTourTarget.RecentRow in availableTargets) {
        add(HomeTourStep(HomeTourTarget.RecentRow, R.string.ui_tour_recent_tap_title, R.string.ui_tour_recent_tap_description, demo = HomeTourDemo.RowTap))
        add(HomeTourStep(HomeTourTarget.RecentRow, R.string.ui_tour_swipe_title, R.string.ui_tour_swipe_description, demo = HomeTourDemo.RowSwipe))
    }
    if (HomeTourTarget.Search in availableTargets) {
        add(HomeTourStep(HomeTourTarget.Search, R.string.ui_tour_search_title, R.string.ui_tour_search_description, demo = HomeTourDemo.SearchTyping))
    }
    if (HomeTourTarget.Favorites in availableTargets) {
        add(HomeTourStep(HomeTourTarget.Favorites, R.string.ui_tour_favorites_title, R.string.ui_tour_favorites_description, demo = HomeTourDemo.FavoritesTap))
    }
    if (HomeTourTarget.Dialpad in availableTargets) {
        add(HomeTourStep(HomeTourTarget.Dialpad, R.string.ui_tour_dialpad_title, R.string.ui_tour_dialpad_description, demo = HomeTourDemo.DialpadTap))
    }
}

@Composable
internal fun HomeCoachMarkTour(
    targets: Map<HomeTourTarget, Rect>,
    onComplete: () -> Unit,
) {
    var popupOrigin by remember { mutableStateOf(Offset.Zero) }
    Popup(
        alignment = androidx.compose.ui.Alignment.TopStart,
        onDismissRequest = onComplete,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true,
            dismissOnClickOutside = false, clippingEnabled = false),
    ) {
        Box(Modifier.fillMaxSize().testTag("tour-popup-host")
            .semantics { homeTourPopupOrigin = popupOrigin }
            .homeTourBounds { popupOrigin = it.topLeft }) {
            HomeCoachMarkTourContent(targets.mapValues { it.value.translate(-popupOrigin) }, onComplete)
        }
    }
}

@Composable
internal fun HomeCoachMarkTourContent(
    targets: Map<HomeTourTarget, Rect>,
    onComplete: () -> Unit,
    demoProgress: Float? = null,
) {
    val visibleTargets = targets.filterValues { it.width > 0f && it.height > 0f }
    val steps = remember(visibleTargets.keys) { homeTourSteps(visibleTargets.keys) }
    if (steps.isEmpty()) return
    var currentStep by remember { mutableIntStateOf(0) }
    val safeStep = currentStep.coerceIn(0, steps.lastIndex)
    val step = steps[safeStep]

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val marginPx = with(density) { 12.dp.toPx() }
        val spotlight = targets[step.target]?.let {
            homeTourSpotlight(it, step, Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), marginPx)
        }
        val interactionSource = remember { MutableInteractionSource() }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("tour-spotlight")
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {},
                ),
        ) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            spotlight?.let { rect ->
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    cornerRadius = if (step.circularSpotlight) {
                        CornerRadius(rect.width / 2f, rect.height / 2f)
                    } else {
                        CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    },
                    blendMode = BlendMode.Clear,
                )
            }
        }

        spotlight?.let { rect ->
            HomeTourDemoOverlay(
                demo = step.demo,
                target = rect,
                fixedProgress = demoProgress,
            )
        }

        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val cardWidth = minOf(maxWidth - 32.dp, 360.dp)
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val estimatedHeightPx = with(density) { 190.dp.toPx() }
        val cardHeightPx = cardSize.height.takeIf { it > 0 }?.toFloat() ?: estimatedHeightPx
        val horizontalOffset = ((screenWidthPx - cardWidthPx) / 2f).roundToInt()
        val verticalOffset = coachMarkCardTop(
            target = spotlight,
            screenHeightPx = screenHeightPx,
            cardHeightPx = cardHeightPx,
            marginPx = with(density) { 20.dp.toPx() },
        ).roundToInt()

        Surface(
            modifier = Modifier
                .width(cardWidth)
                .widthIn(max = 360.dp)
                .onSizeChanged { cardSize = it }
                .then(Modifier.offset { IntOffset(horizontalOffset, verticalOffset) })
                .testTag("tour-card"),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.ui_tour_step, safeStep + 1, steps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.accentText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(step.title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(step.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onComplete, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.skip)) }
                    Spacer(Modifier.weight(1f))
                    if (safeStep > 0) {
                        TextButton(onClick = { currentStep-- }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.back)) }
                    }
                    Button(
                        onClick = {
                            if (safeStep == steps.lastIndex) onComplete() else currentStep++
                        },
                    ) {
                        Text(stringResource(if (safeStep == steps.lastIndex) R.string.done else R.string.next))
                    }
                }
            }
        }
    }
}

internal fun tourSwipeOffset(progress: Float): Float = when {
    progress < 0.18f -> 0f
    progress < 0.42f -> -(progress - 0.18f) / 0.24f
    progress < 0.58f -> -1f + ((progress - 0.42f) / 0.16f) * 2f
    progress < 0.82f -> 1f - (progress - 0.58f) / 0.24f
    else -> 0f
}

internal fun tourTypedCharacterCount(progress: Float, textLength: Int): Int =
    when {
        progress < 0.15f -> 0
        progress > 0.82f -> 0
        else -> (((progress - 0.15f) / 0.42f) * textLength)
            .roundToInt()
            .coerceIn(0, textLength)
    }

internal fun coachMarkCardTop(
    target: Rect?,
    screenHeightPx: Float,
    cardHeightPx: Float,
    marginPx: Float,
): Float {
    if (target == null) return ((screenHeightPx - cardHeightPx) / 2f).coerceAtLeast(marginPx)
    val below = target.bottom + marginPx
    val above = target.top - cardHeightPx - marginPx
    return if (below + cardHeightPx <= screenHeightPx - marginPx) below
    else above.coerceAtLeast(marginPx)
}
