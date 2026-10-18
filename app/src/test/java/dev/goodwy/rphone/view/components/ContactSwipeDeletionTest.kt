package dev.goodwy.rphone.view.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.lang.reflect.Proxy
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactSwipeDeletionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val deleted = mutableListOf<String>()
    @After fun cleanup() { stopKoin() }

    @Test fun `contact swipe asks once Cancel is inert and Confirm deletes only that contact`() {
        render(hasNumber = true)
        rule.onNodeWithTag("contact-row").performTouchInput { swipeLeft() }
        rule.onNodeWithText("Delete Contact").assertIsDisplayed()
        assertEquals(emptyList<String>(), deleted)
        captureConfirmation()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Delete Contact").assertDoesNotExist()
        assertEquals(emptyList<String>(), deleted)
        rule.onNodeWithTag("contact-row").performTouchInput { swipeRight() }
        rule.onNodeWithText("Delete Contact").assertIsDisplayed()
        rule.onNodeWithText("Delete").performClick()
        rule.waitUntil(5_000) { deleted == listOf("contact-42") }
        rule.onNodeWithText("Delete Contact").assertDoesNotExist()
    }
    @Test fun `numberless contact still supports delete with confirmation`() {
        render(hasNumber = false)
        rule.onNodeWithTag("contact-row").performTouchInput { swipeLeft() }
        rule.onNodeWithText("Delete Contact").assertIsDisplayed()
        assertEquals(emptyList<String>(), deleted)
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), deleted)
    }
    @Test fun `selection mode never starts swipe deletion`() {
        render(hasNumber = true, selectionMode = true)
        rule.onNodeWithTag("contact-row").performTouchInput { swipeLeft() }
        rule.onNodeWithText("Delete Contact").assertDoesNotExist()
        assertEquals(emptyList<String>(), deleted)
    }
    @Test fun quickGestureUsesTheExistingNumberChooserBeforeCopyingIdentity() {
        render(hasNumber = true, quickGesture = true, multiple = true)
        rule.onNodeWithText("Test Alexandru Butica").performTouchInput { longClick() }
        rule.onNodeWithText("Copy").assertDoesNotExist()
        val start=rule.onNodeWithText("Test Alexandru Butica").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {down(start)}
        rule.mainClock.advanceTimeBy(700);rule.waitForIdle()
        val copy=rule.onNodeWithText("Copy").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {moveTo(copy);up()}
        rule.onNodeWithText("Copy").assertDoesNotExist()
        rule.onNodeWithText("+40722000001").performClick()
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(android.content.ClipboardManager::class.java)
        val text = clipboard.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(text.contains("Test Alexandru Butica")); assertTrue(text.contains("+40722000001"))
        assertEquals(emptyList<String>(), deleted)
    }

    @Test fun quickTapOpensAndClosesThenUsesTheSameNumberChooser() {
        render(hasNumber=true,multiple=true,quickTap=true)
        val row=rule.onNodeWithText("Test Alexandru Butica")
        row.performClick();rule.onNodeWithText("Copy").assertIsDisplayed()
        row.performClick();rule.onNodeWithText("Copy").assertDoesNotExist()
        row.performClick();rule.onNodeWithText("Copy").performClick()
        rule.onNodeWithText("+40722000001").performClick()
        val clip=RuntimeEnvironment.getApplication().getSystemService(android.content.ClipboardManager::class.java)
        assertTrue(clip.primaryClip!!.getItemAt(0).text.toString().contains("+40722000001"))
        assertEquals(emptyList<String>(),deleted)
    }

    private fun captureConfirmation() = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val manager = org.robolectric.util.ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.view.WindowManagerGlobal"), "getInstance")
        val windows = org.robolectric.util.ReflectionHelpers.getField<List<android.view.View>>(manager, "mViews")
        windows.forEach { window ->
            val location = IntArray(2); window.getLocationOnScreen(location)
            canvas.save(); canvas.translate(location[0].toFloat(), location[1].toFloat()); window.draw(canvas); canvas.restore()
        }
        val file = java.io.File("build/reports/visual/contact-swipe-delete-confirmation.png")
        file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun render(hasNumber: Boolean, selectionMode: Boolean = false, quickGesture: Boolean = false, multiple: Boolean = false, quickTap: Boolean = false) {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        val contact = Contact("contact-42", givenName = "Test Alexandru Butica",
            phoneNumbers = if (multiple) listOf("+40722000000", "+40722000001") else if (hasNumber) listOf("+40722000000") else emptyList())
        val repo = Proxy.newProxyInstance(IContactsRepository::class.java.classLoader,
            arrayOf(IContactsRepository::class.java)) { _, method, args -> when (method.name) {
                "getChanges" -> kotlinx.coroutines.flow.emptyFlow<Unit>()
                "deleteContact" -> { deleted += args!![0] as String; Unit }
                "getContacts", "getContactsFull" -> listOf(contact)
                "getAvailableAccounts", "getAvailableAccountsForMoving", "getHiddenNumbers", "getAllGroups" -> emptyList<Any>()
                else -> null
            } } as IContactsRepository
        startKoin { modules(module { single { prefs }; viewModel { ContactsViewModel(context, repo, prefs) } }) }
        val navigator = Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _, method, _ ->
                if (method.returnType == Boolean::class.javaPrimitiveType) true else null
            } as DestinationsNavigator
        rule.setContent {
            DotDialerTheme(systemDark = false) {
                Box(Modifier.fillMaxWidth().testTag("contact-row")) {
                    ContactListItem(contact, navigator, selectionMode = selectionMode, displayOrder = 0,
                        rowPreferences = ContactListRowPreferences(true, 8, 8, false, "light", 0.5f, 18, 48, quickActionsEnabled = quickGesture, quickActionsOnTap = quickTap))
                }
            }
        }
    }
}
