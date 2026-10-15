package dev.goodwy.rphone.view.screen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutGeometryTest {

    @Test
    fun `approved contact browser geometry stays stable`() {
        assertEquals(2, BROWSE_CONTACTS_TITLE_TOP_PADDING_DP)
        assertEquals(46, HOME_RESULT_AVATAR_DP)
        assertEquals(12, HOME_RESULT_VERTICAL_PADDING_DP)
    }

    @Test
    fun `home top bar stays inside horizontal safe drawing bounds`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/UnifiedHomeScreen.kt",
        ).readText()
        val topBar = source.substringAfter("internal fun UnifiedHomeTopBar(")
            .substringBefore("private fun UnifiedHomeContent(")

        assertTrue(
            topBar.contains(
                ".windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))",
            ),
        )
    }

    private fun repoFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(6)
            .map { File(it, relativePath) }
            .firstOrNull { it.isFile }
            ?: error("Unable to find repository file: $relativePath")
    }
}
