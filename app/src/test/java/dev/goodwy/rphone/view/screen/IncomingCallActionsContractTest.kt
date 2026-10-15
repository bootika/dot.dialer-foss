package dev.goodwy.rphone.view.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallActionsContractTest {

    @Test
    fun `incoming screen exposes one shared message reminder and unknown caller action row`() {
        val callScreen = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt",
        ).readText()
        val components = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreenComponents.kt",
        ).readText()

        assertTrue(callScreen.contains("IncomingCallSecondaryActionCells("))
        assertTrue(callScreen.contains("showMore = isUnknownCaller"))
        assertTrue(callScreen.contains("contactId.isNullOrBlank()"))
        assertFalse(callScreen.contains("contactId?.startsWith(\"p\")"))
        assertTrue(components.contains("R.string.message"))
        assertTrue(components.contains("R.string.remind_me"))
        assertTrue(components.contains("R.string.more"))
        val secondaryActions = components.substringAfter("fun IncomingCallSecondaryActions(")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")
        assertTrue(secondaryActions.contains("CallScreenActionButton("))
        assertFalse(secondaryActions.contains("IncomingSecondaryActionButton("))
        assertFalse(components.substringAfter("fun DualActionHorizontalSlider(")
            .substringBefore(") {").contains("onMessage"))
        assertFalse(components.substringAfter("fun DefaultSwipeToAnswer(")
            .substringBefore(") {").contains("onMessage"))
    }

    @Test
    fun `quick response uses telecom reply when supported and retains a composer fallback`() {
        val callScreen = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt",
        ).readText()
        val transientActions = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreenTransientActions.kt",
        ).readText()

        assertTrue(callScreen.contains("CAPABILITY_RESPOND_VIA_TEXT"))
        assertTrue(callScreen.contains("call.reject(true, message)"))
        assertTrue(callScreen.contains("openQuickResponseComposer"))
        assertTrue(transientActions.contains("Intent.ACTION_SENDTO"))
        assertTrue(transientActions.contains("sms_body"))
    }

    @Test
    fun `call back reminder is durable local and opens the dialer`() {
        val scheduler = repoFile(
            "app/src/main/java/dev/goodwy/rphone/controller/util/CallBackReminderScheduler.kt",
        ).readText()
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(scheduler.contains("setAndAllowWhileIdle"))
        assertTrue(scheduler.contains("CallBackReminderReceiver"))
        assertTrue(scheduler.contains("NotificationChannel"))
        assertTrue(scheduler.contains("Intent.ACTION_DIAL"))
        assertTrue(scheduler.contains("POST_NOTIFICATIONS"))
        assertTrue(manifest.contains(".controller.util.CallBackReminderReceiver"))
        assertTrue(manifest.contains("android:exported=\"false\""))
    }

    @Test
    fun `default slider uses visible idle colors and strong committed colors`() {
        val components = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreenComponents.kt",
        ).readText().replace("\r\n", "\n")
        val defaultSlider = components.substringAfter("fun DefaultSwipeToAnswer(")
            .substringBefore("fun MoreItem(")

        assertTrue(defaultSlider.contains("idleAnswerColor = incomingActionSurfaceColor(color_call_button, 0f)"))
        assertTrue(defaultSlider.contains("idleDeclineColor = incomingActionSurfaceColor(color_call_end, 0f)"))
        assertTrue(defaultSlider.contains("val trackWidth = DEFAULT_INCOMING_SWIPE_TRACK_WIDTH_DP.dp"))
        assertTrue(defaultSlider.contains("DEFAULT_INCOMING_SWIPE_TRIGGER_FRACTION"))
        assertTrue(defaultSlider.contains("containerColor = if (declinePressed) color_call_end else idleDeclineColor"))
        assertTrue(defaultSlider.contains("incomingActionColor(\n                idleAnswerColor,\n                color_call_button"))
    }

    @Test
    fun `incoming and active calls share the same adaptive hero rhythm`() {
        val callScreen = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt",
        ).readText()
        val components = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreenComponents.kt",
        ).readText()
        val hero = callScreen.substringAfter("// --- APPROVED CALL HERO ---")
            .substringBefore("// --- UI CONTROLS ---")
        val incomingControls = callScreen.substringAfter("val useCustomUI = preferenceManager.getInt(")
            .substringBefore("if (isPocketModeCovered")
        val defaultSlider = components.substringAfter("fun DefaultSwipeToAnswer(")
            .substringBefore("fun MoreItem(")

        assertTrue(hero.contains("contentAlignment = Alignment.Center"))
        assertTrue(callScreen.contains("start = fullPhotoZoneTopPx"))
        assertTrue(callScreen.contains("AmbientDropletMiddleZone(middleVisualZoneTopPx, actionControlsTopPx)"))
        assertTrue(callScreen.contains("end = actionControlsTopPx"))
        assertTrue(hero.contains("screenSpacing.avatarTopGapDp.dp"))
        assertFalse(incomingControls.contains(".statusBarsPadding()"))
        assertTrue(incomingControls.contains("bottom = screenSpacing.controlsBottomPaddingDp.dp"))
        assertTrue(incomingControls.contains("screenSpacing.actionRowsGapDp.dp"))
        assertTrue(incomingControls.contains("actionGap = screenSpacing.hangupGapDp.dp"))
        assertTrue(defaultSlider.contains("verticalArrangement = Arrangement.spacedBy(actionGap)"))
        assertTrue(defaultSlider.contains("modifier = Modifier.padding(bottom = bottomPadding)"))
    }

    @Test
    fun `unknown caller block action uses Android block list and unwanted reject reason`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/IncomingCallTransientDialogs.kt",
        ).readText()

        assertTrue(source.contains("BlockedNumbersManager.block"))
        assertTrue(source.contains("Call.REJECT_REASON_UNWANTED"))
        assertTrue(source.contains("ContactsContract.Intents.Insert.ACTION"))
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
