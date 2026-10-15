package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactPhoneDetail
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteCallNumberTest {
    private val contact = Contact(
        id = "contact-1",
        phoneNumbers = listOf("+40 721 111 111", "0722 222 222"),
        phoneDetails = listOf(
            ContactPhoneDetail(number = "+40 721 111 111"),
            ContactPhoneDetail(number = "0722 222 222", isPrimary = true),
        ),
    )

    @Test
    fun `saved favorite number wins over the system primary number`() {
        assertEquals(
            "+40 721 111 111",
            favoriteCallNumber(contact, "+40721111111"),
        )
    }

    @Test
    fun `system primary number is used when no favorite override exists`() {
        assertEquals("0722 222 222", favoriteCallNumber(contact, null))
    }

    @Test
    fun `stale favorite override falls back to system primary number`() {
        assertEquals("0722 222 222", favoriteCallNumber(contact, "0700 000 000"))
    }

    @Test
    fun `contact without phone numbers has no favorite call number`() {
        assertNull(favoriteCallNumber(Contact(id = "empty"), null))
    }

    @Test
    fun `favorite number menu is explicitly a selection control`() {
        val english = repoFile("app/src/main/res/values/strings.xml").readText()
        val romanian = repoFile("app/src/main/res/values-ro/strings.xml").readText()
        val home = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/UnifiedHomeScreen.kt",
        ).readText()

        assertTrue(english.contains(">Select call number</string>"))
        assertTrue(english.contains(">Selected: %1\$s</string>"))
        assertTrue(romanian.contains(">Selectează numărul pentru apel</string>"))
        assertTrue(romanian.contains(">Selectat: %1\$s</string>"))
        assertTrue(home.contains("label = stringResource(R.string.favorite_call_number)"))
        assertTrue(home.contains("subtitle = stringResource(R.string.favorite_call_number_value"))
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
