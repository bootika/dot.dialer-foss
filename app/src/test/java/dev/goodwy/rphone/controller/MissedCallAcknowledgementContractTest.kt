package dev.goodwy.rphone.controller

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedCallAcknowledgementContractTest {
    @Test
    fun `acknowledging missed calls preserves history and clears unread state`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/modal/repository/CallLogRepository.kt",
        ).readText()
        val acknowledgement = source.substringAfter("override suspend fun markMissedCallsSeen()")
            .substringBefore("private fun contactIdFromLookupUri")

        assertTrue(acknowledgement.contains("CallLog.Calls.MISSED_TYPE"))
        assertTrue(acknowledgement.contains("put(CallLog.Calls.NEW, 0)"))
        assertTrue(acknowledgement.contains("put(CallLog.Calls.IS_READ, 1)"))
        assertTrue(acknowledgement.contains("OR ${'$'}{CallLog.Calls.IS_READ} = 0"))
        assertTrue(acknowledgement.contains("contentResolver.update"))
        assertTrue(!acknowledgement.contains("contentResolver.delete"))
    }

    @Test
    fun `notification cleanup survives process restart and home acknowledges on resume`() {
        val notifications = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/MissedCallNotifications.kt",
        ).readText()
        val home = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/UnifiedHomeScreen.kt",
        ).readText()
        val effect = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/MissedCallAcknowledgementEffect.kt",
        ).readText()

        assertTrue(notifications.contains("manager.activeNotifications"))
        assertTrue(notifications.contains("it.notification.channelId == CallNotificationManager.MISSED_CHANNEL_ID"))
        assertTrue(home.contains("AcknowledgeMissedCallsEffect(callLogViewModel)"))
        assertTrue(effect.contains("LifecycleEventEffect(Lifecycle.Event.ON_RESUME)"))
        assertTrue(effect.contains("viewModel.acknowledgeMissedCalls()"))
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
