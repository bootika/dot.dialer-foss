package dev.goodwy.rphone.view.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircularInteractionContractTest {
    @Test
    fun `shared circular interaction clips before bounded feedback`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/CircularInteraction.kt",
        ).readText()

        val implementation = source.substringAfter("): Modifier =")
        assertTrue(implementation.contains("clip(CircleShape).combinedClickable("))
        assertTrue(implementation.contains("indication = ripple(bounded = true)"))
    }

    @Test
    fun `interactive avatars use the shared circular interaction`() {
        val favorite = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/IPhoneFavoritesRow.kt",
        ).readText()
        val home = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/HomeClickableModifier.kt",
        ).readText()
        val list = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/RillExpressiveUI.kt",
        ).readText()
        val tile = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/SingleTile.kt",
        ).readText()
        val notes = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/NotesScreen.kt",
        ).readText()
        val iconButton = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/RillIconButton.kt",
        ).readText()

        assertTrue(favorite.contains(".circularCombinedClickable("))
        assertFalse(favorite.contains("Modifier.combinedClickable("))
        assertTrue(home.contains("return circularCombinedClickable("))
        assertTrue(list.contains("Modifier.circularCombinedClickable("))
        assertTrue(tile.contains("Modifier.circularCombinedClickable("))
        assertTrue(notes.contains("Modifier.circularCombinedClickable("))
        assertTrue(iconButton.contains(".circularCombinedClickable("))
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
