package dev.goodwy.rphone.view.screen

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ContactActionVisibilityContractTest {
    @Test fun `no production Go to top or contact Video entry remains`() {
        val root=File(System.getProperty("user.dir"),"src/main/java")
        assertTrue(root.isDirectory)
        root.walkTopDown().filter { it.isFile && it.extension=="kt" }.forEach {
            assertFalse("Go to top returned in ${it.name}",it.readText().contains("ScrollToTopButton"))
            assertFalse("Go to top label returned in ${it.name}",it.readText().contains("\"Scroll to top\""))
        }
        val details=File(root,"dev/goodwy/rphone/view/screen/ContactDetails.kt").readText()
        assertFalse(details.contains("rememberVideoLauncher"))
        assertFalse(details.contains("R.string.video"))
        assertTrue(details.contains("ContactPhotoViewer("))
        assertEquals(2,Regex("clickable\\(enabled = !contact\\?\\.photoUri.isNullOrBlank\\(\\)\\)").findAll(details).count())
    }
}
