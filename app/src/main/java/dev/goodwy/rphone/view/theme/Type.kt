@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.goodwy.rphone.view.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.goodwy.rphone.R

val ManropeFontFamily = FontFamily(
    manropeFont(FontWeight.ExtraLight),
    manropeFont(FontWeight.Light),
    manropeFont(FontWeight.Normal),
    manropeFont(FontWeight.Medium),
    manropeFont(FontWeight.SemiBold),
    manropeFont(FontWeight.Bold),
)

private fun manropeFont(weight: FontWeight) = Font(
    resId = R.font.manrope_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val DotDialerTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = manropeTextStyle(FontWeight.Bold, 45, 52),
    displaySmall = manropeTextStyle(FontWeight.Bold, 36, 44),
    headlineLarge = manropeTextStyle(FontWeight.Bold, 32, 40),
    headlineMedium = manropeTextStyle(FontWeight.Bold, 28, 36),
    headlineSmall = manropeTextStyle(FontWeight.SemiBold, 24, 32),
    titleLarge = manropeTextStyle(FontWeight.SemiBold, 22, 28),
    titleMedium = manropeTextStyle(FontWeight.Medium, 18, 25),
    titleSmall = manropeTextStyle(FontWeight.SemiBold, 14, 20),
    bodyLarge = manropeTextStyle(FontWeight.Normal, 16, 24),
    bodyMedium = manropeTextStyle(FontWeight.Normal, 14, 20),
    bodySmall = manropeTextStyle(FontWeight.Normal, 13, 18),
    labelLarge = manropeTextStyle(FontWeight.Medium, 14, 20),
    labelMedium = manropeTextStyle(FontWeight.Normal, 12, 16),
    labelSmall = manropeTextStyle(FontWeight.Normal, 11, 16),
)

private fun manropeTextStyle(
    weight: FontWeight,
    sizeSp: Int,
    lineHeightSp: Int,
): TextStyle = TextStyle(
    fontFamily = ManropeFontFamily,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
)

@Deprecated("Dot Dialer uses fixed Manrope typography")
fun buildTypography(fontFamily: FontFamily, scale: Float = 1.0f): Typography = DotDialerTypography

val Typography = DotDialerTypography

/** Actionable row labels keep body sizing while descriptions remain Regular. */
val Typography.controlLabel: TextStyle
    get() = bodyLarge.copy(fontWeight = FontWeight.Medium)
