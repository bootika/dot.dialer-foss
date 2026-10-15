package dev.goodwy.rphone.view.screen.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeutralSettingsComponentsTest {

    @Test
    fun `two and three choices use a segmented control`() {
        assertEquals(
            DotSettingsChoicePresentation.Segmented,
            dotSettingsChoicePresentation(2),
        )
        assertEquals(
            DotSettingsChoicePresentation.Segmented,
            dotSettingsChoicePresentation(3),
        )
    }

    @Test
    fun `medium choice sets use an inline selector`() {
        assertEquals(
            DotSettingsChoicePresentation.InlineSelect,
            dotSettingsChoicePresentation(4),
        )
        assertEquals(
            DotSettingsChoicePresentation.InlineSelect,
            dotSettingsChoicePresentation(8),
        )
    }

    @Test
    fun `long choice sets keep the scalable dialog`() {
        assertEquals(
            DotSettingsChoicePresentation.Dialog,
            dotSettingsChoicePresentation(9),
        )
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
