package dev.goodwy.rphone.view.screen.settings

import androidx.compose.ui.graphics.Color
import dev.goodwy.rphone.view.components.modernPastelAvatarColors
import dev.goodwy.rphone.view.theme.DotElectricIris
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualPaletteSelectorsTest {
    @Test
    fun `accent previews preserve keys and use exact electric iris`() {
        assertEquals(DotElectricIris, accentPreviewColor("violet"))
        assertEquals(Color(0xFF6857E5), accentPreviewColor("violet"))
    }

    @Test
    fun `avatar previews use the actual runtime palettes`() {
        val primary = Color(0xFFC4F4DF)

        assertEquals(
            modernPastelAvatarColors.map { it.first },
            avatarPalettePreviewColors(AVATAR_PALETTE_MULTICOLOR, primary),
        )
        assertEquals(
            modernPastelAvatarColors.map { it.first },
            avatarPalettePreviewColors(AVATAR_PALETTE_APP_COLOR, primary),
        )
    }

    @Test
    fun `avatar preview includes exact Thunderbird amber and orange colors`() {
        val preview = avatarPalettePreviewColors(AVATAR_PALETTE_MULTICOLOR, Color.Black)

        assertTrue(Color(0xFFFFCA28) in preview)
        assertTrue(Color(0xFFFFA726) in preview)
    }

    @Test
    fun `visual selectors expose names selection semantics outline and check`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/VisualPaletteSelectors.kt",
        ).readText()

        assertTrue(source.contains("text = label"))
        assertTrue(source.contains("this.selected = selected"))
        assertTrue(source.contains("Modifier.border(1.5.dp"))
        assertTrue(source.contains("Icons.Default.Check"))
        assertTrue(source.contains("AvatarPalettePreview"))
        assertTrue(source.contains("AVATAR_PALETTE_PREVIEW_COLUMNS = 10"))
        assertTrue(source.contains("colors.forEachIndexed"))
    }

    @Test
    fun `accent swatches have no circular outline while selection checks remain`() {
        val source = repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/settings/VisualPaletteSelectors.kt").readText()
        val swatch = source.substringAfter("internal fun AccentSwatch(")
            .substringBefore("internal fun AvatarPalettePreview(")
        org.junit.Assert.assertFalse(swatch.contains(".border("))
        assertTrue(swatch.contains(".size(34.dp)"))
        assertTrue(swatch.contains(".background(color)"))
        assertTrue(swatch.contains("Icons.Default.Check"))
        assertTrue(source.contains("Modifier.border(1.5.dp")) // Selected row, not circle.
    }

    @Test
    fun `avatar colors are not configurable from either settings surface`() {
        val interfaceSource = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/InterfaceScreen.kt",
        ).readText()
        val advancedAvatarSource = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/settings/AvatarsPreferenceScreen.kt",
        ).readText()

        org.junit.Assert.assertFalse(interfaceSource.contains("DotAvatarPaletteChoiceRow("))
        org.junit.Assert.assertFalse(advancedAvatarSource.contains("DotAvatarPaletteChoiceRow("))
        org.junit.Assert.assertFalse(advancedAvatarSource.contains("setAvatarPalette("))
        org.junit.Assert.assertFalse(interfaceSource.contains("avatar_palette_app_color"))
        org.junit.Assert.assertFalse(advancedAvatarSource.contains("avatar_palette_app_color"))
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
