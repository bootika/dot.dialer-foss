package dev.goodwy.rphone.view.screen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDetailsColorContractTest {

    @Test
    fun `contact actions use solid accent once and neutral supporting surfaces`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/ContactDetails.kt",
        ).readText()

        assertTrue(source.contains("containerColor = MaterialTheme.colorScheme.primary"))
        assertTrue(source.contains("contentColor = MaterialTheme.colorScheme.onPrimary"))
        assertTrue(source.contains("containerColor = MaterialTheme.colorScheme.surfaceContainerHigh"))
        assertFalse(source.contains("containerColor = MaterialTheme.colorScheme.primaryContainer"))
        assertFalse(source.contains("containerColor = MaterialTheme.colorScheme.secondaryContainer"))
    }

    @Test
    fun `favorite star has no tinted or oval halo`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/ContactDetails.kt",
        ).readText()

        assertTrue(source.contains("modifier = Modifier.size(40.dp)"))
        assertTrue(source.contains("containerColor = Color.Transparent"))
        assertFalse(source.contains("modifier = Modifier.width(32.dp)"))
    }

    @Test
    fun `shared cards and icon supports never dilute the accent`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/RillExpressiveUI.kt",
        ).readText()

        assertTrue(source.contains("containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh"))
        assertTrue(source.contains("?: colors.surfaceContainerHighest"))
        assertFalse(source.contains("iconContainerColor?.copy(alpha = 0.15f)"))
        assertFalse(source.contains("?: MaterialTheme.colorScheme.secondaryContainer"))
    }

    @Test
    fun `contact detail category titles use the neutral theme text color`() {
        val contactComponentSource = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/ContactDetailsComponents.kt",
        ).readText()
        val componentSource = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/RillExpressiveUI.kt",
        ).readText()

        assertTrue(componentSource.contains("titleColor: Color = MaterialTheme.colorScheme.primary"))
        assertTrue(componentSource.contains("color = titleColor"))
        assertTrue(contactComponentSource.contains("internal fun ContactDetailsCategoryCard"))
        assertTrue(contactComponentSource.contains("titleColor = MaterialTheme.colorScheme.onSurface"))
        assertEquals(
            1,
            Regex("RillExpressiveCard\\(\\s*title\\s*=").findAll(contactComponentSource).count(),
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
