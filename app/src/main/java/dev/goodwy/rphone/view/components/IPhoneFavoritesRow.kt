package dev.goodwy.rphone.view.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.theme.color_call_end
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

internal const val FAVORITES_AVATAR_BOOST_DP = 10
internal const val FAVORITE_ITEM_MIN_WIDTH_DP = 70
internal const val FAVORITE_GAP_SCALE = 0.9f
internal const val FAVORITE_ITEM_SPACING_DP = 6 * FAVORITE_GAP_SCALE

internal fun favoriteItemWidthDp(avatarSizeDp: Int, fontScale: Float = 1f): Float {
    val labelWidth = maxOf(FAVORITE_ITEM_MIN_WIDTH_DP, avatarSizeDp + 14, ceil(64f * fontScale).toInt())
    // Tighten only the space around each circle, including extra label room at large font sizes.
    return avatarSizeDp + (labelWidth - avatarSizeDp) * FAVORITE_GAP_SCALE
}

internal fun estimatedVisibleFavoriteItems(
    viewportWidthDp: Int,
    avatarSizeDp: Int,
    horizontalPaddingDp: Int = 16,
    fontScale: Float = 1f,
): Float {
    val availableWidth = viewportWidthDp - horizontalPaddingDp * 2
    val itemWidth = favoriteItemWidthDp(avatarSizeDp, fontScale)
    return (availableWidth + FAVORITE_ITEM_SPACING_DP) /
        (itemWidth + FAVORITE_ITEM_SPACING_DP)
}

@Composable
fun IPhoneFavoritesRow(
    favorites: List<Contact>,
    isEditing: Boolean,
    onAddFirst: () -> Unit,
    onUnfavorite: (Contact) -> Unit,
    onSaveOrder: (List<String>) -> Unit,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    displayOrder: Int,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onDraggingChange: (Boolean) -> Unit = {},
    avatarSize: Dp = 46.dp,
) {
    val favoritesList = remember(favorites) {
        mutableStateListOf<Contact>().apply {
            addAll(favorites)
        }
    }

    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val favoriteIds = remember(favorites) { favorites.map(Contact::id) }
    var hasAlignedInitialFavorites by remember { mutableStateOf(false) }

    val itemWidth = favoriteItemWidthDp(avatarSize.value.roundToInt(), density.fontScale).dp
    val spacing = FAVORITE_ITEM_SPACING_DP.dp
    val itemWidthPx = with(density) { itemWidth.toPx() }
    val spacingPx = with(density) { spacing.toPx() }
    val itemTotalWidthPx = itemWidthPx + spacingPx

    LaunchedEffect(favoriteIds) {
        if (!hasAlignedInitialFavorites && favoriteIds.isNotEmpty()) {
            lazyListState.scrollToItem(index = 0, scrollOffset = 0)
            hasAlignedInitialFavorites = true
        }
    }

    // Keep the infinite transition out of the normal scrolling path. It only exists while the
    // user is explicitly editing favorites.
    val rotation: Float
    val translationY: Float
    if (isEditing) {
        val infiniteTransition = rememberInfiniteTransition(label = "wiggle")
        rotation = infiniteTransition.animateFloat(
            initialValue = -1.2f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(160, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wiggleRotation",
        ).value
        translationY = infiniteTransition.animateFloat(
            initialValue = -0.8f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(140, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wiggleTranslation",
        ).value
    } else {
        rotation = 0f
        translationY = 0f
    }

    LaunchedEffect(draggedIndex, dragOffsetX) {
        if (draggedIndex != -1) {
            val layoutInfo = lazyListState.layoutInfo
            val containerWidth = layoutInfo.viewportEndOffset
            val draggedItemInfo = layoutInfo.visibleItemsInfo.find { it.index == draggedIndex }
            
            if (draggedItemInfo != null) {
                val currentPos = draggedItemInfo.offset + dragOffsetX
                val scrollThreshold = 120f
                
                if (currentPos < scrollThreshold) {
                    scope.launch { lazyListState.animateScrollBy(-500f) }
                } else if (currentPos > containerWidth - itemWidthPx - scrollThreshold) {
                    scope.launch { lazyListState.animateScrollBy(500f) }
                }
            }
        }
    }

    LazyRow(
        state = lazyListState,
        flingBehavior = rememberSnapFlingBehavior(lazyListState),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 3.dp, bottom = 7.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        itemsIndexed(
            items = favoritesList,
            key = { _, contact -> contact.id }
        ) { index, contact ->
            val isDragged = index == draggedIndex
            
            val animatedScale by animateFloatAsState(
                targetValue = if (isDragged) 1.08f else 1.0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "scale"
            )
            
            val animatedElevation by animateDpAsState(
                targetValue = if (isDragged) 16.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "elevation"
            )

            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .wrapContentHeight()
                    // The fix: only animate placement when NOT being dragged to avoid weird jumps
                    .then(if (!isDragged) Modifier.animateItem(
                        placementSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    ) else Modifier)
                    .zIndex(if (isDragged) 100f else 0f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isDragged) {
                                translationX = dragOffsetX
                                scaleX = animatedScale
                                scaleY = animatedScale
                            }
                        }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .graphicsLayer {
                                if (isEditing && !isDragged) {
                                    rotationZ = rotation
                                    this.translationY = translationY * density.density
                                }
                            }
                            .shadow(
                                elevation = animatedElevation,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color.Black.copy(alpha = 0.25f),
                                spotColor = Color.Black.copy(alpha = 0.4f)
                            )
                            .then(
                                if (isEditing) {
                                    Modifier.pointerInput(contact.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                onDraggingChange(true)
                                                draggedIndex = favoritesList.indexOfFirst { it.id == contact.id }
                                                dragOffsetX = 0f
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetX += dragAmount.x

                                                val currentIndex = favoritesList.indexOfFirst { it.id == contact.id }
                                                if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                                val swapThreshold = itemTotalWidthPx * 0.55f

                                                if (dragOffsetX > swapThreshold && currentIndex < favoritesList.lastIndex) {
                                                    val targetIndex = currentIndex + 1
                                                    val item = favoritesList[currentIndex]
                                                    favoritesList.removeAt(currentIndex)
                                                    favoritesList.add(targetIndex, item)
                                                    draggedIndex = targetIndex
                                                    dragOffsetX -= itemTotalWidthPx
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } else if (dragOffsetX < -swapThreshold && currentIndex > 0) {
                                                    val targetIndex = currentIndex - 1
                                                    val item = favoritesList[currentIndex]
                                                    favoritesList.removeAt(currentIndex)
                                                    favoritesList.add(targetIndex, item)
                                                    draggedIndex = targetIndex
                                                    dragOffsetX += itemTotalWidthPx
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            },
                                            onDragEnd = {
                                                onSaveOrder(favoritesList.map { it.id })
                                                draggedIndex = -1
                                                dragOffsetX = 0f
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onDraggingChange(false)
                                            },
                                            onDragCancel = {
                                                draggedIndex = -1
                                                dragOffsetX = 0f
                                                onDraggingChange(false)
                                            },
                                        )
                                    }
                                } else {
                                    Modifier.clickable(
                                        interactionSource = null,
                                        indication = null,
                                        onClick = { onClick(contact) },
                                    )
                                },
                            ),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
//                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        tonalElevation = animatedElevation / 3
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(avatarSize + 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                RillAvatar(
                                    name = contact.displayName,
                                    photoUri = contact.photoUri,
                                    modifier = Modifier
                                        .size(avatarSize)
                                        .circularCombinedClickable(
                                            onClick = { onClick(contact) },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onLongClick(contact)
                                            },
                                        ),
                                    shape = CircleShape
                                )
                            }
                            val displayName = getDisplayName(contact, displayOrder)
                            Text(
                                text = displayName, //contact.displayName,
                                modifier = Modifier
                                    .heightIn(min = 30.dp)
                                    .padding(horizontal = 1.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Floating Delete Button (Fixed & Scaled)
                    if (isEditing) {
                        var appeared by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { appeared = true }
                        val iconScale by animateFloatAsState(
                            targetValue = if (appeared) 1f else 0.5f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "iconScale"
                        )
                        val iconAlpha by animateFloatAsState(
                            targetValue = if (appeared) 1f else 0f,
                            animationSpec = tween(250),
                            label = "iconAlpha"
                        )
                        Surface(
                            onClick = { onUnfavorite(contact) },
                            modifier = Modifier
                                .size(22.dp)
                                .offset(x = (-2).dp, y = (-2).dp)
                                .scale(iconScale)
                                .alpha(iconAlpha)
                                .graphicsLayer {
                                    if (isEditing && !isDragged) {
                                        rotationZ = rotation
                                        this.translationY = translationY * density.density
                                    }
                                },
                            shape = CircleShape,
                            color = color_call_end,
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = stringResource(R.string.remove_from_favorites),
                                tint = Color.White,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
        item(key = "add-favorite") {
            Column(
                modifier = Modifier
                    .width(itemWidth)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onAddFirst,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    onClick = onAddFirst,
                    modifier = Modifier.size(avatarSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_to_favorites),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.add_favorite_short),
                    modifier = Modifier.heightIn(min = 30.dp).padding(top = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun getModernGradient(seed: String): Pair<Color, Color> {
    val gradients = listOf(
        Color(0xFF8E2DE2) to Color(0xFF4A00E0),
        Color(0xFF00c6ff) to Color(0xFF0072ff),
        Color(0xFFf857a6) to Color(0xFFff5858),
        Color(0xFF11998E) to Color(0xFF38EF7D),
        Color(0xFFf7971e) to Color(0xFFffd200),
        Color(0xFFeb3349) to Color(0xFFf45c43),
        Color(0xFF1fa2ff) to Color(0xFF12d8fa),
        Color(0xFF70e1f5) to Color(0xFFffd194)
    )
    return gradients[abs(seed.hashCode()) % gradients.size]
}
