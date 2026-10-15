package dev.goodwy.rphone.localization

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationContractTest {

    @Test
    fun `in-app picker has one unambiguous entry for every shipped language`() {
        assertEquals(SUPPORTED_LOCALES.keys.toList(), AppLanguageCatalog.options.map { it.tag })
        assertEquals("English", AppLanguageCatalog.options.single { it.tag == "en" }.nativeName)
        assertEquals("en", AppLanguageCatalog.canonicalTag("en-RO"))
        assertEquals("", AppLanguageCatalog.canonicalTag(""))

        val languageCodes = AppLanguageCatalog.options.map { java.util.Locale.forLanguageTag(it.tag).language }
        assertEquals(languageCodes.distinct(), languageCodes)
    }

    @Test
    fun `Android locale picker exposes only the supported Latin script languages`() {
        val localeConfig = repoFile("app/src/main/res/xml/locales_config.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(localeConfig)
        val locales = document.getElementsByTagName("locale")
        val actual = (0 until locales.length).map { index ->
            (locales.item(index) as Element).getAttribute("android:name")
        }

        assertEquals(SUPPORTED_LOCALES.keys.toList(), actual)

        val gradle = repoFile("app/build.gradle").readText()
        actual.forEach { locale ->
            val resourceConfiguration = when (locale) {
                "pt-BR" -> "pt-rBR"
                "id" -> "in"
                else -> locale
            }
            assertTrue(gradle.contains("'$resourceConfiguration'"))
        }
        assertTrue(gradle.contains("generateLocaleConfig = false"))

        val sourceLocaleDirectories = repoFile("app/src/main/res")
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") && it.name != "values-night" }
            .map(File::getName)
            .toSet()
        assertEquals(SUPPORTED_LOCALES.values.filter { it != "values" }.toSet(), sourceLocaleDirectories)
    }

    @Test
    fun `every shipped locale covers all translatable resources and preserves placeholders`() {
        val base = resourcesInDirectory(repoFile("app/src/main/res/values"))
            .filterValues { it.translatable }

        SUPPORTED_LOCALES.filterKeys { it != "en" }.forEach { (locale, directory) ->
            val translatedFile = repoFile("app/src/main/res/$directory")
            val translated = resourcesInDirectory(translatedFile).filterValues { it.translatable }

            assertEquals("Resource coverage differs for $locale", base.keys, translated.keys)
            base.forEach { (name, source) ->
                val allowedSignatures = source.values.map(::placeholderSignature).toSet()
                translated.getValue(name).values.forEach { value ->
                    assertTrue(
                        "Placeholder mismatch for $locale/$name: $value",
                        placeholderSignature(value) in allowedSignatures,
                    )
                    assertFalse(
                        "Translation sentinel leaked into $locale/$name: $value",
                        SENTINEL_PATTERN.containsMatchIn(value),
                    )
                    assertFalse(
                        "A decimal digit is glued to a %d placeholder in $locale/$name: $value",
                        BROKEN_INTEGER_SUFFIX_PATTERN.containsMatchIn(value),
                    )
                    assertFalse(
                        "A word is repeated mechanically in $locale/$name: $value",
                        REPEATED_WORD_PATTERN.containsMatchIn(value),
                    )
                }
            }
        }
    }

    @Test
    fun `shipped translations do not silently fall back to English`() {
        val base = resourcesInDirectory(repoFile("app/src/main/res/values"))
            .filterValues { it.translatable }

        SUPPORTED_LOCALES.filterKeys { it != "en" }.forEach { (locale, directory) ->
            val translated = resourcesInDirectory(repoFile("app/src/main/res/$directory"))
                .filterValues { it.translatable }
            val identical = base.keys.filterTo(sortedSetOf()) { name ->
                base.getValue(name).values == translated.getValue(name).values
            }

            val intentional = COMMON_INTENTIONAL_IDENTICAL_TRANSLATIONS + INTENTIONAL_IDENTICAL_TRANSLATIONS.getValue(locale)
            // Search aliases retain the old localized labels, including previously approved cognates.
            val unexpected = identical - intentional - intentional.map { "settings_alias_$it" }.toSet()
            assertTrue(
                "Unexpected English fallback in $locale: $unexpected",
                unexpected.isEmpty(),
            )
        }
    }

    @Test
    fun `Play purchase confirmation is flavor scoped and translated in every shipped locale`() {
        val key = "plan_purchase_complete"
        val base = resourcesInDirectory(repoFile("app/src/gplay/res/values"))
        assertEquals(setOf(key), base.keys)
        assertEquals(listOf("Your plan is active."), base.getValue(key).values)
        SUPPORTED_LOCALES.forEach { (locale, directory) ->
            val translated = resourcesInDirectory(repoFile("app/src/gplay/res/$directory"))
            assertEquals("Play resource coverage differs for $locale", base.keys, translated.keys)
            val value = translated.getValue(key).values.single()
            assertTrue("Empty Play confirmation for $locale", value.isNotBlank())
            assertEquals(placeholderSignature(base.getValue(key).values.single()), placeholderSignature(value))
            assertFalse("Play-only confirmation leaked into common resources", resourcesInDirectory(
                repoFile("app/src/main/res/$directory"),
            ).containsKey(key))
        }
    }

    private data class Resource(
        val values: List<String>,
        val translatable: Boolean,
        val kind: String,
        val quantities: List<String>,
    )

    private fun resourcesIn(file: File): LinkedHashMap<String, Resource> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = linkedMapOf<String, Resource>()
        val children = document.documentElement.childNodes
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            if (element.tagName !in setOf("string", "plurals", "string-array")) continue
            val name = element.getAttribute("name")
            val values = if (element.tagName == "string") {
                listOf(element.textContent)
            } else {
                val items = element.getElementsByTagName("item")
                (0 until items.length).map(items::item).map { it.textContent }
            }
            check(result.put(name, Resource(values, element.getAttribute("translatable") != "false", element.tagName,
                (0 until element.getElementsByTagName("item").length).map { (element.getElementsByTagName("item").item(it) as Element).getAttribute("quantity") })) == null) {
                "Duplicate resource $name in $file"
            }
        }
        return result
    }

    private fun resourcesInDirectory(directory: File): LinkedHashMap<String, Resource> {
        val result = linkedMapOf<String, Resource>()
        directory.listFiles().orEmpty().filter { it.extension == "xml" }.sortedBy { it.name }.forEach { file ->
            resourcesIn(file).forEach { (name, resource) ->
                check(result.put(name, resource) == null) { "Duplicate resource $name in $directory" }
            }
        }
        return result
    }

    @Test
    fun `voice quality and brand tokens stay invariant and ordinary Unknown is localized`() {
        val base = resourcesInDirectory(repoFile("app/src/main/res/values"))
        val expected = mapOf(
            "call_voice_quality_unreported_short" to "Voice call",
            "call_voice_quality_hd" to "HD Voice",
            "call_voice_quality_hd_plus" to "HD+ Voice",
            "app_name" to "dot.dialer",
            "dark_edge_radio" to "Dark Edge Radio",
            "dot_dictate" to "Dot Dictate",
            "contact_photos_title" to "Contacts Optimizer",
            "call_screen_sim_label" to "SIM %1\$s",
            "about_copyright" to "© Dot Design, %1\$d",
        )
        expected.forEach { (key, value) ->
            assertFalse("$key must not be translated", base.getValue(key).translatable)
            assertEquals(listOf(value), base.getValue(key).values)
        }
        assertTrue("Unknown is ordinary UI, not a technical token", base.getValue("label_unknown").translatable)
        SUPPORTED_LOCALES.filterKeys { it != "en" }.forEach { (locale, directory) ->
            val translated = resourcesInDirectory(repoFile("app/src/main/res/$directory"))
            assertTrue("Nontranslatable overrides in $locale", translated.keys.intersect(base.filterValues { !it.translatable }.keys).isEmpty())
        }
    }

    @Test
    fun `all resources retain type nonempty content and valid plural or array structure`() {
        val base = resourcesInDirectory(repoFile("app/src/main/res/values"))
        SUPPORTED_LOCALES.forEach { (locale, directory) ->
            val translated = resourcesInDirectory(repoFile("app/src/main/res/$directory"))
            translated.forEach { (name, resource) ->
                assertEquals("Resource type $locale/$name", base.getValue(name).kind, resource.kind)
                assertTrue("Empty $locale/$name", resource.values.all { it.isNotBlank() })
                if (resource.kind == "plurals") {
                    assertTrue("Missing other $locale/$name", "other" in resource.quantities)
                    assertEquals(resource.quantities.distinct(), resource.quantities)
                    assertTrue(resource.quantities.all { it in setOf("zero", "one", "two", "few", "many", "other") })
                }
                if (resource.kind == "string-array") {
                    assertEquals(base.getValue(name).values.size, resource.values.size)
                    resource.values.forEachIndexed { index, value ->
                        assertEquals(placeholderSignature(base.getValue(name).values[index]), placeholderSignature(value))
                    }
                }
            }
        }
    }

    @Test
    fun `corrected reachable UI uses resources instead of English literals`() {
        val files = listOf(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/About.kt",
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/ContactManagementScreen.kt",
            "app/src/main/java/dev/goodwy/rphone/view/components/RillDialogs.kt",
        )
        val forbidden = listOf("Rate This App", "Your feedback helps us improve!", "GitHub Repository",
            "Standardizing numbers", "% completed", "Selected", "Game Over!", "Replay", "Jump!",
            "Tap JUMP to avoid obstacles!", "Hello!", "Tap a star to rate", "Not Now", "Star ")
        files.forEach { path ->
            val code = repoFile(path).readText().lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
            forbidden.forEach { value ->
                assertFalse("Untranslated literal $value in $path", code.contains("\"$value"))
            }
        }
    }

    private fun placeholderSignature(value: String): List<String> = PLACEHOLDER_PATTERN
        .findAll(value)
        .map(MatchResult::value)
        .filter { it != "%%" }
        .sorted()
        .toList()

    private fun repoFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(6)
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: error("Cannot locate $relativePath from $start")
    }

    companion object {
        private val SUPPORTED_LOCALES = linkedMapOf(
            "en" to "values",
            "ro" to "values-ro",
            "es" to "values-es",
            "pt-BR" to "values-pt-rBR",
            "fr" to "values-fr",
            "de" to "values-de",
            "it" to "values-it",
            "id" to "values-in",
            "tr" to "values-tr",
            "pl" to "values-pl",
            "vi" to "values-vi",
        )
        private val INTENTIONAL_IDENTICAL_TRANSLATIONS = mapOf(
            "ro" to sortedSetOf(
                "contact_action",
                "accent_coral", "accent_violet", "audio_route_bluetooth", "avatar_palette_google",
                "avatar_palette_multicolor", "avatar_palette_pastel", "email", "pager", "prefix", "recent_number",
                "suffix_description", "video",
            ),
            "es" to sortedSetOf(
                "accent_coral", "accent_jade", "accent_violet", "audio_route_bluetooth",
                "avatar_palette_google", "avatar_palette_jade", "avatar_palette_pastel",
                "avatar_palette_vibrant", "suffix_description",
            ),
            "pt-BR" to sortedSetOf(
                "accent_coral", "accent_jade", "audio_route_bluetooth", "avatar_palette_google",
                "avatar_palette_jade", "avatar_palette_pastel", "interface_settings", "notif_via_sim",
                "pager", "suffix_description",
            ),
            "fr" to sortedSetOf(
                "conference_participant_count",
                "contact_action",
                "accent_jade", "accent_violet", "actions", "audio_route_bluetooth",
                "avatar_palette_google", "avatar_palette_jade", "avatar_palette_pastel",
                "avatar_palette_vibrant", "contacts", "manage_contacts", "interface_settings", "message", "mobile",
                "notes", "notif_via_sim", "suffix_description", "theme_auto",
            ),
            "de" to sortedSetOf(
                "rating_okay",
                "accent_jade", "audio_route_bluetooth", "audio_route_headset", "avatar_palette_google",
                "avatar_palette_jade", "avatar_size_medium", "call_avatar_size_medium", "name", "pager",
                "suffix", "suffix_description", "theme_auto", "theme_system", "video",
            ),
            "it" to sortedSetOf(
                "accent_jade", "audio_route_bluetooth", "avatar_palette_jade", "email", "home_tab",
                "password", "theme_auto", "video",
            ),
            "id" to sortedSetOf(
                "accent_coral", "accent_jade", "accent_violet", "audio_route_bluetooth",
                "audio_route_headset", "audio_route_speaker", "avatar_palette_google",
                "avatar_palette_jade", "avatar_palette_pastel", "avatar_palette_vibrant", "pager", "video",
            ),
            "tr" to sortedSetOf(
                "accent_jade", "audio_route_bluetooth", "avatar_palette_google", "avatar_palette_jade",
                "avatar_palette_pastel", "video",
            ),
            "pl" to sortedSetOf(
                "accent_jade", "accent_violet", "audio_route_bluetooth", "avatar_palette_google",
                "avatar_palette_jade", "avatar_palette_pastel", "pager", "theme_auto", "theme_system",
            ),
            "vi" to sortedSetOf(
                "accent_violet", "avatar_palette_google", "email", "pager", "suffix_description",
            ),
        )
        private val COMMON_INTENTIONAL_IDENTICAL_TRANSLATIONS = setOf(
            "accent_violet", "android_auto", "days_letter", "hours_letter", "minutes_letter", "ok", "pin",
            "seconds_letter", "sms", "photo_scan_failure_count",
        )
        private val PLACEHOLDER_PATTERN = Regex(
            "%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]",
        )
        private val SENTINEL_PATTERN = Regex(
            "Alex\\w*Butica|Mihai\\w*P\\w+|Carmen\\w*I\\w+|Diana\\w*Marin|Radu\\w*Stan|4242\\d*",
            RegexOption.IGNORE_CASE,
        )
        private val BROKEN_INTEGER_SUFFIX_PATTERN = Regex("%(?:\\d+\\$)?d\\d")
        private val REPEATED_WORD_PATTERN = Regex(
            "\\b([\\p{L}\\p{N}]{2,})\\b(?:[\\s.,;:!?-]+\\1\\b){2,}",
            RegexOption.IGNORE_CASE,
        )
    }
}
