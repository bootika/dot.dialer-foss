package dev.goodwy.rphone.view.screen.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsContentContractTest {

    @Test fun `retired private contacts are not advertised in any localized management summary`() {
        val retired = listOf("Private contacts", "Contacte private", "Private Kontakte",
            "Contactos privados", "Contacts privés", "Contatti privati", "Quan hệ cá nhân",
            "Kontakty prywatne", "Özel kişiler", "Contatos privados", "Kontak pribadi")
        val summaries = repoFile("app/src/main/res").listFiles()!!.filter { it.name.startsWith("values") }
            .mapNotNull { folder ->
                val xml = File(folder, "strings.xml")
                if (!xml.isFile) null else Regex("<string name=\"managing_contact_sources_subtitle\">([^<]+)</string>")
                    .find(xml.readText())?.groupValues?.get(1)
            }
        org.junit.Assert.assertEquals(11, summaries.size)
        for (summary in summaries) {
            assertTrue(summary.isNotBlank())
            assertFalse(summary, retired.any { summary.contains(it, ignoreCase = true) })
        }
    }

    @Test
    fun `contact tools only has normal contacts navigation and keeps all operations`() {
        val advanced = repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/settings/AdvancedSettingsScreen.kt").readText()
        val contacts = repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/settings/ContactManagementScreen.kt").readText()
        val home = repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/settings/components/SettingsHomeContent.kt").readText()
        assertFalse(advanced.contains("R.string.contact_tools"))
        assertFalse(advanced.contains("ContactManagementScreenDestination"))
        assertTrue(home.contains("ContactManagementScreenDestination()"))
        assertTrue(contacts.contains("if (!toolsOnly)"))
        assertTrue(contacts.contains("ContactManagementScreenDestination(toolsOnly = true)"))
        assertTrue(contacts.contains("viewModel.previewStandardize"))
        assertTrue(contacts.contains("ContactMergeDuplicatesScreenDestination"))
        assertTrue(contacts.contains("ContactUnmergeDuplicatesScreenDestination"))
    }

    @Test
    fun `about uses Dot links and the owner app portfolio`() {
        val settings = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/SettingsScreen.kt",
        ).readText()
        val about = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/About.kt",
        ).readText()
        val callerUi = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/CallerUIScreen.kt",
        ).readText()
        assertFalse(settings.contains("R.string.check_for_updates"))
        assertFalse(settings.contains("R.string.other_apps"))
        val portfolio = repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/settings/AboutOtherApps.kt").readText()
        assertTrue(about.contains("AboutOtherApps(language)"))
        assertTrue(portfolio.contains("R.string.other_apps"))
        assertTrue(portfolio.contains("DARK_EDGE_RADIO_URL"))
        assertTrue(portfolio.contains("dotDictateUrl(language)"))
        assertTrue(about.contains("termsAndConditionsUrl(language)"))
        assertTrue(about.contains("shadowElevation = 0.dp"))
        assertFalse(about.contains("Goodwy"))
        assertTrue(about.contains("ContributorsScreenDestination"))
        assertFalse(about.contains("R.string.support_development"))
        assertFalse(callerUi.contains("SupportProjectItem"))
    }

    @Test
    fun `settings landing page delegates colored icons to its component boundary`() {
        val settings = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/SettingsScreen.kt",
        ).readText()
        val components = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/components/SettingsHomeComponents.kt",
        ).readText()
        val content = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/components/SettingsHomeContent.kt",
        ).readText()

        assertTrue(settings.contains("SettingsHomeContent("))
        assertTrue(content.contains("SettingsHomeListItem("))
        assertTrue(components.contains("color = iconBgContainerColor"))
        assertTrue(components.contains("tint = iconContainerColor"))
    }

    @Test
    fun `automatic Android backup stays silent while manual backup opens Downloads`() {
        val settings = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/SettingsScreen.kt",
        ).readText()
        val contracts = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/util/BackupDocumentContracts.kt",
        ).readText()
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        val cloudRules = repoFile("app/src/main/res/xml/data_extraction_rules.xml").readText()
        val legacyRules = repoFile("app/src/main/res/xml/backup_rules.xml").readText()

        assertFalse(settings.contains("R.string.google_backup_title"))
        assertFalse(settings.contains("BACKUP_SETTINGS"))
        assertTrue(settings.contains("DotDialer_Backup_"))
        assertFalse(settings.contains("RPhone_Backup_"))
        assertTrue(contracts.contains("DocumentsContract.EXTRA_INITIAL_URI"))
        assertTrue(contracts.contains("primary:Download"))
        assertTrue(manifest.contains("android:allowBackup=\"true\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(cloudRules.contains("datastore/rill_prefs.preferences_pb"))
        assertTrue(cloudRules.contains("<device-transfer>"))
        assertTrue(legacyRules.contains("datastore/rill_prefs.preferences_pb"))
        assertFalse(legacyRules.contains("requireFlags="))
    }

    @Test
    fun `contacts section follows calls without the legacy oversized gap`() {
        val settings = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/SettingsScreen.kt",
        ).readText()

        assertFalse(settings.contains("Spacer(Modifier.height(72.dp))"))
    }

    @Test
    fun `settings search indexes the Android Auto switch`() {
        val catalog = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/components/SettingsSearchCatalog.kt",
        ).readText()
        val organization = repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/settings/components/SettingsOrganization.kt").readText()
        assertTrue(organization.contains("\"K04\", R.string.android_auto_integration, SettingsEditor.CALLS, false"))
        assertTrue(organization.contains("\"K04\" -> autoAvailable"))
        assertTrue(catalog.contains("AndroidAutoIntegration.isAvailable()"))
        assertTrue(catalog.contains("R.string.android_auto_integration_subtitle"))
        assertTrue(catalog.contains("SettingsEditor.CALLS -> navigate(CallSettingScreenDestination())"))
    }

    @Test
    fun `ambient droplet switch disables brightness without discarding its value`() {
        val callerUi = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/CallerUIScreen.kt",
        ).readText()
        val callScreen = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt",
        ).readText()
        val background = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreenBackground.kt",
        ).readText()
        val controls = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/AmbientDropletSettingsControls.kt",
        ).readText()

        assertTrue(callerUi.contains("AmbientDropletSettingsControls(prefs, settingsState)"))
        assertTrue(controls.contains("AmbientDropletPreference.ENABLED_KEY"))
        assertTrue(controls.contains("AmbientDropletPreference.BRIGHTNESS_KEY"))
        assertTrue(controls.contains("enabled = enabled"))
        assertTrue(controls.contains("SettingsPercentageSlider("))
        val sharedSlider = repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/settings/SettingsPercentageSlider.kt").readText()
        assertTrue(sharedSlider.contains(".alpha(if (enabled) 1f else 0.42f)"))
        assertTrue(callScreen.contains("ambientDroplets = ambientDroplets"))
        assertTrue(background.contains("if (ambientDroplets.enabled)"))
    }

    private fun repoFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(6)
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: error("Cannot locate $relativePath from $start")
    }
}
