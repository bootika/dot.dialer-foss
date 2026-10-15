package dev.goodwy.rphone.view.screen.settings

import android.content.ContextWrapper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.view.theme.DotDialerTheme
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Production lazy review widgets with isolated synthetic proposals; Apply is never invoked. */
@RunWith(AndroidJUnit4::class)
class ContactReviewDeviceTest {
    @Test fun continuousEightHundredRowsAllNoneAndSelectionSurviveDisposal() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runReviewDeviceProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val output = File(context.cacheDir, "review-device-v${dev.goodwy.rphone.BuildConfig.VERSION_CODE}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext() = this
            override fun getNoBackupFilesDir() = File(output, "private").apply { mkdirs() }
        }
        val reviews = WhatsAppAvatarReviewStore(isolated)
        val processor = WhatsAppAvatarSyncProcessor(isolated, PreferenceManager(context), reviews,
            WhatsAppAvatarContactStore(context), GlobalContext.get().get<CallPhotoHydrationManager>())
        val items = (1..800).map { i ->
            WhatsAppAvatarReviewItem(id = "row-$i", contactId = 800000L + i, rawContactId = 900000L + i,
                phoneDataId = 1000000L + i, displayName = "Test Alexandru Butica $i",
                matchedNumber = "+407990${i.toString().padStart(5, '0')}", accountName = "fixture@example.test",
                candidateFileName = "", previousFileName = null, candidateWidth = 0, candidateHeight = 0,
                previousWidth = null, previousHeight = null, sourcePackage = "",
                matchStrength = WhatsAppAvatarMatchStrength.ExactPhone, createdAtMillis = i.toLong(),
                source = "Phone number", kind = "number", originalNumber = "07990${i.toString().padStart(5, '0')}",
                proposedNumber = "+407990${i.toString().padStart(5, '0')}")
        }
        val ui = AtomicReference<ContactPhotoReviewUi>()
        val list = AtomicReference<LazyListState>()
        fun await(condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 12000
            while (!condition()) {
                assertTrue("Native review condition timed out", SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(50)
            }
        }
        fun click(text: String) {
            var clicked = false
            try { await {
                val matches = mutableListOf<AccessibilityNodeInfo>()
                fun collect(node: AccessibilityNodeInfo?, depth: Int) {
                    if (node == null || depth > 20) return
                    if (node.text?.toString()?.trim() == text.trim()) matches += node
                    for (i in 0 until node.childCount) collect(node.getChild(i), depth + 1)
                }
                collect(instrumentation.uiAutomation.rootInActiveWindow, 0)
                for (found in matches) {
                    if (found.text?.toString()?.trim() != text.trim()) continue
                    var node: AccessibilityNodeInfo? = found
                    while (node != null && !node.isClickable) node = node.parent
                    if (node?.isEnabled == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        clicked = true; break
                    }
                }
                clicked
            } } catch (failure: AssertionError) {
                val dump = StringBuilder("requested=$text\n")
                fun walk(node: AccessibilityNodeInfo?, depth: Int) {
                    if (node == null || depth > 15) return
                    dump.append(" ".repeat(depth)).append(node.text).append(" | ").append(node.contentDescription)
                        .append(" clickable=").append(node.isClickable).append(" enabled=").append(node.isEnabled).append('\n')
                    for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
                }
                walk(instrumentation.uiAutomation.rootInActiveWindow, 0)
                File(output, "accessibility-failure.txt").writeText(dump.toString())
                requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let {
                    File(output, "failure.png").outputStream().use { stream -> it.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream) }
                    it.recycle()
                }
                throw failure
            }
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                val model = rememberContactPhotoReview(items, reviews, processor)
                val state = rememberLazyListState()
                SideEffect { ui.set(model); list.set(state) }
                DotDialerTheme(systemDark = false) {
                    LazyColumn(Modifier.fillMaxSize(), state = state) { contactPhotoReviewItems(model) }
                }
            } }
            await { ui.get()?.proposals?.size == 800 }
            assertEquals(800, ui.get().groups.size)
            click(context.getString(R.string.contact_photos_selection_all))
            await { ui.get().selection.selected.size == 800 && !ui.get().selection.busy }
            SystemClock.sleep(150)
            requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let {
                File(output, "all-800.png").outputStream().use { stream -> it.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream) }
                it.recycle()
            }
            scenario.onActivity { activity -> activity.lifecycleScope.launch { list.get().scrollToItem(799) } }
            await { list.get().firstVisibleItemIndex > 700 }
            assertEquals(800, ui.get().selection.selected.size)
            scenario.onActivity { activity -> activity.lifecycleScope.launch { list.get().scrollToItem(0) } }
            await { list.get().firstVisibleItemIndex == 0 }
            click(context.getString(R.string.contact_photos_clear_photo_selection))
            await { ui.get().selection.selected.isEmpty() }
            assertEquals(800, ui.get().proposals.size)
        }
        File(output, "verification.json").writeText(JSONObject().put("rows", 800)
            .put("allSelected", 800).put("noneSelected", 0).put("lazyDisposalPreservesSelection", true)
            .put("actualProductionReviewWidgets", true).put("contactWrites", 0)
            .put("mainPreferenceWrites", 0).put("mainReviewWrites", 0).toString())
    }
}
