package dev.goodwy.rphone.controller

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallActivityEffectsContractTest {

    @Test
    fun `answer and hangup haptics depend only on call state transitions`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/CallActivity.kt",
        ).readText()
        val transitionEffect = source
            .substringAfter("LaunchedEffect(callState) {")
            .substringBefore("LaunchedEffect(session, awaitingOutgoingSession)")

        assertTrue(transitionEffect.contains("HapticIntent.CONFIRM"))
        assertTrue(transitionEffect.contains("HapticIntent.REJECT"))
        assertFalse(transitionEffect.contains("audioState"))
        assertFalse(transitionEffect.contains("settingsState"))
    }

    @Test
    fun `identity lookup and cache stay outside the activity orchestrator`() {
        val activity = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/CallActivity.kt",
        ).readText()
        val resolver = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/CallIdentityResolver.kt",
        ).readText()

        assertTrue(activity.contains("identityResolver.rememberIdentity"))
        assertFalse(activity.contains("contactsRepo.getContactByNumber"))
        assertTrue(resolver.contains("contactsRepository.getContactByNumber"))
        assertTrue(resolver.contains("CallBackgroundStore.resolveResult"))
    }

    @Test
    fun `full screen frosted call uses whole-window blur-behind API`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/CallActivity.kt",
        ).readText()
        val frostedWindow = source
            .substringAfter("private fun applyFrostedWindow")
            .substringBefore("override fun onNewIntent")

        assertTrue(frostedWindow.contains("setBlurBehindRadius"))
        assertTrue(frostedWindow.contains("FLAG_BLUR_BEHIND"))
        assertFalse(frostedWindow.contains("setBackgroundBlurRadius"))
    }

    @Test
    fun `call activity wakes the screen only after validating a presentable call`() {
        val activity = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/CallActivity.kt",
        ).readText()
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        val callActivityManifest = manifest
            .substringAfter("android:name=\".controller.CallActivity\"")
            .substringBefore("/>")
        val onCreate = activity
            .substringAfter("override fun onCreate(savedInstanceState: Bundle?)")
            .substringBefore("private fun isNightMode")

        assertFalse(callActivityManifest.contains("android:turnScreenOn=\"true\""))
        assertTrue(onCreate.contains("CallPresentationPolicy.shouldFinishBeforeRendering"))
        assertTrue(
            onCreate.indexOf("finishAndRemoveTask()") <
                onCreate.indexOf("turnScreenOnAndShowWhileLocked()"),
        )
        assertTrue(activity.contains("if (hasPresentableCall())"))
        assertFalse(activity.contains("override fun onAttachedToWindow()"))
    }

    @Test
    fun `waking a call screen cannot silently override the keep screen on preference`() {
        val activity = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/CallActivity.kt",
        ).readText()
        val wakeHelper = activity
            .substringAfter("private fun turnScreenOnAndShowWhileLocked()")
            .substringBefore("private fun applyKeepScreenOnPreference()")
        val preferenceHelper = activity
            .substringAfter("private fun applyKeepScreenOnPreference()")
            .substringBefore("private fun hasPresentableCall()")

        assertFalse(wakeHelper.contains("FLAG_KEEP_SCREEN_ON"))
        assertTrue(preferenceHelper.contains("KEY_KEEP_SCREEN_ON"))
        assertTrue(preferenceHelper.contains("addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"))
        assertTrue(preferenceHelper.contains("clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"))
        assertTrue(Regex("applyKeepScreenOnPreference\\(\\)").findAll(activity).count() >= 4)
        assertTrue(
            activity.substringAfter("override fun onResume()")
                .substringBefore("override fun onPause()")
                .contains("applyKeepScreenOnPreference()"),
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
