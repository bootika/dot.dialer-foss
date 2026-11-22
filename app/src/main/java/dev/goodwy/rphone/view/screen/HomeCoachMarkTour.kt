package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.view.theme.accentText

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.screen.onboarding.onboardingPrimaryButtonColors
import kotlin.math.roundToInt

internal enum class HomeTourTarget { Search, Favorites, RecentRow, RecentAvatar, Dialpad }
internal enum class HomeTourDemo { AvatarLongPress, RowTap, RowSwipe, SearchTyping, FavoritesTap, DialpadTap }

internal data class HomeTourActions(val tap: String, val swipeRight: String?, val swipeLeft: String?)

internal data class HomeTourStep(
    val target: HomeTourTarget,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val circularSpotlight: Boolean = false,
    val demo: HomeTourDemo,
)

internal fun homeTourSteps(availableTargets: Set<HomeTourTarget>, hasSwipeActions: Boolean = true): List<HomeTourStep> = buildList {
    if (HomeTourTarget.RecentAvatar in availableTargets) {
        add(HomeTourStep(HomeTourTarget.RecentAvatar, R.string.tour_avatar_title, R.string.tour_avatar_short, true, HomeTourDemo.AvatarLongPress))
    }
    if (HomeTourTarget.RecentRow in availableTargets) {
        add(HomeTourStep(HomeTourTarget.RecentRow, R.string.ui_tour_recent_tap_title, R.string.ui_tour_recent_tap_description, demo = HomeTourDemo.RowTap))
        if (hasSwipeActions) {
            add(HomeTourStep(HomeTourTarget.RecentRow, R.string.tour_swipe_title, R.string.ui_tour_swipe_description, demo = HomeTourDemo.RowSwipe))
        }
    }
    if (HomeTourTarget.Search in availableTargets) {
        add(HomeTourStep(HomeTourTarget.Search, R.string.ui_tour_search_title, R.string.tour_search_short, demo = HomeTourDemo.SearchTyping))
    }
    if (HomeTourTarget.Favorites in availableTargets) {
        add(HomeTourStep(HomeTourTarget.Favorites, R.string.ui_tour_favorites_title, R.string.tour_favorites_short, circularSpotlight = true, demo = HomeTourDemo.FavoritesTap))
    }
    if (HomeTourTarget.Dialpad in availableTargets) {
        add(HomeTourStep(HomeTourTarget.Dialpad, R.string.tour_dialpad_title, R.string.tour_dialpad_short, demo = HomeTourDemo.DialpadTap))
    }
}

@Composable
internal fun HomeCoachMarkTour(
    targets: Map<HomeTourTarget, Rect>,
    actions: HomeTourActions? = null,
    onComplete: () -> Unit,
) {
    var popupOrigin by remember { mutableStateOf(Offset.Zero) }
    val tourTitle = stringResource(R.string.ui_tour_title)
    Popup(
        alignment = androidx.compose.ui.Alignment.TopStart,
        onDismissRequest = onComplete,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true,
            dismissOnClickOutside = false, clippingEnabled = false),
    ) {
        Box(Modifier.fillMaxSize().testTag("tour-popup-host")
            .semantics {
                homeTourPopupOrigin = popupOrigin
                paneTitle = tourTitle
            }
            .homeTourBounds { popupOrigin = it.topLeft }) {
            HomeCoachMarkTourContent(targets.mapValues { it.value.translate(-popupOrigin) }, onComplete, actions = actions)
        }
    }
}

@Composable
internal fun HomeCoachMarkTourContent(
    targets: Map<HomeTourTarget, Rect>,
    onComplete: () -> Unit,
    demoProgress: Float? = null,
    actions: HomeTourActions? = null,
) {
    val visibleTargets = targets.filterValues { it.width > 0f && it.height > 0f }
    val steps = remember(visibleTargets.keys, actions) {
        homeTourSteps(visibleTargets.keys,
            hasSwipeActions = actions == null || actions.swipeRight != null || actions.swipeLeft != null)
    }
    if (steps.isEmpty()) return
    var currentStep by remember { mutableIntStateOf(0) }
    val safeStep = currentStep.coerceIn(0, steps.lastIndex)
    val step = steps[safeStep]

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val alignWrappedActions = maxWidth <= 360.dp && density.fontScale >= 1.3f
        val marginPx = with(density) { 3.dp.toPx() }
        val spotlight = targets[step.target]?.let {
            homeTourSpotlight(it, step, Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), marginPx)
        }
        val spotlightRadius = if (step.circularSpotlight) {
            spotlight?.let { minOf(it.width, it.height) / 2f } ?: 0f
        } else with(density) { (if (step.target == HomeTourTarget.Search) 24.dp else 16.dp).toPx() }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("tour-spotlight")
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
        ) {
            drawRect(Color.Black.copy(alpha = 0.51f))
            spotlight?.let { rect ->
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    cornerRadius = CornerRadius(spotlightRadius),
                    blendMode = BlendMode.Clear,
                )
            }
        }

        spotlight?.let { HomeTourDemoOverlay(step.demo, it, spotlightRadius, demoProgress) }
        AnchoredTourCard(spotlight, keepFavoriteCaption = step.demo == HomeTourDemo.FavoritesTap) {
            Surface(
                modifier = Modifier.testTag("tour-card"),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ui_tour_step, safeStep + 1, steps.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val callsOnTap = step.demo == HomeTourDemo.RowTap && actions?.tap == stringResource(R.string.call)
                        val title = when {
                            callsOnTap -> R.string.tour_tap_to_call_title
                            step.demo == HomeTourDemo.RowTap && actions != null -> R.string.setup_call_style_tap_gesture
                            else -> step.title
                        }
                        Text(
                            text = stringResource(title),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (step.demo == HomeTourDemo.RowSwipe && actions != null) {
                            actions.swipeRight?.let { TourSwipeDirection(true, it) }
                            actions.swipeLeft?.let { TourSwipeDirection(false, it) }
                        } else {
                            val description = when {
                                callsOnTap -> stringResource(R.string.tour_tap_to_call_short)
                                step.demo == HomeTourDemo.RowTap && actions != null ->
                                    stringResource(R.string.feature_tip_gesture_action, stringResource(R.string.setup_call_style_tap_gesture), actions.tap)
                                else -> stringResource(step.description)
                            }
                            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (step.demo == HomeTourDemo.RowSwipe) {
                        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    val secondary = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (alignWrappedActions) Arrangement.End else Arrangement.SpaceBetween,
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onComplete, colors = secondary) { Text(stringResource(R.string.tour_skip_action)) }
                        if (safeStep > 0) TextButton(onClick = { currentStep-- }, colors = secondary) {
                            Text(stringResource(R.string.back))
                        }
                        Button(
                            onClick = { if (safeStep == steps.lastIndex) onComplete() else currentStep++ },
                            colors = onboardingPrimaryButtonColors(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(stringResource(if (safeStep == steps.lastIndex) R.string.done else R.string.setup_call_style_continue))
                        }
                    }
                }
            }
        }
    }
}

/** Measure once against available space, with the footer outside the scrolling explanation. */
@Composable
private fun AnchoredTourCard(spotlight: Rect?, keepFavoriteCaption: Boolean, content: @Composable () -> Unit) {
    val color = MaterialTheme.colorScheme.surface
    SubcomposeLayout(Modifier.fillMaxSize()) { bounds ->
        val margin = 16.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val width = minOf(bounds.maxWidth - margin * 2, 360.dp.roundToPx()).coerceAtLeast(1)
        val viewportHeight = bounds.maxHeight
        val availableHeight = (viewportHeight - margin * 2).coerceAtLeast(1)
        val placementTarget = spotlight?.let {
            if (keepFavoriteCaption) Rect(it.left, it.top, it.right, it.bottom + 22.sp.toPx()) else it
        }
        val room = placementTarget?.let {
            maxOf(it.top - gap - margin, viewportHeight - it.bottom - gap - margin).toInt()
        } ?: availableHeight
        // Extreme viewport heights can require overlap; keep enough room for the wrapped footer.
        val heightLimit = room.coerceIn(minOf(120.dp.roundToPx(), availableHeight), availableHeight)
        val card = subcompose("card", content).single().measure(Constraints.fixedWidth(width).copy(maxHeight = heightLimit))
        val x = (bounds.maxWidth - width) / 2
        val y = coachMarkCardTop(placementTarget, viewportHeight.toFloat(), card.height.toFloat(), margin.toFloat(), gap.toFloat())
            .roundToInt()
        val notch = spotlight?.let { target ->
            val pointsUp = y >= target.bottom
            val pointsDown = y + card.height <= target.top
            if (!pointsUp && !pointsDown) return@let null
            val notchWidth = 22.dp.roundToPx()
            val notchHeight = 8.dp.roundToPx()
            val inset = minOf(20.dp.roundToPx(), (width - notchWidth).coerceAtLeast(0) / 2)
            val left = (target.center.x.roundToInt() - notchWidth / 2)
                .coerceIn(x + inset, maxOf(x + inset, x + width - inset - notchWidth))
            val top = if (pointsUp) y - notchHeight + 1 else y + card.height - 1
            val arrow = subcompose("notch") {
                Canvas(Modifier.fillMaxSize()) {
                    val base = if (pointsUp) size.height else 0f
                    val tip = if (pointsUp) 0f else size.height
                    drawPath(Path().apply {
                        moveTo(0f, base)
                        lineTo(size.width / 2f, tip)
                        lineTo(size.width, base)
                        close()
                    }, color)
                }
            }.single().measure(Constraints.fixed(notchWidth, notchHeight))
            Triple(arrow, left, top)
        }
        layout(bounds.maxWidth, viewportHeight) {
            card.place(x, y)
            notch?.let { (arrow, left, top) -> arrow.place(left, top) }
        }
    }
}

@Composable
private fun TourSwipeDirection(right: Boolean, action: String) {
    val direction = stringResource(if (right) R.string.setup_call_style_direction_right else R.string.setup_call_style_direction_left)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (right) Icons.AutoMirrored.Rounded.ArrowForward else Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.accentText)
        Text(stringResource(R.string.feature_tip_gesture_action, direction, action),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun coachMarkCardTop(
    target: Rect?,
    screenHeightPx: Float,
    cardHeightPx: Float,
    marginPx: Float,
    gapPx: Float = marginPx,
): Float {
    val bottomSafe = (screenHeightPx - cardHeightPx - marginPx).coerceAtLeast(marginPx)
    if (target == null) return ((screenHeightPx - cardHeightPx) / 2f).coerceIn(marginPx, bottomSafe)
    val below = target.bottom + gapPx
    val above = target.top - cardHeightPx - gapPx
    if (below <= bottomSafe) return below.coerceAtLeast(marginPx)
    if (above >= marginPx) return above.coerceAtMost(bottomSafe)
    return if (target.center.y < screenHeightPx / 2f) bottomSafe else marginPx
}
