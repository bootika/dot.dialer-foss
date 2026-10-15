package dev.goodwy.rphone.view.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDepthPolishTest {

    @Test
    fun `alphabet indicator keeps a restrained shadow`() {
        assertEquals(3, CONTACT_ALPHABET_INDICATOR_SHADOW_ELEVATION_DP)
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/AZListScroll.kt",
        ).readText()
        assertTrue(source.contains("color = MaterialTheme.colorScheme.surfaceContainer,"))
        assertFalse(source.contains("color = MaterialTheme.colorScheme.surfaceContainerHighest,"))
    }

    @Test
    fun `dialpad button uses subtle resting and interaction elevations`() {
        assertEquals(2, DIALPAD_FAB_DEFAULT_ELEVATION_DP)
        assertEquals(4, DIALPAD_FAB_INTERACTION_ELEVATION_DP)
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
