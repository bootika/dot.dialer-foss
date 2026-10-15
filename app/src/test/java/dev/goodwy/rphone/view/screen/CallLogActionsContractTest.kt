package dev.goodwy.rphone.view.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogActionsContractTest {

    @Test
    fun `history call action uses the shared SIM preference route`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallLogs.kt",
        ).readText()
        val callAction = source
            .substringAfter("onCallClick = {")
            .substringBefore("selected =")

        assertTrue(callAction.contains("placeCallWithSimPreference(context, lg.number, simPref)"))
        assertTrue(callAction.contains("pendingNumber = lg.number"))
        assertTrue(callAction.contains("showSimPicker = true"))
        assertFalse(callAction.contains("READ_PHONE_STATE"))
        assertFalse(callAction.contains("callCapablePhoneAccounts"))
    }

    @Test
    fun `call log selection exposes only block and delete actions`() {
        val home = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/UnifiedHomeScreen.kt",
        ).readText()
        val callLogs = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallLogs.kt",
        ).readText()
        val actions = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/CallLogTile.kt",
        ).readText()

        val recentSelectionBar = home
            .substringAfter("selectedRecentIds.isNotEmpty()")
            .substringBefore("selectedContactIds.isNotEmpty()")

        val batchActionBar = actions
            .substringAfter("fun BatchCallLogActionBar(")
            .substringBefore("if (showDeleteConfirm)")

        assertTrue(recentSelectionBar.contains("onDelete ="))
        assertTrue(recentSelectionBar.contains("onBlock ="))
        assertFalse(recentSelectionBar.contains("onCallLogs ="))
        assertFalse(callLogs.contains("onClearAll ="))
        assertFalse(batchActionBar.contains("MoreVert"))
        assertFalse(batchActionBar.contains("DropdownMenu("))
        assertFalse(batchActionBar.contains("onShare"))
        assertFalse(batchActionBar.contains("onCallLogs"))
        assertFalse(batchActionBar.contains("onClearAll"))
    }

    @Test
    fun `call log selection uses restrained surfaces`() {
        val actions = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/CallLogTile.kt",
        ).readText()
        val rows = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/RillExpressiveUI.kt",
        ).readText()
        val colors = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/theme/CallLogSelectionColor.kt",
        ).readText()

        val selectionBar = actions
            .substringAfter("fun BatchCallLogActionBar(")
            .substringBefore("if (showDeleteConfirm)")

        assertTrue(selectionBar.contains("surfaceContainerHigh"))
        assertFalse(selectionBar.contains("color = MaterialTheme.colorScheme.primaryContainer"))
        assertTrue(rows.contains("color = if (isSelected) callLogSelectionColor else cardColor"))
        assertTrue(colors.contains("get() = MaterialTheme.colorScheme.surfaceContainerHigh"))
        assertFalse(colors.contains("MaterialTheme.colorScheme.primary"))

        val home = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/UnifiedHomeScreen.kt",
        ).readText()
        assertTrue(home.contains("color = if (selected) callLogSelectionColor"))
    }

    @Test
    fun `long pressing a recent avatar starts row selection`() {
        val home = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/UnifiedHomeScreen.kt",
        ).readText()
        val recentRow = home
            .substringAfter("avatarColorIndex = avatarColorPlan.recentIndices[index]")
            .substringBefore("isLastInSection = index == recentRows.lastIndex")

        assertTrue(recentRow.contains("onAvatarLongClick ="))
        assertTrue(home.contains("selectedRecentIds + callEntry.id"))
        assertTrue(home.contains("HomeSelectableAvatar("))

        val modifier = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/HomeClickableModifier.kt",
        ).readText()
        assertTrue(modifier.contains("circularCombinedClickable("))
        assertTrue(modifier.contains("onLongClick = { trigger(onLongClick) }"))
        assertTrue(modifier.contains("performAppHaptic"))

        val avatar = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/HomeSelectableAvatar.kt",
        ).readText()
        assertTrue(avatar.contains("Icons.Rounded.Check"))
        assertTrue(avatar.contains("MaterialTheme.colorScheme.primary"))
        assertTrue(avatar.contains("if (selected)"))
    }

    @Test
    fun `contact history actions use strong centered controls`() {
        val callLogs = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallLogs.kt",
        ).readText()
        val paging = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallHistoryPaging.kt",
        ).readText()
        val bottomActions = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/ContactHistoryBottomActions.kt",
        ).readText()

        assertTrue(paging.contains("containerColor = color_call_button"))
        assertTrue(paging.contains("heightIn(min = 58.dp)"))
        assertFalse(paging.contains("FilledTonalButton"))

        assertTrue(bottomActions.contains("contentAlignment = Alignment.Center"))
        assertTrue(bottomActions.contains(".fillMaxWidth()"))
        assertTrue(bottomActions.contains(".weight(1f)"))
        assertTrue(bottomActions.contains(".height(54.dp)"))
        assertTrue(bottomActions.contains("label = stringResource(R.string.call)"))
        assertTrue(bottomActions.contains("label = stringResource(R.string.message)"))
        assertTrue(callLogs.contains("ContactHistoryBottomActions("))
        assertFalse(callLogs.contains("ScrollToTopButton"))
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
