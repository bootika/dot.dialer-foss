package dev.goodwy.rphone.view.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiCallActionsContractTest {

    @Test
    fun `call waiting offers only answer and decline decisions`() {
        val screen = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt",
        ).readText()
        val components = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreenComponents.kt",
        ).readText()

        assertTrue(screen.contains("holdExistingAndAnswer"))
        assertTrue(screen.contains("declineIncomingAndResumeExisting"))
        assertTrue(screen.contains("existingCall.unhold()"))
        assertTrue(components.contains("R.string.answer"))
        assertTrue(components.contains("R.string.decline"))
        assertFalse(screen.contains("endExistingAndAnswer"))
        assertFalse(components.contains("IncomingWaitingActionTone.Replace"))
        assertFalse(components.contains("R.string.replace_call"))
    }

    @Test
    fun `second call card reports real telecom state and exposes swap and end`() {
        val screen = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt",
        ).readText()
        val secondaryCard = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/SecondaryCallCard.kt",
        ).readText()

        assertTrue(secondaryCard.contains("Call.STATE_ACTIVE -> stringResource(R.string.call_status_active)"))
        assertTrue(secondaryCard.contains("Call.STATE_HOLDING -> stringResource(R.string.call_status_on_hold)"))
        assertTrue(secondaryCard.contains("CallControlIcons.Swap"))
        assertTrue(secondaryCard.contains("onClick = onSwap"))
        assertTrue(secondaryCard.contains("R.string.end_call_short"))
        assertTrue(screen.contains("onSwap = ::swapWithOtherCall"))
        assertTrue(secondaryCard.contains("R.string.merge_calls"))
        assertTrue(secondaryCard.contains("enabled = canMerge"))
        assertTrue(screen.contains("onMerge = ::mergeWithOtherCall"))
        assertTrue(screen.contains("canMerge = canMergeCalls"))
    }

    @Test
    fun `merge is grouped with swap in the secondary card and keeps telecom checks`() {
        val screen = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt",
        ).readText()
        val actions = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/InCallActionLauncher.kt",
        ).readText()

        val card = repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/SecondaryCallCard.kt").readText()
        assertFalse(screen.contains("showsMergeAction"))
        assertTrue(card.contains("icon = DotDialerMergeIcon"))
        assertFalse(screen.contains("Icons.AutoMirrored.Rounded.CallMerge"))
        assertFalse(screen.contains("R.string.merge_calls"))
        assertTrue(card.contains("onClick = onMerge"))
        assertTrue(screen.contains("fun mergeWithOtherCall()"))
        assertTrue(actions.contains("second in first.conferenceableCalls"))
        assertTrue(actions.contains("first.conference(second)"))
        assertTrue(actions.contains("second.conference(first)"))
    }

    @Test
    fun `conference participants are not rendered as separate calls and add call remains available`() {
        val screen = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt",
        ).readText()
        val service = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/CallService.kt",
        ).readText()
        val relations = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/util/TelecomCallRelations.kt",
        ).readText()
        val selection = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/util/TelecomCallSelection.kt",
        ).readText()

        assertTrue(screen.contains("rememberOtherLogicalTelecomCall(allCalls, call)"))
        val observer = repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/TelecomCallSnapshot.kt").readText()
        assertTrue(observer.contains("selectOtherLogicalTelecomCall(calls, current)"))
        assertTrue(observer.contains("observed.forEach { it.unregisterCallback(callback) }"))
        assertTrue(screen.contains("else -> CallControlIcons.Add"))
        assertFalse(screen.contains("DotDialerAddCallIcon"))
        assertTrue(selection.contains("telecomConferenceRoot()"))
        assertTrue(selection.contains("logicalCalls.none { existing -> existing === root }"))
        assertTrue(selection.contains("candidate === currentRoot"))
        assertTrue(service.contains("onChildrenChanged"))
        assertTrue(service.contains("onParentChanged"))
        assertTrue(service.contains("onConferenceableCallsChanged"))
        assertTrue(relations.contains("thisParent == null && otherParent == null"))
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
