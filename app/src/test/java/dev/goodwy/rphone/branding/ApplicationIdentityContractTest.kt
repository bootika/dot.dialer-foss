package dev.goodwy.rphone.branding

import dev.goodwy.rphone.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationIdentityContractTest {
    @Test
    fun `version name is derived from the three decimal version code positions`() {
        val code = BuildConfig.VERSION_CODE
        val expectedBaseName = "${code / 100}.${code / 10 % 10}.${code % 10}"

        assertEquals("$expectedBaseName-test", BuildConfig.VERSION_NAME)
    }

    @Test
    fun `android application identity stays dot dialer`() {
        val buildScript = repoFile("app/build.gradle").readText()
        val constants = repoFile(
            "app/src/main/java/dev/goodwy/rphone/Constants.kt",
        ).readText()

        assertTrue(buildScript.contains("applicationId = \"dot.dialer\""))
        assertTrue(buildScript.contains("applicationIdSuffix = \".test\""))
        assertTrue(constants.contains("details?id=dot.dialer"))
        assertTrue(
            repoFile("app/src/main/AndroidManifest.xml")
                .readText()
                .contains("android:usesCleartextTraffic=\"false\""),
        )
    }

    @Test
    fun `file provider authority follows the installed application id`() {
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        val paths = repoFile("app/src/main/res/xml/file_paths.xml").readText()
        val sharing = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/util/VCardShareUtils.kt",
        ).readText()

        assertTrue(manifest.contains("android:authorities=\"${'$'}{applicationId}.provider\""))
        assertTrue(sharing.contains("${'$'}{context.packageName}.provider"))
        assertTrue(paths.contains("<cache-path name=\"shared_contacts\" path=\"shared_contacts/\" />"))
        assertTrue(!paths.contains("external-path"))
        assertTrue(!paths.contains("external-files-path"))
        assertTrue(!paths.contains("<files-path"))
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
