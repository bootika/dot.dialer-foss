package dev.goodwy.rphone.view.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedHomeFooterContractTest {
    @Test
    fun `recents footer opens contacts with dialpad closed`() {
        val homeSource = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/UnifiedHomeScreen.kt",
        ).readText()
        val supportSource = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/UnifiedHomeSupport.kt",
        ).readText()

        assertTrue(homeSource.contains("item(key = \"view-all-contacts\")"))
        assertTrue(homeSource.contains("HomeViewContactsButton("))
        assertTrue(homeSource.contains("showDialpad = false"))
        assertTrue(homeSource.contains("browseContacts = true"))
        assertTrue(homeSource.contains("listState.scrollToItem(0)"))
        assertTrue(supportSource.contains("stringResource(R.string.view_contacts)"))
        assertTrue(supportSource.contains("MaterialTheme.colorScheme.surfaceContainerHigh"))
    }

    private fun repoFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(6)
            .map { File(it, relativePath) }
            .firstOrNull(File::isFile)
            ?: error("Unable to find repository file: $relativePath")
    }
}
