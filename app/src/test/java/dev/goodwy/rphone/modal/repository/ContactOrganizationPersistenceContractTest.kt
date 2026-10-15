package dev.goodwy.rphone.modal.repository

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactOrganizationPersistenceContractTest {
    @Test
    fun `raw contact organization is replaced so cleared values cannot survive`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/modal/repository/ContactOrganizationOperations.kt",
        ).readText()
        val deletePosition = source.indexOf("ContentProviderOperation.newDelete")
        val insertPosition = source.indexOf("ContentProviderOperation.newInsert")
        assertTrue(deletePosition >= 0)
        assertTrue(insertPosition > deletePosition)
        assertTrue(source.contains("if (company.isBlank() && jobTitle.isBlank()) return"))
        assertTrue(source.contains("CommonDataKinds.Organization.COMPANY, company"))
        assertTrue(source.contains("CommonDataKinds.Organization.TITLE, jobTitle"))
    }

    private fun repoFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(6)
            .map { File(it, relativePath) }
            .firstOrNull(File::exists)
            ?: error("Cannot locate $relativePath from $start")
    }
}
