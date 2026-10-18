package dev.goodwy.rphone.view.screen

import android.app.Application
import android.content.ContextWrapper
import android.provider.ContactsContract
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.activity.ComponentActivity
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.test.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.*
import dev.goodwy.rphone.modal.repository.ContactsRepository
import dev.goodwy.rphone.modal.repository.ContactEditPersistenceAuditTest
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import java.util.concurrent.ConcurrentHashMap
import org.junit.*
import org.junit.Assert.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class ContactEditFlowTest {
    // StateRestorationTester still accepts the legacy rule, not the v2 wrapper.
    @Suppress("DEPRECATION")
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private lateinit var provider: ContactEditPersistenceAuditTest.AuditContactsProvider
    private lateinit var repository: ContactsRepository
    private var createdViewModel: ContactsViewModel? = null
    private val screenLoads = ConcurrentHashMap.newKeySet<Job>()
    private var holdScreenLoad: CompletableDeferred<Unit>? = null
    private var paidAllowed = true
    private var editorClosed = false
    private var navigations=0
    private val navigator=Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
        arrayOf(DestinationsNavigator::class.java)) { proxy, method, args ->
        when(method.name) { "navigateUp" -> { navigations++; true }; "toString" -> "Test navigation"
            "equals" -> proxy === args?.firstOrNull(); "hashCode" -> System.identityHashCode(proxy); else -> null }
    } as DestinationsNavigator

    @Before fun setup() {
        val context=RuntimeEnvironment.getApplication()
        provider=ContactEditPersistenceAuditTest.AuditContactsProvider().also { it.onCreate() }
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider)
        provider.raw("11","101")
        repository=ContactsRepository(context,ContactEditPersistenceAuditTest.MemoryPrivateDao())
        runBlocking { repository.updateRawContact("11",Contact(id="101",givenName="Test Alexandru Butica",company="Studio",
            nickname="Alex",phoneNumbers=listOf("07700900123"),
            phoneDetails=listOf(ContactPhoneDetail(number="07700900123",isPrimary=true)),notes="Notă existentă")) }
        val screenRepository = object : IContactsRepository by repository {
            override suspend fun getContactById(contactId: String): Contact? {
                currentCoroutineContext()[Job]?.let(screenLoads::add)
                holdScreenLoad?.await()
                return repository.getContactById(contactId)
            }
        }
        val prefs=PreferenceManager(context,featureAllowed={paidAllowed})
        // Construct the VM when the composition's main dispatcher is installed, not before setContent.
        startKoin { modules(module { single { prefs }; single {
            ContactsViewModel(context,screenRepository,prefs).also { createdViewModel=it }
        } }) }
    }

    private fun closeEditor() {
        if (editorClosed) return
        val lifetime = createdViewModel?.viewModelScope?.coroutineContext?.get(Job)
        // Close the owner, not just its content: the screen load belongs to LaunchedEffect,
        // while account/provider work belongs to the VM. Both must finish before SQLite closes.
        rule.activityRule.scenario.close()
        rule.waitUntil(10000) {
            lifetime?.isCompleted != false && screenLoads.all { it.isCompleted }
        }
        assertTrue("Editor ViewModel must be cancelled before provider teardown", lifetime?.isCancelled != false)
        assertTrue("Editor screen loads must finish before provider teardown", screenLoads.all { it.isCompleted })
        editorClosed = true
    }

    @After fun close() {
        try {
            closeEditor()
        } finally {
            org.robolectric.shadows.ShadowLog.getLogsForTag("ContactEdit")
                .filter { it.msg == "Unable to load editable contact" }
                .forEach { println(it.msg); it.throwable?.printStackTrace() }
            stopKoin()
            provider.close()
        }
    }

    @Test fun freePlanCanAddPhoneWithoutEditingOrLosingStoredPaidMetadata() {
        val prefs=org.koin.java.KoinJavaComponent.get<PreferenceManager>(PreferenceManager::class.java)
        runBlocking { dev.goodwy.rphone.controller.util.PhoneExtensions.replace(prefs,emptyList(),
            mapOf("07700900123" to dev.goodwy.rphone.controller.util.PhoneExtension("204"))) }
        paidAllowed=false;prefs.notifyPlanChanged()
        val visual=visualContext()
        rule.setContent { CompositionLocalProvider(LocalContext provides visual) {
            DotDialerTheme { ContactEditScreen(contactId="101",navigator=navigator) }
        } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("Studio").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("contactEditFields").performScrollToNode(hasText(visual.getString(dev.goodwy.rphone.R.string.add_phone)))
        rule.onNodeWithText(visual.getString(dev.goodwy.rphone.R.string.add_phone)).performClick()
        rule.onAllNodes(hasSetTextAction() and hasText("Mobile")).onLast().performTextInput("07700900124")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(10000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();navigations==1 }
        assertEquals(setOf("07700900123","07700900124"),runBlocking { repository.getRawContactData("11") }!!.phoneNumbers.toSet())
        assertEquals("204",dev.goodwy.rphone.controller.util.PhoneExtensions.find(prefs,"07700900123")?.digits)
    }

    @Test fun localExtensionDraftCommitsWithContactAndKeepsProviderPhoneUnchanged() {
        val visual=visualContext()
        rule.setContent { CompositionLocalProvider(LocalContext provides visual) {
            DotDialerTheme { ContactEditScreen(contactId="101",navigator=navigator) }
        } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("Studio").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("contactEditFields").performScrollToNode(hasText("Extension"))
        rule.onNode(hasSetTextAction() and hasText("Extension")).performTextInput("204#")
        val prefs=org.koin.java.KoinJavaComponent.get<PreferenceManager>(PreferenceManager::class.java)
        assertNull(dev.goodwy.rphone.controller.util.PhoneExtensions.find(prefs,"07700900123"))
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(10000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); navigations==1 }
        assertEquals("204#",dev.goodwy.rphone.controller.util.PhoneExtensions.find(prefs,"07700900123")?.digits)
        assertEquals(listOf("07700900123"),runBlocking { repository.getRawContactData("11") }?.phoneNumbers)
    }

    @Test fun pendingEditorLoadIsCancelledBeforeProviderTeardown() {
        holdScreenLoad = CompletableDeferred()
        val visual = visualContext()
        rule.setContent { CompositionLocalProvider(LocalContext provides visual) {
            DotDialerTheme { ContactEditScreen(contactId="101", navigator=navigator) }
        } }
        rule.waitUntil(10000) { screenLoads.isNotEmpty() }
        assertTrue(screenLoads.any { it.isActive })
        closeEditor()
        assertTrue(screenLoads.all { it.isCancelled && it.isCompleted })
        assertEquals("Studio", runBlocking { repository.getRawContactData("11") }?.company)
        assertEquals(0, navigations)
    }

    @Test fun focusedEditorFieldStaysAboveReportedKeyboardAndWindowPolicyRestores() {
        val visual=visualContext()
        var editing by androidx.compose.runtime.mutableStateOf(true)
        lateinit var insetView: android.view.View
        val original=android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        rule.activity.window.setSoftInputMode(original)
        rule.setContent { CompositionLocalProvider(LocalContext provides visual) {
            val view=androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.SideEffect { insetView=view }
            DotDialerTheme { if(editing) ContactEditScreen(contactId="101",navigator=navigator) }
        } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("First name").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("contactEditFields").performScrollToNode(hasText("First name"))
        val first=rule.onNode(hasSetTextAction() and hasText("First name"))
        first.performClick()
        rule.mainClock.advanceTimeBy(1000)
        fun keyboard(height:Int) {
            rule.runOnIdle {
                val insets=androidx.core.view.WindowInsetsCompat.Builder()
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),androidx.core.graphics.Insets.of(0,0,0,height))
                    .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(),height>0).build()
                androidx.core.view.ViewCompat.dispatchApplyWindowInsets(insetView,insets)
            }
            rule.mainClock.advanceTimeBy(1000)
        }
        keyboard(400)
        first.assertIsFocused()
        assertTrue(first.fetchSemanticsNode().boundsInRoot.bottom<=400f)
        keyboard(480)
        assertTrue(first.fetchSemanticsNode().boundsInRoot.bottom<=320f)
        rule.onNodeWithTag("contactEditFields").performScrollToNode(hasText("Company"))
        val company=rule.onNode(hasSetTextAction() and hasText("Company"))
        company.performClick();rule.mainClock.advanceTimeBy(1000)
        company.assertIsFocused()
        assertTrue(company.fetchSemanticsNode().boundsInRoot.bottom<=320f)
        keyboard(0)
        assertEquals(0,navigations)
        rule.runOnIdle { editing=false }
        rule.runOnIdle { assertEquals(original,rule.activity.window.attributes.softInputMode) }
    }

    @Test fun emptyPhotoSurfacesAndPlusLaunchSamePickersWithoutSaving() = checkPhotoActions()
    @Test @Config(qualifiers="w800dp-h360dp-land-mdpi")
    fun landscapeEmptyPhotoSurfacesAndPlusLaunchSamePickersWithoutSaving() = checkPhotoActions()
    private fun checkPhotoActions() {
        val visual=visualContext()
        rule.setContent { CompositionLocalProvider(LocalContext provides visual) {
            DotDialerTheme { ContactEditScreen(contactId="101",navigator=navigator) }
        } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("Test Alexandru Butica").fetchSemanticsNodes().isNotEmpty() }
        val shadow=org.robolectric.Shadows.shadowOf(rule.activity)
        fun launchAndCancel(node: SemanticsNodeInteraction): android.content.Intent {
            node.performClick()
            rule.waitForIdle()
            val started=requireNotNull(shadow.nextStartedActivityForResult)
            rule.runOnIdle { rule.activity.activityResultRegistry.dispatchResult(
                started.requestCode,android.app.Activity.RESULT_CANCELED,null) }
            rule.waitForIdle()
            return started.intent
        }
        val photo=launchAndCancel(rule.onNodeWithTag("contactEditPhotoSurface"))
        val photoPlus=launchAndCancel(rule.onNodeWithContentDescription(rule.activity.getString(dev.goodwy.rphone.R.string.add_photo)))
        assertEquals(photo.action,photoPlus.action)
        assertEquals(photo.type,photoPlus.type)
        val background=launchAndCancel(rule.onNodeWithTag("contactEditBackgroundSurface"))
        val label=rule.activity.getString(dev.goodwy.rphone.R.string.contact_call_background_set)
        val backgroundPlus=launchAndCancel(rule.onNodeWithContentDescription(label))
        assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT,background.action)
        assertEquals(background.action,backgroundPlus.action)
        assertEquals(background.type,backgroundPlus.type)
        assertEquals(0,navigations)
        assertNull(runBlocking { repository.getRawContactData("11") }?.photoUri)
        rule.onNodeWithTag("contactEditPhotoSurface").assertHasClickAction()
        rule.onNodeWithTag("contactEditBackgroundSurface").assertHasClickAction()
        fun choose(node:SemanticsNodeInteraction,uri:String) {
            node.performClick()
            rule.waitForIdle()
            val started=requireNotNull(shadow.nextStartedActivityForResult)
            rule.runOnIdle { rule.activity.activityResultRegistry.dispatchResult(started.requestCode,
                android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.parse(uri))) }
            rule.waitForIdle()
        }
        choose(rule.onNodeWithTag("contactEditPhotoSurface"),"content://test-images/portrait")
        rule.onNodeWithTag("contactEditPhotoSurface").assert(hasClickAction().not())
        choose(rule.onNodeWithTag("contactEditBackgroundSurface"),"content://test-images/background")
        rule.onNodeWithTag("contactEditBackgroundSurface").assertDoesNotExist()
        assertNull(runBlocking { repository.getRawContactData("11") }?.photoUri)
        assertEquals(0,navigations)
    }

    @Test fun favoriteToggleKeepsSurroundingPixelsUnchangedAndCommitsOnlyOnSave() {
        val visual=visualContext()
        rule.setContent { CompositionLocalProvider(LocalContext provides visual) {
            DotDialerTheme { ContactEditScreen(contactId="101",navigator=navigator) }
        } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("Studio").fetchSemanticsNodes().isNotEmpty() }
        val favorite=rule.onNodeWithTag("contactEditFavorite")
        fun screenshot(name:String): android.graphics.Bitmap {
            lateinit var bitmap:android.graphics.Bitmap
            rule.runOnIdle {
                val view=rule.activity.window.decorView
                bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                val folder=java.io.File(System.getProperty("user.dir"),"build/reports/visual/contact-photo-actions").apply { mkdirs() }
                java.io.File(folder,"$name.png").outputStream().use {
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
                }
            }
            return bitmap
        }
        val bounds=favorite.fetchSemanticsNode().boundsInWindow
        val before=screenshot("favorite-off")
        favorite.performClick()
        rule.mainClock.advanceTimeBy(1000)
        val after=screenshot("favorite-on")
        for(y in bounds.top.toInt() until bounds.bottom.toInt()) for(x in bounds.left.toInt() until bounds.right.toInt()) {
            // Exclude the centered24dp star; verify that the surrounding disc area stays unchanged.
            if(y<bounds.top+8 || y>=bounds.bottom-8 || x<bounds.left+3 || x>=bounds.right-3)
                assertEquals(before.getPixel(x,y),after.getPixel(x,y))
        }
        assertFalse(runBlocking { repository.getRawContactData("11") }!!.isFavorite)
        favorite.performClick()
        assertEquals(0,navigations)
    }
    private fun visualContext() = object : ContextWrapper(rule.activity) {
        override fun getDisplay(): Display = rule.activity.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    }

    @Test fun `Home edit loads full source and failed save keeps draft for successful retry`() {
        val visualContext=visualContext()
        rule.setContent { CompositionLocalProvider(LocalContext provides visualContext) {
            DotDialerTheme { ContactEditScreen(contactId="101",navigator=navigator) }
        } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("Studio").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Studio").performTextReplacement("Atelier")
        provider.failWrites=true
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("Could not save all changes. Your edits are still here; please try again.").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertEquals(0,navigations)
        rule.onNodeWithText("Atelier").assertExists()
        provider.failWrites=false
        rule.onNodeWithText("Save").assertIsEnabled().performClick()
        try { rule.waitUntil(10000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            navigations==1
        } }
        catch(error: Throwable) {
            org.robolectric.shadows.ShadowLog.getLogsForTag("ContactEdit").forEach { println(it.msg); it.throwable?.printStackTrace() }
            println("Persisted company="+runBlocking { repository.getRawContactData("11")?.company })
            println("Navigation count="+navigations)
            val vm=org.koin.core.context.GlobalContext.get().get<ContactsViewModel>()
            println("Contact revision="+vm.contactRevision.value)
            throw error
        }
        val saved=runBlocking { requireNotNull(repository.getRawContactData("11")) }
        Assert.assertEquals("Atelier",saved.company)
        Assert.assertEquals("Alex",saved.nickname)
        Assert.assertEquals("Notă existentă",saved.notes)
        Assert.assertTrue(saved.phoneDetails.single().isPrimary)
    }

    @Test fun `state restoration retains scalar draft instead of replacing it with provider snapshot`() {
        val restoration=StateRestorationTester(rule)
        val visualContext=visualContext()
        restoration.setContent { CompositionLocalProvider(LocalContext provides visualContext) {
            DotDialerTheme { ContactEditScreen(contactId="101",navigator=navigator) }
        } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("Studio").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Studio").performTextReplacement("Atelier")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Atelier").assertExists()
        Assert.assertEquals("Studio",runBlocking { requireNotNull(repository.getRawContactData("11")).company })
        Assert.assertEquals(0,navigations)
    }

    @Test fun `Add field exposes all five optional fields and persists their input`() {
        runBlocking { repository.updateRawContact("11",requireNotNull(repository.getRawContactData("11")).copy(nickname="")) }
        val visualContext=visualContext()
        val editorGeneration=androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent { CompositionLocalProvider(LocalContext provides visualContext) {
            DotDialerTheme { androidx.compose.runtime.key(editorGeneration.intValue) {
                ContactEditScreen(contactId="101",navigator=navigator)
            } }
        } }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("contactEditFields").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("contactEditFields").performScrollToNode(hasText("Add field"))
        rule.onNodeWithText("Add field").performClick()
        for(label in listOf("Prefix","Middle name","Suffix","Nickname","Job title")) {
            rule.onNodeWithText(label).performClick()
        }
        rule.onNodeWithText("Prefix").performTouchInput {
            swipe(center,center+Offset(0f,600f),durationMillis=200)
        }
        for((label,value) in listOf("Prefix" to "Dr", "Middle name" to "Mihai", "Suffix" to "Jr",
            "Nickname" to "Alex", "Job title" to "Designer")) {
            rule.onNodeWithTag("contactEditFields").performScrollToNode(hasText(label))
            rule.onNode(hasSetTextAction() and hasText(label)).performTextReplacement(value)
        }
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(10000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            navigations==1
        }
        val saved=runBlocking { requireNotNull(repository.getRawContactData("11")) }
        Assert.assertEquals("Dr",saved.namePrefix); Assert.assertEquals("Mihai",saved.middleName)
        Assert.assertEquals("Jr",saved.nameSuffix); Assert.assertEquals("Alex",saved.nickname)
        Assert.assertEquals("Designer",saved.jobTitle)
        // A fresh editor must reconstruct the optional fields from the saved provider, not the old draft.
        rule.runOnIdle { editorGeneration.intValue++ }
        rule.waitUntil(10000) { rule.onAllNodesWithText("Dr").fetchSemanticsNodes().isNotEmpty() }
        for(value in listOf("Dr","Mihai","Jr","Alex","Designer")) {
            rule.onNodeWithTag("contactEditFields").performScrollToNode(hasText(value))
            rule.onNode(hasSetTextAction() and hasText(value)).assertExists()
        }
    }

    @Test fun `discard changes exits without writing provider`() {
        val visualContext=visualContext()
        rule.setContent { CompositionLocalProvider(LocalContext provides visualContext) {
            DotDialerTheme { ContactEditScreen(contactId="101",navigator=navigator) }
        } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("Studio").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Studio").performTextReplacement("Atelier")
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText("Cancel").performClick()
        Assert.assertEquals(1,navigations)
        Assert.assertEquals("Studio",runBlocking { requireNotNull(repository.getRawContactData("11")).company })
    }
}
