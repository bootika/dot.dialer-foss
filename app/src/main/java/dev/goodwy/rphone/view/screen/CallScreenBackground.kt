package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.goodwy.rphone.view.components.callPhotoRevision
import dev.goodwy.rphone.view.components.whiteContactPhotoBackground
import dev.goodwy.rphone.liquidglass.backdrops.LayerBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.layerBackdrop
import dev.goodwy.rphone.view.theme.RillDurations
import kotlin.math.roundToInt

private const val CircularTopScrimFraction = 0.36f
private const val CircularBottomScrimFraction = 0.54f
// The blurred window contributes colour, not recognisable shapes or text.
internal const val CALL_FROSTED_BACKGROUND_ALPHA = 0.94f

internal fun callBackgroundLayerAlpha(frostedWindow: Boolean): Float =
    if (frostedWindow) CALL_FROSTED_BACKGROUND_ALPHA else 1f

private fun Modifier.featherFullWidthCallPhoto(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    CALL_PHOTO_EDGE_FEATHER_FRACTION to Color.White,
                    (1f - CALL_PHOTO_EDGE_FEATHER_FRACTION) to Color.White,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

@Composable
internal fun ExpressiveBackground(
    photoUri: String?,
    avatarFallbackColor: Color = MaterialTheme.colorScheme.primaryContainer,
    conferenceAvatarSources: List<CallMistSource>? = null,
    useAvatarPhoto: Boolean = true,
    backgroundUri: String? = null,
    liquidGlassBackdrop: LayerBackdrop,
    hideGradient: Boolean,
    fullPhoto: Boolean = false,
    clearZoneCenterDp: Float? = null,
    fullPhotoOffsetDp: Float = 0f,
    frostedWindow: Boolean = false,
    ambientDroplets: AmbientDropletRenderConfig = AmbientDropletRenderConfig(),
    ambientMiddleZone: AmbientDropletMiddleZone? = null,
    onPhotoLongClick: ((Offset) -> Unit)? = null,
    photoLongClickLabel: String? = null,
    circularAvatarVisible: Boolean = true,
    customGlassState: DialpadEffectState = DialpadEffectState(false, io.github.bootika.dotdialer.core.preferences.DialpadGlassPolicy.DEFAULT_STRENGTH_PERCENT, 20f),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val screenWidthDp = (windowSize.width / density.density).roundToInt()
    val screenHeightDp = (windowSize.height / density.density).roundToInt()
    val scheme = MaterialTheme.colorScheme

    val customUri = backgroundUri.takeIf { customCallBackgroundAllowed(fullPhoto, circularAvatarVisible, conferenceAvatarSources != null) }
    var customFailed by remember(customUri) { mutableStateOf(false) }

    val request = remember(customUri, context) {
        if (customUri.isNullOrEmpty()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(customUri)
                .crossfade(RillDurations.Long1)
            .build()
        }
    }

    val customVisible = request != null && !customFailed
    val useFullPhoto = fullPhoto && !photoUri.isNullOrEmpty() && conferenceAvatarSources == null
    val surface = if (!useFullPhoto && !customVisible) callMistSurface(scheme.surface) else scheme.surface
    val photoRevision = dev.goodwy.rphone.view.components.LocalCallPhotoRevision.current
    var retainedPhotoCacheKey by remember(photoUri,useFullPhoto) { mutableStateOf<coil.memory.MemoryCache.Key?>(null) }
    val photoRequest = remember(photoUri, photoRevision, context,useFullPhoto) {
        photoUri?.let { uri -> ImageRequest.Builder(context).data(uri).callPhotoRevision(uri, photoRevision)
            .apply { if (useFullPhoto) whiteContactPhotoBackground(uri, photoRevision ?: 0) }
            .placeholderMemoryCacheKey(retainedPhotoCacheKey).build() }
    }
    val activeModel: Any? = if (customVisible) request else photoRequest
    val imageVisible = activeModel != null
    var sourceAspectRatio by remember(photoUri) { mutableStateOf<Float?>(null) }
    var photoPositionInRoot by remember(activeModel) { mutableStateOf(Offset.Zero) }
    var photoSize by remember(activeModel) { mutableStateOf(IntSize.Zero) }
    val useFullWidthPanel = useFullPhoto && usesFullWidthPhotoPanel(sourceAspectRatio ?: 1f)
    val panelAspectRatio = callPhotoPanelAspectRatio(sourceAspectRatio ?: 1f)
    val panelTopInset = remember(
        screenWidthDp,
        screenHeightDp,
        panelAspectRatio,
        clearZoneCenterDp,
    ) {
        callPhotoPanelTopInsetDp(
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            panelAspectRatio = panelAspectRatio,
            clearZoneCenterDp = clearZoneCenterDp,
        ).dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Artistic blending needs the background and droplets in the same isolated layer.
            // Without this, some GPU pipelines composite the Canvas independently and the
            // droplets look like ordinary translucent white circles (or disappear entirely).
            .graphicsLayer {
                compositingStrategy = CALL_AMBIENT_COMPOSITING_STRATEGY
            }
            .layerBackdrop(liquidGlassBackdrop)
            // Apply translucency only to the glass tint. Caller photos and the ambient artwork
            // remain app content and must not leak the previous screen through their pixels.
            .background(surface.copy(alpha = callBackgroundLayerAlpha(frostedWindow))),
    ) {
        if (imageVisible && useFullPhoto) {
            // The blurred echo carries the photo colour to both vertical edges of the screen.
            // The sharp layer below spans the entire width, so there is no side feather.
            AsyncImage(
                model = activeModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("callFullPhotoEcho")
                    .blur(48.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.56f,
            )

            val sharpModifier = if (useFullWidthPanel) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(panelAspectRatio)
                    .align(Alignment.TopCenter)
                    .offset(y = panelTopInset)
                    .featherFullWidthCallPhoto()
            } else {
                Modifier.fillMaxSize()
            }
            AsyncImage(
                model = activeModel,
                contentDescription = null,
                modifier = sharpModifier
                    .offset(y = fullPhotoOffsetDp.dp)
                    .testTag("callFullPhotoSharp")
                    .then(
                    if (onPhotoLongClick != null) {
                        Modifier
                            .onGloballyPositioned { coordinates ->
                                photoPositionInRoot = coordinates.positionInRoot()
                                photoSize = coordinates.size
                            }
                            .pointerInput(onPhotoLongClick) {
                                detectTapGestures(
                                    onLongPress = { localPosition ->
                                        onPhotoLongClick(photoPositionInRoot + localPosition)
                                    },
                                )
                            }
                            .semantics {
                                onLongClick(label = photoLongClickLabel) {
                                    onPhotoLongClick(
                                        photoPositionInRoot + Offset(
                                            photoSize.width / 2f,
                                            photoSize.height / 2f,
                                        ),
                                    )
                                    true
                                }
                            }
                    } else {
                        Modifier
                    },
                ),
                onSuccess = { state ->
                    if (customVisible) customFailed = false
                    retainedPhotoCacheKey = state.result.memoryCacheKey
                    val drawable = state.result.drawable
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                        sourceAspectRatio = drawable.intrinsicWidth.toFloat() /
                            drawable.intrinsicHeight.toFloat()
                    }
                },
                onError = { if (customVisible) customFailed = true },
                contentScale = ContentScale.Crop,
                alignment = if (useFullWidthPanel) Alignment.Center else Alignment.TopCenter,
                alpha = 0.97f,
            )
        } else if (customVisible) {
            CustomCallBackgroundGlass(
                request = request,
                state = customGlassState,
                onError = { customFailed = true },
            )
        } else if (!conferenceAvatarSources.isNullOrEmpty()) {
            CallConferenceMist(conferenceAvatarSources, ambientMiddleZone)
        } else {
            CallAvatarMist(if (useAvatarPhoto && conferenceAvatarSources == null) photoUri else null, avatarFallbackColor, ambientMiddleZone)
        }

        if (imageVisible && (useFullPhoto || customVisible)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(
                        if (useFullPhoto) CALL_PHOTO_TOP_SCRIM_FRACTION
                        else CircularTopScrimFraction
                    )
                    .align(Alignment.TopCenter)
                    .background(
                        if (useFullPhoto) {
                            // Keep the caller identity readable while allowing the photograph's
                            // colour echo to travel visibly through the entire header. The lower
                            // treatment remains unchanged; its longer control-area fade is the
                            // approved reference.
                            Brush.verticalGradient(
                                0f to surface.copy(alpha = 0.92f),
                                0.22f to surface.copy(alpha = 0.86f),
                                0.42f to surface.copy(alpha = 0.70f),
                                0.62f to surface.copy(alpha = 0.34f),
                                0.82f to surface.copy(alpha = 0.10f),
                                1f to surface.copy(alpha = 0f),
                            )
                        } else {
                            Brush.verticalGradient(
                                0f to surface.copy(alpha = 0.88f),
                                0.52f to surface.copy(alpha = 0.60f),
                                1f to surface.copy(alpha = 0f),
                            )
                        }
                    )
            )

            if (!hideGradient) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(
                        if (useFullPhoto) CALL_PHOTO_BOTTOM_SCRIM_FRACTION
                        else CircularBottomScrimFraction
                    )
                    .align(Alignment.BottomCenter)
                    .background(
                        if (useFullPhoto) {
                            Brush.verticalGradient(
                                0f to surface.copy(alpha = 0f),
                                0.22f to surface.copy(alpha = 0.10f),
                                0.42f to surface.copy(alpha = 0.35f),
                                CALL_PHOTO_BOTTOM_FADE_SETTLED_STOP to surface.copy(alpha = 0.82f),
                                0.68f to surface.copy(alpha = 0.95f),
                                1f to surface.copy(alpha = 0.96f),
                            )
                        } else {
                            Brush.verticalGradient(
                                0f to surface.copy(alpha = 0f),
                                0.34f to surface.copy(alpha = 0.42f),
                                0.72f to surface.copy(alpha = 0.88f),
                                1f to surface.copy(alpha = 0.88f),
                            )
                        }
                    )
            )
        }

        if (ambientDroplets.enabled) {
            CallAmbientDroplets(
                hasAvatar = !photoUri.isNullOrEmpty(),
                brightnessPercent = ambientDroplets.brightnessPercent,
                middleZone = ambientMiddleZone,
            )
        }
    }
}
