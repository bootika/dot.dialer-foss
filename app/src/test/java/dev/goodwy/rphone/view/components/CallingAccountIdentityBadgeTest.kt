package dev.goodwy.rphone.view.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallingAccountIdentityBadgeTest {
    @Test
    fun callScreenBadgeIsNeutralWhileKeepingTheAndroidIcon() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/CallingAccountIdentityBadge.kt",
        ).readText()

        assertFalse(source.contains("highlightColor"))
        assertTrue(source.contains("loadCallingAccountIcon"))
        assertTrue(source.contains("color = MaterialTheme.colorScheme.surface.copy"))
        assertTrue(source.contains("contentColor = MaterialTheme.colorScheme.onSurfaceVariant"))
        assertTrue(CALL_ACCOUNT_BADGE_CONTAINER_ALPHA in 0.24f..0.32f)
        assertTrue(CALL_ACCOUNT_BADGE_BORDER_ALPHA in 0.12f..0.20f)
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
