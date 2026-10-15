package dev.goodwy.rphone.localization

import java.util.Locale

/** The exact language choices presented inside Dot Dialer. */
data class AppLanguageOption(
    val tag: String,
    val nativeName: String,
)

object AppLanguageCatalog {
    val options = listOf(
        AppLanguageOption("en", "English"),
        AppLanguageOption("ro", "Română"),
        AppLanguageOption("es", "Español"),
        AppLanguageOption("pt-BR", "Português (Brasil)"),
        AppLanguageOption("fr", "Français"),
        AppLanguageOption("de", "Deutsch"),
        AppLanguageOption("it", "Italiano"),
        AppLanguageOption("id", "Bahasa Indonesia"),
        AppLanguageOption("tr", "Türkçe"),
        AppLanguageOption("pl", "Polski"),
        AppLanguageOption("vi", "Tiếng Việt"),
    )

    /**
     * Maps a regional locale persisted by an OEM (for example en-RO) back to the
     * single language choice shipped by the app. An empty tag means follow system.
     */
    fun canonicalTag(rawTag: String?): String {
        val firstTag = rawTag.orEmpty().substringBefore(',').trim()
        if (firstTag.isEmpty()) return ""
        options.firstOrNull { it.tag.equals(firstTag, ignoreCase = true) }?.let { return it.tag }

        val language = Locale.forLanguageTag(firstTag).language
        return options.singleOrNull { Locale.forLanguageTag(it.tag).language == language }?.tag.orEmpty()
    }
}
