package dev.goodwy.rphone.view.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchArchitectureContractTest {

    @Test
    fun `search orchestrator delegates result rendering`() {
        val search = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/Search.kt",
        ).readText()
        val results = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/SearchResults.kt",
        ).readText()

        assertTrue(search.contains("SearchResultsList("))
        assertTrue(results.contains("DotDialerSearchPayload.ContactResult"))
        assertTrue(results.contains("DotDialerSearchPayload.CallResult"))
        assertTrue(results.contains("DotDialerSearchPayload.NoteResult"))
        assertFalse(search.contains("NavController"))
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
