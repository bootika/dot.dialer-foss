package dev.goodwy.rphone.view.screen

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.goodwy.rphone.liquidglass.backdrops.layerBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import io.github.bootika.dotdialer.core.preferences.DialpadGlassPolicy

internal fun customCallBackgroundAllowed(fullPhoto: Boolean, circularAvatarVisible: Boolean, conference: Boolean): Boolean =
    !fullPhoto && circularAvatarVisible && !conference

/** Fixed Glass value for the circular caller background; independent of Dial Pad preferences. */
internal const val CUSTOM_CALL_BACKGROUND_GLASS_STRENGTH = 50
internal fun customCallGlassTintAlpha(): Float =
    DialpadGlassPolicy.effect(true, CUSTOM_CALL_BACKGROUND_GLASS_STRENGTH, supported = true, hasBackdrop = true).fillAlpha
@Composable
internal fun CustomCallBackgroundGlass(request: ImageRequest?, state: DialpadEffectState, onError: () -> Unit) {
    val fixedState = state.copy(strength = CUSTOM_CALL_BACKGROUND_GLASS_STRENGTH)
    key(request?.data) {
        val photoBackdrop = rememberLayerBackdrop()
        val optical = state.glass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocalView.current.isHardwareAccelerated
        Box(Modifier.fillMaxSize().testTag("callCustomBackgroundGlass")) {
            // Only the photograph is sampled; the glass must never sample its own rendered output.
            Box(Modifier.fillMaxSize().layerBackdrop(photoBackdrop).background(MaterialTheme.colorScheme.surface)) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().testTag("callCustomBackgroundImage")
                        .then(if (optical) Modifier else Modifier.blur(state.blurRadius.coerceIn(0f, 100f).dp)),
                    contentScale = ContentScale.Crop,
                    onError = { onError() },
                )
            }
            if (optical) {
                // The lens renderer requires a CornerBasedShape, even for square viewport corners.
                DialpadGlassSurface(fixedState, Modifier.fillMaxSize().testTag("callCustomBackgroundOptical"), RoundedCornerShape(0.dp), photoBackdrop) { }
            } else {
                Box(Modifier.fillMaxSize().background(
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = customCallGlassTintAlpha()),
                ))
            }
        }
    }
}
