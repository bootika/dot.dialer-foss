package dev.goodwy.rphone.view.components

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIconPaletteTest {

    @Test
    fun `settings cream palette is warm rather than gray`() {
        assertEquals(Color(0xFF6D5530), SETTINGS_ICON_CREAM_FOREGROUND_LIGHT)
        assertEquals(Color(0xFFF3E5CA), SETTINGS_ICON_CREAM_BACKGROUND_LIGHT)
        assertEquals(Color(0xFFE8D4AE), SETTINGS_ICON_CREAM_FOREGROUND_DARK)
        assertEquals(Color(0xFF4A3E2E), SETTINGS_ICON_CREAM_BACKGROUND_DARK)
    }

    @Test
    fun `settings routes normalize legacy icon colors centrally`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/RillExpressiveUI.kt",
        ).readText()
        val paletteSource = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/RillSettingsIconPalette.kt",
        ).readText()
        assertTrue(source.contains("if (neutralSettingsStyle)"))
        assertTrue(source.contains("settingsPalette.background"))
        assertTrue(source.contains("settingsPalette.foreground"))
        assertTrue(paletteSource.contains("foreground = colors.primary"))
        assertTrue(paletteSource.contains("background = colors.primaryContainer"))
        assertFalse(source.contains("if (neutralSettingsStyle) iconBgContainerColor"))
    }

    @Test
    fun `calling account icons preserve Android SIM identity as the sole palette exception`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/CallAccountsScreen.kt",
        ).readText()
        assertTrue(source.contains("account.displayColor(MaterialTheme.colorScheme.primary)"))
        assertTrue(source.contains("highlightColor"))
        assertTrue(source.contains("CallingAccountIdentityIcon"))
        assertTrue(source.contains("keep Android's system-provided color"))

        val accountSource = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/util/CallingAccountUtils.kt",
        ).readText()
        assertTrue(accountSource.contains("val icon: Icon?"))
        assertTrue(accountSource.contains("icon = account.icon"))

        listOf(
            "app/src/main/java/dev/goodwy/rphone/view/components/SimPickerDialog.kt",
            "app/src/main/java/dev/goodwy/rphone/view/components/CallingAccountPreferenceDialog.kt",
        ).forEach { path ->
            assertTrue(repoFile(path).readText().contains("CallingAccountIdentityIcon"))
        }
    }

    @Test
    fun `every settings destination opts into the constrained icon palette`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/MainActivity.kt",
        ).readText()
        val expectedRoutes = listOf(
            "SettingsScreenDestination.route",
            "AboutAppScreenDestination.route",
            "AvatarsPreferenceScreenDestination.route",
            "BiometricScreenDestination.route",
            "BlockedNumbersScreenDestination.route",
            "BlurEffectsElementsScreenDestination.route",
            "CallAccountsScreenDestination.route",
            "CallerUIScreenDestination.route",
            "CallSettingScreenDestination.route",
            "ContactManagementScreenDestination.route",
            "ContactMergeDuplicatesScreenDestination.route",
            "ContactUnmergeDuplicatesScreenDestination.route",
            "ContactVisibilityScreenDestination.route",
            "ContributorsScreenDestination.route",
            "HomeGesturesScreenDestination.route",
            "InterfaceScreenDestination.route",
            "LiquidGlassElementsScreenDestination.route",
            "QuickResponsesScreenDestination.route",
            "RecentsSearchScreenDestination.route",
            "SoundVibrationScreenDestination.route",
            "SpamScreenDestination.route",
        )
        expectedRoutes.forEach { route -> assertTrue("Missing $route", source.contains(route)) }
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
