package dev.goodwy.rphone.view.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InCallActionsContractTest {

    @Test
    fun `primary grid exposes one cycling audio action and disables unsupported controls`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt",
        ).readText()
        assertTrue(source.contains("if (!hasExpandedCallPanel) {"))
        val primaryGrid = source.substringAfter("if (!hasExpandedCallPanel) {")
            .substringBefore("val endInteraction")

        assertTrue(primaryGrid.contains("R.string.mute"))
        assertTrue(primaryGrid.contains("icon = callMuteActionIcon(isMuted)"))
        assertTrue(primaryGrid.contains("if (isMuted) R.string.unmute else R.string.mute"))
        assertTrue(primaryGrid.contains("isActive = isMuted"))
        assertTrue(primaryGrid.contains("callViewModel.toggleMute()"))
        assertTrue(primaryGrid.contains("R.string.keypad"))
        assertTrue(primaryGrid.contains("R.string.add_note"))
        assertTrue(primaryGrid.contains("audioRouteLabel"))
        assertTrue(primaryGrid.contains("R.string.add_call"))
        assertTrue(primaryGrid.contains("R.string.resume"))
        assertFalse(primaryGrid.contains("R.string.merge_calls"))
        assertFalse(primaryGrid.contains("showsMergeAction"))
        assertTrue(primaryGrid.contains("performAddCallOrResume"))
        assertTrue(primaryGrid.contains("callState == Call.STATE_HOLDING && otherCall == null"))
        assertTrue(primaryGrid.contains("isResumeAction = callState == Call.STATE_HOLDING"))
        assertTrue(primaryGrid.contains("callViewModel.cycleAudioRoute()"))
        assertTrue(primaryGrid.contains("enabled = controls.audioRoute"))
        assertTrue(primaryGrid.contains("enabled = controls.mute"))
        assertTrue(primaryGrid.contains("enabled = controls.keypad"))
        assertTrue(primaryGrid.contains("enabled = controls.more"))
        assertTrue(primaryGrid.contains("R.string.more"))
        assertTrue(
            "The balanced in-call grid must expose exactly six actions",
            Regex("CallScreenActionButton\\(").findAll(primaryGrid).count() == 6,
        )
        assertFalse(primaryGrid.contains("showAudioPicker"))
        assertFalse(primaryGrid.contains("callViewModel.setAudioRoute"))
        assertFalse(primaryGrid.contains("R.string.view_contact"))
    }

    @Test
    fun `add call control resumes the same held call instead of opening the dialer again`() {
        val launcher = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/InCallActionLauncher.kt",
        ).readText()
        val action = launcher.substringAfter("internal fun performAddCallOrResume")

        assertTrue(action.contains("callState == Call.STATE_HOLDING && !hasOtherCall"))
        assertTrue(action.contains("call.unhold()"))
        assertTrue(action.contains("launchAdditionalCall(context, call, callState, hasOtherCall)"))
    }

    @Test
    fun `note is promoted to the primary grid while more keeps contact actions`() {
        val callScreen = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt",
        ).readText()
        val morePanel = callScreen.substringAfter("if (showMore)")
            .substringBefore("if (showNoteWindow)")
        val contactDetails = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/ContactDetails.kt",
        ).readText()
        val actionLauncher = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/InCallActionLauncher.kt",
        ).readText()

        assertFalse(morePanel.contains("R.string.add_note"))
        assertTrue(morePanel.contains("R.string.view_contact"))
        assertTrue(morePanel.contains("openInAppContactDetails"))
        assertTrue(actionLauncher.contains("ACTION_VIEW_CONTACT"))
        assertFalse(morePanel.contains("R.string.add_call"))
        assertTrue(contactDetails.contains("NoteManager.getAllNotesForNumbers"))
        assertTrue(contactDetails.contains("ContactNotesCategoryCard"))
        val notesCard = repoFile("app/src/main/java/dev/goodwy/rphone/view/screen/ContactDetailsComponents.kt")
            .readText().substringAfter("internal fun ContactNotesCategoryCard")
            .substringBefore("internal fun CallBackgroundRow")
        assertTrue(notesCard.contains("R.string.call_notes"))
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
