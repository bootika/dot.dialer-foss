package dev.goodwy.rphone.controller

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Structural guard complements the real RuStore flavor compilation and Android lint. */
class RuStoreActivityOwnershipContractTest {
    @Test fun `billing view model does not retain an activity field`() {
        val source = repoFile("app/src/rustore/java/dev/goodwy/rphone/controller/RuStoreViewModel.kt").readText()
        assertFalse(Regex("(?:val|var)\\s+\\w+\\s*:\\s*(?:ComponentActivity|Activity)\\??").containsMatchIn(source))
        assertTrue(source.contains("onScreenActivityAvailable(activity: ComponentActivity)"))
        assertTrue(source.contains("handleNewIntent(activity.intent, activity)"))
        assertTrue(source.contains("activity.isFinishing || activity.isDestroyed"))
        assertFalse(source.contains("currentActivity"))
    }

    @Test fun `checkout and lifecycle use the current compose activity`() {
        val wrapper = repoFile("app/src/rustore/java/dev/goodwy/rphone/view/screen/settings/DonateScreen.kt").readText()
        assertTrue(wrapper.contains("PlanAccessContent"))
        val source = repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/settings/PlanBillingLifecycle.kt").readText() +
            repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/settings/PlanAccessContent.kt").readText()
        assertTrue(source.contains("LocalActivity.current as? ComponentActivity"))
        assertTrue(source.contains("DisposableEffect(activity)"))
        assertTrue(source.contains("rememberUpdatedState(loading)"))
        assertTrue(source.contains("val checkoutActivity=activity"))
        assertTrue(source.contains("activity?.let(helper::onScreenActivityAvailable)"))
        assertTrue(source.contains("removeObserver(listener)"))
    }

    private fun repoFile(path: String): File = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
        .take(6).map { File(it, path) }.first { it.exists() }
}
