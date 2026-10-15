package dev.goodwy.rphone.view.theme

import android.app.Activity
import android.os.Build
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.PreferenceManager
import org.koin.compose.koinInject

enum class DotAccent(val key: String) {
    Jade("jade"),
    Blue("blue"),
    Violet("violet"),
    Coral("coral"),
}

internal fun resolveAccent(key: String?): DotAccent =
    DotAccent.entries.firstOrNull { it.key == key } ?: DotAccent.Jade

val DotJade = Color(0xFF00BB77)
val DotBlue = Color(0xFF4285F4)
val DotElectricIris = Color(0xFF6857E5)
val DotRuby = Color(0xFFE5485B)
val DotCoral = DotRuby // Legacy symbol/key retained for saved coral selections.
val DotInk = Color(0xFF10231C)
val DotIvory = Color(0xFFF7F5EF)
// #F2E5CA with HSL saturation +0.04 and lightness -0.04, unchanged hue (D37).
val DotCallQualityCream = Color(0xFFF0DEB8)
val DotWhite = Color(0xFFFFFFFF)
val DotError = Color(0xFFC83E4D)
internal val DotDarkBackground = Color(0xFF08100D)
internal val DotDarkCard = Color(0xFF15201C)
internal val DotDarkHeader = Color(0xFF1B2822)

private val DotLightColors = lightColorScheme(
    primary = DotJade,
    onPrimary = DotInk,
    primaryContainer = Color(0xFFC4F4DF),
    onPrimaryContainer = Color(0xFF073B29),
    secondary = Color(0xFF406659),
    onSecondary = DotWhite,
    secondaryContainer = Color(0xFFD9E9E2),
    onSecondaryContainer = DotInk,
    tertiary = Color(0xFF58655F),
    onTertiary = DotWhite,
    tertiaryContainer = Color(0xFFE1E9E5),
    onTertiaryContainer = DotInk,
    error = DotError,
    onError = DotWhite,
    errorContainer = Color(0xFFFFD9DD),
    onErrorContainer = Color(0xFF48000B),
    background = DotIvory,
    onBackground = DotInk,
    surface = DotIvory,
    onSurface = DotInk,
    surfaceVariant = Color(0xFFE5EAE7),
    onSurfaceVariant = Color(0xFF43524C),
    outline = Color(0xFF74817C),
    outlineVariant = Color(0xFFC5CDC9),
    inverseSurface = DotInk,
    inverseOnSurface = DotIvory,
    inversePrimary = Color(0xFF4DE2A8),
    surfaceDim = Color(0xFFDEDCD6),
    surfaceBright = DotWhite,
    surfaceContainerLowest = DotWhite,
    surfaceContainerLow = Color(0xFFFAF8F2),
    surfaceContainer = Color(0xFFF2F0EA),
    surfaceContainerHigh = Color(0xFFECEAE4),
    surfaceContainerHighest = Color(0xFFE6E4DE),
)

private val DotDarkColors = darkColorScheme(
    primary = DotJade,
    onPrimary = DotInk,
    primaryContainer = Color(0xFF005239),
    onPrimaryContainer = Color(0xFFB7F3DA),
    secondary = Color(0xFFAFCFC1),
    onSecondary = Color(0xFF18372C),
    secondaryContainer = Color(0xFF294B3F),
    onSecondaryContainer = Color(0xFFD1EBDD),
    tertiary = Color(0xFFBBC8C1),
    onTertiary = Color(0xFF26332D),
    tertiaryContainer = Color(0xFF3B4942),
    onTertiaryContainer = Color(0xFFD7E5DD),
    error = Color(0xFFFFB2B9),
    onError = Color(0xFF680019),
    errorContainer = Color(0xFF920029),
    onErrorContainer = Color(0xFFFFD9DD),
    background = DotDarkBackground,
    onBackground = Color(0xFFE6F0EB),
    surface = DotDarkBackground,
    onSurface = Color(0xFFE6F0EB),
    surfaceVariant = Color(0xFF24332D),
    onSurfaceVariant = Color(0xFFC2CEC8),
    outline = Color(0xFF8C9993),
    outlineVariant = Color(0xFF2B3A34),
    inverseSurface = Color(0xFFE6F0EB),
    inverseOnSurface = Color(0xFF22302A),
    inversePrimary = Color(0xFF006C4A),
    surfaceDim = DotDarkBackground,
    surfaceBright = DotDarkCard,
    surfaceContainerLowest = DotDarkBackground,
    surfaceContainerLow = DotDarkCard,
    surfaceContainer = Color(0xFF19251F),
    surfaceContainerHigh = DotDarkHeader,
    surfaceContainerHighest = Color(0xFF24312B),
)

internal fun resolveDarkTheme(themeMode: String, systemDark: Boolean): Boolean = when (themeMode) {
    "light", "white" -> false
    "dark", "black" -> true
    else -> systemDark
}

@Composable
fun DotDialerTheme(
    systemDark: Boolean = isSystemInDarkTheme(),
    accent: DotAccent = DotAccent.Jade,
    interfaceScale: Float = 1f,
    highContrast: Boolean = false,
    ignoreSystemFontScale: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseColors = if (systemDark) DotDarkColors else DotLightColors
    val colors = remember(systemDark, accent, highContrast) {
        accentColors(baseColors, accent, systemDark).let { accented ->
            if (!highContrast) accented else accented.copy(
                onBackground = if (systemDark) Color.White else Color.Black,
                onSurface = if (systemDark) Color.White else Color.Black,
                onSurfaceVariant = if (systemDark) Color(0xFFF0F4F2) else Color(0xFF17211D),
                outline = if (systemDark) Color(0xFFCBD4D0) else Color(0xFF33423B),
            )
        }
    }
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.systemBarActivity()?.window ?: return@SideEffect
            makeLegacySystemBarsTransparent(window)
            applySystemBarIconAppearance(window, systemDark)
        }
        DisposableEffect(view, lifecycleOwner, systemDark) {
            val apply = {
                view.context.systemBarActivity()?.window?.let { applySystemBarIconAppearance(it, systemDark) }
                Unit
            }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) apply()
            }
            val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
                if (focused) apply()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            }
        }
    }

    val customColors = rememberCustomColors(systemDark)
    val systemDensity = LocalDensity.current
    val textDensity = remember(systemDensity, ignoreSystemFontScale) {
        appTextDensity(systemDensity, ignoreSystemFontScale)
    }
    CompositionLocalProvider(LocalCustomColors provides customColors, LocalDensity provides textDensity) {
        MaterialTheme(
            colorScheme = colors,
            typography = remember(interfaceScale) {
                DotDialerTypography.scaled(interfaceScale.coerceIn(0.9f, 1.2f))
            },
            shapes = rillShapes(RillShapeDefaults.DefaultRoundness),
            content = content,
        )
    }
}

@Suppress("DEPRECATION")
private fun makeLegacySystemBarsTransparent(window: Window) {
    // Android 15 enforces edge-to-edge and ignores bar colors. Older supported releases still need
    // explicit transparency to avoid an opaque strip behind the call and contact surfaces.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
}

@Composable
fun DotDialerAppTheme(content: @Composable () -> Unit) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val themeMode = remember(settingsState) {
        prefs.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto"
    }
    val accent = remember(settingsState) {
        resolveAccent(prefs.getString(PreferenceManager.KEY_ACCENT_COLOR, "jade"))
    }
    val interfaceScale = remember(settingsState) {
        prefs.getInt(PreferenceManager.KEY_INTERFACE_SCALE_PERCENT, 100) / 100f
    }
    val highContrast = remember(settingsState) {
        prefs.getBoolean(PreferenceManager.KEY_HIGH_CONTRAST_TEXT, false)
    }
    val ignoreSystemFontScale = remember(settingsState) {
        prefs.getBoolean(PreferenceManager.KEY_IGNORE_SYSTEM_FONT_SCALE, PreferenceManager.DEFAULT_IGNORE_SYSTEM_FONT_SCALE)
    }
    DotDialerTheme(
        systemDark = resolveDarkTheme(themeMode, isSystemInDarkTheme()),
        accent = accent,
        interfaceScale = interfaceScale,
        highContrast = highContrast,
        ignoreSystemFontScale = ignoreSystemFontScale,
        content = content,
    )
}

private fun accentColors(base: ColorScheme, accent: DotAccent, dark: Boolean): ColorScheme {
    val (primary, container, onContainer) = when (accent) {
        DotAccent.Jade -> if (dark) {
            Triple(DotJade, Color(0xFF005239), Color(0xFFB7F3DA))
        } else {
            Triple(DotJade, Color(0xFFC4F4DF), Color(0xFF073B29))
        }
        DotAccent.Blue -> if (dark) {
            Triple(Color(0xFFAFC6FF), Color(0xFF163F8F), Color(0xFFD9E3FF))
        } else {
            Triple(DotBlue, Color(0xFFDCE5FF), Color(0xFF102A63))
        }
        DotAccent.Violet -> if (dark) {
            Triple(DotElectricIris, Color(0xFF352A7A), Color(0xFFE9E0FF))
        } else {
            Triple(DotElectricIris, Color(0xFFE8DEFF), Color(0xFF30205F))
        }
        DotAccent.Coral -> if (dark) {
            Triple(Color(0xFFFFB4BC), Color(0xFF7D2938), Color(0xFFFFD9DE))
        } else {
            Triple(DotRuby, DotRubySoft, Color(0xFF5A1721))
        }
    }
    return base.copy(
        primary = primary,
        onPrimary = when {
            accent == DotAccent.Blue && !dark -> DotInk
            accent == DotAccent.Coral && !dark -> DotRubyInk
            dark && accent != DotAccent.Violet -> Color(0xFF0A241B)
            else -> Color.White
        },
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        inversePrimary = primary,
    )
}

private fun Typography.scaled(scale: Float): Typography = copy(
    displayLarge = displayLarge.scaleBy(scale),
    displayMedium = displayMedium.scaleBy(scale),
    displaySmall = displaySmall.scaleBy(scale),
    headlineLarge = headlineLarge.scaleBy(scale),
    headlineMedium = headlineMedium.scaleBy(scale),
    headlineSmall = headlineSmall.scaleBy(scale),
    titleLarge = titleLarge.scaleBy(scale),
    titleMedium = titleMedium.scaleBy(scale),
    titleSmall = titleSmall.scaleBy(scale),
    bodyLarge = bodyLarge.scaleBy(scale),
    bodyMedium = bodyMedium.scaleBy(scale),
    bodySmall = bodySmall.scaleBy(scale),
    labelLarge = labelLarge.scaleBy(scale),
    labelMedium = labelMedium.scaleBy(scale),
    labelSmall = labelSmall.scaleBy(scale),
)

private fun androidx.compose.ui.text.TextStyle.scaleBy(scale: Float) = copy(
    fontSize = fontSize * scale,
    lineHeight = lineHeight * scale,
)

data class CustomColors(
    val colorDarkIndigo: Color,
    val colorDarkBlue: Color,
    val colorDarkPink: Color,
    val colorDarkOrange: Color,
    val colorDarkAmber: Color,
    val colorDarkGreen: Color,
    val colorDarkOliva: Color,
    val colorDarkCyan: Color,
    val colorDarkRed: Color,
    val colorDarkPurple: Color,
    val colorIndigo: Color,
    val colorBlue: Color,
    val colorPink: Color,
    val colorOrange: Color,
    val colorAmber: Color,
    val colorGreen: Color,
    val colorOliva: Color,
    val colorCyan: Color,
    val colorRed: Color,
    val colorPurple: Color,
)

private fun dotCustomColors(dark: Boolean) = CustomColors(
    colorDarkIndigo = Color(0xFF214C3D),
    colorDarkBlue = Color(0xFF214C3D),
    colorDarkPink = Color(0xFF7D3341),
    colorDarkOrange = Color(0xFF6E4B25),
    colorDarkAmber = Color(0xFF615622),
    colorDarkGreen = Color(0xFF005239),
    colorDarkOliva = Color(0xFF46534D),
    colorDarkCyan = Color(0xFF145445),
    colorDarkRed = Color(0xFF920029),
    colorDarkPurple = Color(0xFF40534A),
    colorIndigo = if (dark) Color(0xFF9ADCC2) else Color(0xFFD0EEE1),
    colorBlue = if (dark) Color(0xFF9ADCC2) else Color(0xFFD0EEE1),
    colorPink = if (dark) Color(0xFFF0B7C1) else Color(0xFFFFD9DD),
    colorOrange = if (dark) Color(0xFFE4C38E) else Color(0xFFF4E2C5),
    colorAmber = if (dark) Color(0xFFD8CC8C) else Color(0xFFF0E9C8),
    colorGreen = if (dark) Color(0xFF4DE2A8) else Color(0xFFC4F4DF),
    colorOliva = if (dark) Color(0xFFBBC8C1) else Color(0xFFE1E9E5),
    colorCyan = if (dark) Color(0xFF86D8BA) else Color(0xFFC4F4DF),
    colorRed = if (dark) Color(0xFFFFB2B9) else Color(0xFFFFD9DD),
    colorPurple = if (dark) Color(0xFFB9CCC3) else Color(0xFFE1E9E5),
)

val LocalCustomColors = staticCompositionLocalOf { dotCustomColors(false) }

val ColorScheme.customColors: CustomColors
    @Composable get() = LocalCustomColors.current

@Composable
fun rememberCustomColors(darkTheme: Boolean): CustomColors = remember(darkTheme) {
    dotCustomColors(darkTheme)
}
