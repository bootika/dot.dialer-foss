package dev.goodwy.rphone.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.isEmoji
import dev.goodwy.rphone.controller.util.isLetter
import org.koin.compose.koinInject
import java.lang.Math.floorMod
import java.util.Locale
import kotlin.math.abs

internal const val AVATAR_LETTER_SCALE = 0.48f

internal const val DOT_UNKNOWN_CALLER_PATH =
    "M12,12 C14.21,12 16,10.21 16,8 C16,5.79 14.21,4 12,4 C9.79,4 8,5.79 8,8 C8,10.21 9.79,12 12,12 Z " +
        "M12,6 C13.1,6 14,6.9 14,8 C14,9.1 13.1,10 12,10 C10.9,10 10,9.1 10,8 C10,6.9 10.9,6 12,6 Z " +
        "M12,14 C9.33,14 4,15.34 4,18 L4,20 L20,20 L20,18 C20,15.34 14.67,14 12,14 Z " +
        "M6.67,18 C7.4,16.98 9.97,16 12,16 C14.04,16 16.61,16.99 17.34,18 Z"

/** One hollow, modern unknown-caller glyph shared by every Dot Dialer identity surface. */
val DotUnknownCallerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "DotUnknownCaller",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(DOT_UNKNOWN_CALLER_PATH).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}

//private val avatarColors3 = listOf(
//    Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC), Color(0xFF7E57C2),
//    Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF29B6F6), Color(0xFF26C6DA),
//    Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFF9CCC65), Color(0xFFD4E157),
//    Color(0xFFFFEE58), Color(0xFFFFCA28), Color(0xFFFFA726), Color(0xFFFF7043)
//)
//
//private val avatarColors2 = listOf(
//    Color(0xFFFF8C42), Color(0xFFFFB7C5), Color(0xFF1E5631), Color(0xFF4A148C),
//    Color(0xFF90CAF9), Color(0xFFC0CA33), Color(0xFFE1BEE7), Color(0xFFCE93D8),
//    Color(0xFF1A237E), Color(0xFFFFB74D), Color(0xFFF48FB1), Color(0xFFE53935),
//    Color(0xFFE64A19), Color(0xFF3949AB), Color(0xFFA5D6A7), Color(0xFF00BCD4)
//)

// Google Message
internal val vibrantAvatarColors = listOf(
    Color(0xFF00D0EA), Color(0xFFFF5B55), Color(0xFFFFC600), Color(0xFFBC56FF),
    Color(0xFFFF891D), Color(0xFF26BC6D), Color(0xFFFF54BB)
)

internal val modernPastelAvatarColors = listOf(
    // Exact Thunderbird for Android light-theme contact fallback palette, in upstream order.
    // Source commit bff04e4836bf511e167bb9205efeb432a6727e8a:
    // legacy/ui/legacy/src/main/res/values/contact_picture_fallback_colors.xml
    // core/ui/legacy/theme2/common/src/main/res/values/material_colors.xml
    Color(0xFFE57373) to Color.White,
    Color(0xFF9575CD) to Color.White,
    Color(0xFF4FC3F7) to Color.White,
    Color(0xFF81C784) to Color.White,
    Color(0xFFFF8A65) to Color.White,
    Color(0xFF90A4AE) to Color.White,
    Color(0xFFF06292) to Color.White,
    Color(0xFF7986CB) to Color.White,
    Color(0xFF4DD0E1) to Color.White,
    Color(0xFFFFCA28) to Color.White,
    Color(0xFFA1887F) to Color.White,
    Color(0xFFBA68C8) to Color.White,
    Color(0xFF64B5F6) to Color.White,
    Color(0xFF4DB6AC) to Color.White,
    Color(0xFFFFA726) to Color.White,
)

internal val googleContactAvatarBackgroundColor = Color(0xFFFFAFAB)
internal val googleContactAvatarContentColor = Color(0xFF690A08)

internal fun canonicalAvatarColorSeed(seed: String): String {
    val normalized = seed
        .filterNot { Character.getType(it) == Character.FORMAT.toInt() }
        .trim()
    val digits = normalized.filter(Char::isDigit)
    val phoneLike = normalized.isNotEmpty() && normalized.all { character ->
        character.isDigit() || character.isWhitespace() || character in "+-()./"
    }
    return when {
        phoneLike && digits.isNotEmpty() -> digits
        normalized.isNotEmpty() -> normalized.lowercase(Locale.ROOT)
        else -> "unknown_caller"
    }
}

internal fun modernPastelAvatarIndex(seed: String): Int =
    (canonicalAvatarColorSeed(seed).hashCode() and Int.MAX_VALUE) % modernPastelAvatarColors.size

/** Identity colour is independent of whether the UI displays a letter or a generic glyph. */
internal fun avatarIdentityColorSeed(name: String, number: String): String =
    canonicalAvatarColorSeed(name.trim().ifBlank { number.trim() })

internal fun distinctAdjacentAvatarColorIndices(seeds: List<String>): List<Int> {
    // Kept as a compatibility entry point for callers. A contact's colour must depend only on
    // its stable identity, never on the previous row or its current position in a list.
    return seeds.map(::modernPastelAvatarIndex)
}

internal fun modernPastelAvatarColor(
    seed: String,
    colorIndex: Int? = null,
): Pair<Color, Color> = modernPastelAvatarColors[
    colorIndex?.let { floorMod(it, modernPastelAvatarColors.size) }
        ?: modernPastelAvatarIndex(seed)
]

/** Shared by the identity circle and its call-screen colour field. */
internal fun rillAvatarColors(
    name: String,
    appearance: RillAvatarAppearance,
    scheme: ColorScheme,
    avatarColorIndex: Int? = null,
    iconContainerColor: Color? = null,
): Pair<Color, Color> {
    val colorKey = canonicalAvatarColorSeed(name.ifBlank { "unknown_caller" })
    return when {
        iconContainerColor != null -> iconContainerColor.copy(alpha = 0.18f) to iconContainerColor
        else -> modernPastelAvatarColor(colorKey, avatarColorIndex)
    }
}

@Immutable
data class RillAvatarAppearance(
    val showPicture: Boolean,
    val showFirstLetter: Boolean,
    val colorfulAvatars: Boolean,
    val modernPastelAvatars: Boolean,
    val secondaryColorAvatars: Boolean,
    val googleContactColorAvatars: Boolean,
    val avatarFrame: Boolean,
)

val LocalRillAvatarAppearance = staticCompositionLocalOf<RillAvatarAppearance?> { null }

fun PreferenceManager.rillAvatarAppearance(): RillAvatarAppearance = RillAvatarAppearance(
    showPicture = getBoolean(PreferenceManager.KEY_SHOW_PICTURE, true),
    showFirstLetter = getBoolean(PreferenceManager.KEY_SHOW_FIRST_LETTER, true),
    colorfulAvatars = false,
    modernPastelAvatars = true,
    secondaryColorAvatars = false,
    googleContactColorAvatars = false,
    avatarFrame = getBoolean(PreferenceManager.KEY_AVATAR_FRAME, false),
)

internal fun usesMulticolorAvatarPalette(
    modernPastel: Boolean,
    legacyVibrant: Boolean,
    legacyGoogle: Boolean,
): Boolean = modernPastel || legacyVibrant || legacyGoogle

internal fun contactAvatarSizeDp(value: String?): Int = when (value) {
    PreferenceManager.CONTACT_AVATAR_SIZE_SMALL -> 40
    PreferenceManager.CONTACT_AVATAR_SIZE_LARGE -> 52
    else -> 46
}

fun PreferenceManager.contactAvatarSizeDp(): Int = contactAvatarSizeDp(
    getString(
        PreferenceManager.KEY_CONTACT_AVATAR_SIZE,
        PreferenceManager.DEFAULT_CONTACT_AVATAR_SIZE,
    ),
)

@Composable
fun RillAvatar(
    name: String,
    modifier: Modifier = Modifier,
    photoUri: String? = null,
    icon: ImageVector? = null,
    avatarColorIndex: Int? = null,
    /** Optional explicit tint colour for vector icon tiles. */
    iconContainerColor: Color? = null,
    shape: Shape = CircleShape,
    contentSize: Dp? = null,
    photoContentScale: ContentScale = ContentScale.Crop,
    colorSeed: String = name,
) {
    val providedAppearance = LocalRillAvatarAppearance.current
    val context = LocalContext.current
    val callPhotoRevision = LocalCallPhotoRevision.current
    val density = LocalDensity.current
    val appearance = if (providedAppearance != null) {
        providedAppearance
    } else {
        val prefs = koinInject<PreferenceManager>()
        val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
        remember(settingsState) { prefs.rillAvatarAppearance() }
    }

    val hasName  = name.trim().isNotEmpty()
    val isLetter  = name.trim().take(1).isLetter() || name.take(2).isEmoji()
    val (backgroundColor, contentColor) = rillAvatarColors(colorSeed, appearance, MaterialTheme.colorScheme, avatarColorIndex, iconContainerColor)

//    var appeared by remember { mutableStateOf(false) }
//    LaunchedEffect(Unit) { appeared = true }
//    val iconScale by animateFloatAsState(
//        targetValue = if (appeared) 1f else 0.5f,
//        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
//        label = "iconScale"
//    )
//    val iconAlpha by animateFloatAsState(
//        targetValue = if (appeared) 1f else 0f,
//        animationSpec = tween(250),
//        label = "iconAlpha"
//    )

    val containerModifier = modifier
        .then(
            if (appearance.avatarFrame) Modifier
                .drawBehind {
                    val borderWidth = size.width * 0.09f // 9% of the width
                    drawOutline(
                        outline = shape.createOutline(size, layoutDirection, this),
                        color = contentColor,
                        style = Stroke(width = borderWidth)
                    )
                }
            else Modifier
        )
        .background(backgroundColor, shape)
//            .scale(iconScale)
//            .alpha(iconAlpha)
        .clip(shape)

    @Composable
    fun AvatarContent(availableSize: Dp) {
        val letterFontSize  = (availableSize.value * AVATAR_LETTER_SCALE).coerceIn(14f, 72f).sp
        val iconSize        = (availableSize.value * 0.65f).coerceIn(16f, 92f).dp
        val placeholderSize = (availableSize.value * 0.85f).dp
        when {
            appearance.showPicture && !photoUri.isNullOrEmpty() /*&& photoUri != "voicemail://icon"*/ -> {
                val imageSizePx = with(density) { availableSize.roundToPx() }.coerceAtLeast(1)
                val imageRequest = remember(photoUri, imageSizePx, callPhotoRevision) {
                    ImageRequest.Builder(context)
                        .data(photoUri)
                        .whiteContactPhotoBackground(photoUri, callPhotoRevision)
                        .size(imageSizePx)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = photoContentScale
                )
            }
            icon != null -> {
                Icon(imageVector = icon, contentDescription = name, tint = contentColor, modifier = Modifier.size(iconSize))
            }
//            (photoUri == "voicemail://icon" || name.equals(stringResource(R.string.settings_voicemail_title), ignoreCase = true)) -> {
//                Icon(
//                    imageVector = Icons.Rounded.Voicemail,
//                    contentDescription = name, tint = contentColor,
//                    modifier = Modifier.size(iconSize)
//                )
//            }
            appearance.showFirstLetter && hasName && isLetter -> {
                val emoji = name.take(2)
                val letter = if (emoji.isEmoji()) emoji else name.trim().take(1).uppercase()
                Text(
                    text = letter,
                    fontSize = letterFontSize,
//                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    lineHeight = letterFontSize
                )
            }
            else -> {
                Icon(
                    imageVector = DotUnknownCallerIcon,
                    contentDescription = name, tint = contentColor,
                    modifier = Modifier.size(placeholderSize)
                )
            }
        }
    }

    if (contentSize != null) {
        Box(modifier = containerModifier, contentAlignment = Alignment.Center) {
            AvatarContent(contentSize)
        }
    } else {
        BoxWithConstraints(modifier = containerModifier, contentAlignment = Alignment.Center) {
            AvatarContent(maxWidth)
        }
    }
}
