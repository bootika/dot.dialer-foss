package dev.goodwy.rphone.modal.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class RawContactEditabilityTest {
    @Test fun unknownColumnFallbackRequiresARealWritableSourceCapability() {
        assertEquals(true,sourceWritableWithoutRowFlags(null,null,emptySet()))
        assertEquals(true,sourceWritableWithoutRowFlags("test@example.test","com.google",setOf("com.google")))
        assertFalse(sourceWritableWithoutRowFlags("test@example.test","com.google",emptySet()))
        assertFalse(sourceWritableWithoutRowFlags("Test Alexandru Butica","linked.readonly",setOf("com.google")))
        assertFalse(sourceWritableWithoutRowFlags(null,"com.google",setOf("com.google")))
    }
    @Test
    fun rawContactIsReadOnlyOnlyWhenAllOfItsDataRowsAreReadOnly() {
        val result = readOnlyRawContactIds(
            listOf(
                RawContactDataAccess("linked", isReadOnly = true),
                RawContactDataAccess("linked", isReadOnly = true),
                RawContactDataAccess("phone", isReadOnly = true),
                RawContactDataAccess("phone", isReadOnly = false),
            ),
        )

        assertEquals(setOf("linked"), result)
        assertFalse("phone" in result)
    }

    @Test
    fun rawContactsQueryDoesNotRequestOemRejectedDerivedColumn() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/modal/repository/ContactsRepository.kt",
        ).readText()
        val method = source.substringAfter("override suspend fun getRawContactsForContact")
            .substringBefore("// We use Android's aggregation mechanism")

        assertFalse(method.contains("RawContacts.RAW_CONTACT_IS_READ_ONLY"))
    }

    private fun repoFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(8)
            .map { File(it, relativePath) }
            .firstOrNull { it.isFile }
            ?: error("Unable to resolve repository file: $relativePath")
    }
}
