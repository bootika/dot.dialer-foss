package dev.goodwy.rphone

import androidx.compose.ui.unit.dp

val GITHUB_DEV = "https://github.com/bootika"
val GITHUB_URL = "https://github.com/bootika/dot.dialer-foss"
val SITE_URL = "https://darkedge.ro/"
val GP_URL = "https://play.google.com/store/apps/details?id=dot.dialer"
val GP_DEV_URL = "https://play.google.com/store/apps/dev?id=5233015275563872792"
val DARK_EDGE_RADIO_URL = "https://play.google.com/store/apps/details?id=com.darkedgeradio.darkedgeradio"
val COFFEE_URL = SITE_URL
val DONATE_URL = SITE_URL
val GITHUB_RELEASES_URL = "https://github.com/bootika/dot.dialer-foss/releases"
val PRIVACY_POLICY = "https://dotdesign.ro/en/apps/dot-dialer/privacy/"
val TERMS_AND_CONDITIONS = "https://dotdesign.ro/en/apps/dot-dialer/terms/"

fun privacyPolicyUrl(language: String): String = if (isRomanian(language))
    "https://dotdesign.ro/aplicatii/dot-dialer/confidentialitate/" else PRIVACY_POLICY

fun termsAndConditionsUrl(language: String): String = if (isRomanian(language))
    "https://dotdesign.ro/aplicatii/dot-dialer/termeni/" else TERMS_AND_CONDITIONS

fun dotDictateUrl(language: String): String = if (isRomanian(language))
    "https://dotdesign.ro/aplicatii/dot-dictation/" else "https://dotdesign.ro/en/apps/dot-dictation/"

private fun isRomanian(language: String): Boolean = language.substringBefore('-').equals("ro", ignoreCase = true)

const val device_only = "local"
const val private_only = "private"

val bottomBarHeight = 64.dp

val cardCornerExtraLarge = 28.dp //MaterialTheme.shapes.extraLarge
val cardCornerLarge = 16.dp //MaterialTheme.shapes.large
val cardCornerMedium = 12.dp //MaterialTheme.shapes.medium
val cardCornerSmall = 8.dp //MaterialTheme.shapes.small
val cardCornerExtraSmall = 4.dp //MaterialTheme.shapes.extraSmall
val cardSpacedBy = 2.dp

const val HOUR_MINUTES = 60
const val DAY_MINUTES = 24 * HOUR_MINUTES
const val WEEK_MINUTES = DAY_MINUTES * 7
const val MONTH_MINUTES = DAY_MINUTES * 30
const val YEAR_MINUTES = DAY_MINUTES * 365

const val MINUTE_SECONDS = 60
const val HOUR_SECONDS = HOUR_MINUTES * 60
const val DAY_SECONDS = DAY_MINUTES * 60
const val WEEK_SECONDS = WEEK_MINUTES * 60
const val MONTH_SECONDS = MONTH_MINUTES * 60
const val YEAR_SECONDS = YEAR_MINUTES * 60
