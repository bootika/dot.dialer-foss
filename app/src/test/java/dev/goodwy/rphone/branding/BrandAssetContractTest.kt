package dev.goodwy.rphone.branding

import java.io.File
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandAssetContractTest {
    @Test
    fun `one approved master drives every branded surface with launcher optical sizing`() {
        val foreground = repoFile("app/src/main/res/drawable/ic_dot_dialer_foreground.xml").readText()
        val monochrome = repoFile("app/src/main/res/drawable/ic_dot_dialer_monochrome.xml").readText()
        val about = repoFile("app/src/main/res/drawable/ic_dot_dialer_about.xml").readText()
        val svg = repoFile("branding/dot-dialer-phone-mark.svg").readText()
        val lockup = repoFile("branding/dot-dialer-logo-lockup.svg").readText()

        val launcherPath = androidPaths(foreground).single()
        val masterPath = requireNotNull(
            Regex("<path fill=\"#FFFFFF\" d=\"([^\"]+)\"")
                .find(svg)?.groupValues?.get(1),
        )
        assertEquals(launcherPath, androidPaths(monochrome).single())
        assertEquals(launcherPath, androidPaths(about).last())
        assertPathScaledAroundCenter(masterPath, launcherPath, scale = 0.84)
        assertTrue(lockup.contains(masterPath))
        assertTrue("The traced master must retain its smooth contour", masterPath.count { it == 'C' } >= 40)
        assertFalse("The wordmark must be outlined, not depend on an installed font", lockup.contains("<text"))
        assertTrue(lockup.contains("fill=\"#10231C\""))
    }

    @Test
    fun `adaptive mark stays inside Android safe geometry`() {
        val foreground = repoFile("app/src/main/res/drawable/ic_dot_dialer_foreground.xml").readText()
        val values = Regex("-?\\d+(?:\\.\\d+)?")
            .findAll(androidPaths(foreground).single())
            .map { it.value.toDouble() }
            .toList()

        assertEquals("Every vector coordinate must be an x-y pair", 0, values.size % 2)
        val points = values.chunked(2).map { it[0] to it[1] }
        assertTrue(points.all { (x, y) -> x in 18.0..90.0 && y in 18.0..90.0 })
        assertTrue(
            "The complete mark, including Bezier controls, must stay inside the 66dp safe circle",
            points.maxOf { (x, y) -> hypot(x - 54.0, y - 54.0) } <= 33.0,
        )
    }

    @Test
    fun `launcher splash about and notifications use the Dot Dialer identity`() {
        val adaptive = repoFile("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val background = repoFile("app/src/main/res/drawable/ic_dot_dialer_background.xml").readText()
        val splash = repoFile("app/src/main/res/drawable/ic_dot_dialer_splash.xml").readText()
        val aboutScreen = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/About.kt",
        ).readText()
        val notifications = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/CallNotificationManager.kt",
        ).readText()

        assertTrue(background.contains("#00BB77"))
        assertTrue(adaptive.contains("@drawable/ic_dot_dialer_foreground"))
        assertTrue(adaptive.contains("@drawable/ic_dot_dialer_monochrome"))
        assertTrue(splash.contains("@drawable/ic_dot_dialer_foreground"))
        assertTrue(aboutScreen.contains("R.drawable.ic_dot_dialer_about"))
        assertTrue(notifications.contains("R.drawable.ic_dot_dialer_notification"))

        val stringCatalogs = repoFile("app/src/main/res")
            .walkTopDown()
            .filter { it.name == "strings.xml" }
            .toList()
        assertFalse(stringCatalogs.any { it.readText().contains("Rill Phone") })
    }

    @Test
    fun `launcher and about use the exact dot dialer product name in every build`() {
        val mainStrings = repoFile("app/src/main/res/values/strings.xml").readText()
        val debugStrings = repoFile("app/src/debug/res/values/strings.xml").readText()
        val exactName = "<string name=\"app_name\" translatable=\"false\">dot.dialer</string>"
        assertTrue(mainStrings.contains(exactName))
        assertTrue(debugStrings.contains(exactName))
        assertFalse(debugStrings.contains("Test"))

        val localizedCatalogs = repoFile("app/src/main/res")
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") && it.name != "values-night" }
            .map { File(it, "strings.xml") }
            .filter(File::isFile)
        assertFalse(
            "Localized resources must not override the product name",
            localizedCatalogs.any { it.readText().contains("name=\"app_name\"") },
        )
    }

    @Test
    fun `phone actions use the approved handset instead of material phone glyphs`() {
        val masterSvg = repoFile("branding/dot-dialer-phone-mark.svg").readText()
        val masterPath = requireNotNull(
            Regex("<path fill=\"#FFFFFF\" d=\"([^\"]+)\"")
                .find(masterSvg)?.groupValues?.get(1),
        )
        val actionAssets = listOf(
            "app/src/main/res/drawable/ic_widget_call.xml",
            "app/src/main/res/drawable/ic_widget_phone.xml",
            "app/src/main/res/drawable/ic_phone.xml",
            "app/src/main/res/drawable/ic_phone_star.xml",
        )
        actionAssets.forEach { relativePath ->
            assertTrue(
                "$relativePath must contain the approved contour",
                androidPaths(repoFile(relativePath).readText()).contains(masterPath),
            )
        }

        val sourceRoot = repoFile("app/src/main/java")
        val legacyPhoneGlyph = Regex(
            "Icons\\.(Default|Rounded|Outlined)\\." +
                "(Call|Phone|CallEnd|AddIcCall|PhonePaused|PhoneDisabled|SettingsPhone|RingVolume)\\b",
        )
        val offenders = sourceRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { legacyPhoneGlyph.containsMatchIn(it.readText()) }
            .map { it.relativeTo(sourceRoot).path }
            .toList()
        assertTrue("Material phone glyphs remain in: $offenders", offenders.isEmpty())
    }

    private fun androidPaths(xml: String): List<String> =
        Regex("android:pathData=\"([^\"]+)\"")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()

    private fun assertPathScaledAroundCenter(master: String, scaled: String, scale: Double) {
        val coordinatePattern = Regex("-?\\d+(?:\\.\\d+)?")
        val masterCoordinates = coordinatePattern.findAll(master).map { it.value.toDouble() }.toList()
        val scaledCoordinates = coordinatePattern.findAll(scaled).map { it.value.toDouble() }.toList()
        assertEquals(masterCoordinates.size, scaledCoordinates.size)
        masterCoordinates.zip(scaledCoordinates).forEachIndexed { index, (source, actual) ->
            val expected = 54.0 + (source - 54.0) * scale
            assertEquals("Coordinate $index must preserve the master contour", expected, actual, 0.002)
        }
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
