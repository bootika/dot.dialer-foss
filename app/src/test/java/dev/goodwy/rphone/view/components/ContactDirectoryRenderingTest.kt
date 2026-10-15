package dev.goodwy.rphone.view.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
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
@Config(sdk=[36], application=Application::class, qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactDirectoryRenderingTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() { stopKoin() }
    @Test fun sharedDirectorySearchRailSortMenusAndNavigation() {
        val context=RuntimeEnvironment.getApplication()
        val prefs=PreferenceManager(context)
        val contacts=('A'..'Z').flatMap { c -> (1..4).map { n ->
            Contact(id="$c$n", givenName="$c Test $n", familyName="Butica", phoneNumbers=listOf("07220000$n"))
        } } + Contact(id="symbol", givenName="123 Test", phoneNumbers=emptyList())
        val repo=Proxy.newProxyInstance(IContactsRepository::class.java.classLoader,
            arrayOf(IContactsRepository::class.java)) { _,m,_ -> when(m.name) {
                "getContacts","getContactsFull" -> contacts
                "getAvailableAccounts","getAvailableAccountsForMoving","getHiddenNumbers","getAllGroups" -> emptyList<Any>()
                else -> null
            } } as IContactsRepository
        startKoin { modules(module { single {prefs}; viewModel {ContactsViewModel(context,repo,prefs)} }) }
        var navigations=0
        val navigator=Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _,m,_ ->
                if(m.name=="navigate") navigations++
                if(m.returnType==Boolean::class.javaPrimitiveType) true else null
            } as DestinationsNavigator
        val directoryState = androidx.compose.foundation.lazy.LazyListState()
        var query by mutableStateOf("")
        var descending by mutableStateOf(false)
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,font)) {
                DotDialerTheme(systemDark=dark) {
                    androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                        ContactDirectoryContent(contacts,query,0,navigator,Modifier.fillMaxSize(),listState=directoryState,descending=descending,
                            headerContent={ContactDirectoryHeader(query,{query=it})})
                    }
                }
            }
        }
        rule.onNodeWithText("A Test 1 Butica").assertIsDisplayed().performClick()
        assertEquals(1,navigations)
        rule.onNodeWithText("M",useUnmergedTree=true).performClick()
        rule.onNodeWithText("M Test 1 Butica").assertIsDisplayed()
        rule.onNodeWithText("M Test 1 Butica").performTouchInput { longClick() }
        rule.onNodeWithText("Edit Contact").assertIsDisplayed()
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.runOnIdle { query="Z Test 3" }
        rule.onNodeWithText("Z Test 3 Butica").assertIsDisplayed()
        rule.onNodeWithText("M Test 1 Butica").assertDoesNotExist()
        rule.runOnIdle { query="unmatched value" }
        rule.onNodeWithText("No results").assertIsDisplayed()
        rule.runOnIdle { query=""; descending=true }
        rule.onNodeWithText("A",useUnmergedTree=true).performClick()
        rule.onNodeWithText("A Test 4 Butica").assertIsDisplayed()
        rule.onNode(hasAnyDescendant(hasText("A")) and isSelected(), useUnmergedTree=true).assertExists()
        for(theme in listOf(false,true)) for(scale in listOf(1f,1.5f)) {
            rule.runOnIdle { dark=theme; font=scale }
            rule.waitForIdle()
            rule.runOnIdle {
                val view=rule.activity.window.decorView
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file=File("build/reports/visual/contact-directory-d84/${if(theme) "dark" else "light"}-${(scale*100).toInt()}.png")
                file.parentFile!!.mkdirs();file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
        }
    }

    @Test fun alphabetTouchUsesFullWidthAndUpdatedCallbacksAndEndsOnCancel() {
        val selections=mutableListOf<Char>()
        var end=0
        var callbackVersion by mutableIntStateOf(0)
        rule.setContent {
            DotDialerTheme {
                Box(Modifier.fillMaxSize()) {
                    AlphabetSideBar(('A'..'Z').toList()+'#',null,Modifier.testTag("rail"),
                        onLetterSelected={selections+=if(callbackVersion==0) it else it.lowercaseChar()},
                        onDragEnd={end++})
                }
            }
        }
        rule.onNodeWithTag("rail").performTouchInput { click(androidx.compose.ui.geometry.Offset(1f,8f+18f*12+9f)) }
        assertEquals('M',selections.last())
        rule.runOnIdle { callbackVersion=1 }
        rule.onNodeWithTag("rail").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(23f,17f));moveTo(androidx.compose.ui.geometry.Offset(23f,8f+18f*25+9f));cancel()
        }
        assertEquals('z',selections.last())
        assertEquals(2,end)
    }
    @Test fun sharedOriginalHeaderQueryAndClearWork() {
        startKoin { modules(module { single { PreferenceManager(RuntimeEnvironment.getApplication()) } }) }
        var query by mutableStateOf("")
        rule.setContent {
            DotDialerTheme {
                ContactDirectoryHeader(query,{query=it})
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("Test Alexandru Butica")
        assertEquals("Test Alexandru Butica",query)
        rule.onNodeWithContentDescription("Clear").performClick()
        assertEquals("",query)
        rule.onNodeWithContentDescription("Filter results").assertDoesNotExist()
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }
}
