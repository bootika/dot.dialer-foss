package dev.goodwy.rphone.view.screen.settings

import android.accounts.Account
import android.app.Application
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.data.RawContactInfo
import dev.goodwy.rphone.modal.data.ContactSource
import dev.goodwy.rphone.view.components.MoveToAccountDialog
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class ContactMaintenanceRenderingTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @After fun clear() { stopKoin() }
    @Test fun `merge sources confirmation errors copy and retired options render consistently`() {
        val context=RuntimeEnvironment.getApplication()
        val prefs=PreferenceManager(context)
        val group=listOf(Contact(id="101",givenName="Test Alexandru Butica",phoneNumbers=listOf("0722123456")),
            Contact(id="202",givenName="Test Alexandru Butica",phoneNumbers=listOf("+40722123456")))
        val account=Account("test.alexandru.butica@example.com","com.google")
        var writes=0
        val repo=Proxy.newProxyInstance(IContactsRepository::class.java.classLoader,
            arrayOf(IContactsRepository::class.java)) { _,method,args -> when(method.name) {
                "getChanges" -> kotlinx.coroutines.flow.emptyFlow<Unit>()

            "getContacts","getContactsFull" -> group
            "getAvailableAccounts","getAvailableAccountsForMoving" -> listOf(account)
            "findDuplicates" -> listOf(group)
            "getRawContactsForContact" -> listOf(RawContactInfo(
                if(args!![0]=="101") "11" else "21",account.name,account.type,args[0] as String))
            "mergeContacts" -> { writes++; throw SecurityException("Test permission revoked") }
            "getContactById" -> group.firstOrNull {it.id==args!![0]}
            "getHiddenNumbers","getAllGroups" -> emptyList<Any>()
            else -> null
        } } as IContactsRepository
        startKoin { modules(module {single {prefs}; viewModel {ContactsViewModel(context,repo,prefs)} }) }
        val navigator=Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
            arrayOf(DestinationsNavigator::class.java)) { _,method,_ ->
                if(method.returnType==Boolean::class.javaPrimitiveType) true else null
            } as DestinationsNavigator
        var scene by mutableStateOf("merge")
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        val selected=mutableListOf<Pair<Account?,Boolean>>()
        val visualContext=object:android.content.ContextWrapper(rule.activity) {
            override fun getDisplay():android.view.Display =
                rule.activity.getSystemService(android.hardware.display.DisplayManager::class.java)
                    .getDisplay(android.view.Display.DEFAULT_DISPLAY)
        }
        rule.setContent {
            CompositionLocalProvider(LocalContext provides visualContext,LocalDensity provides Density(1f,font)) {
                DotDialerTheme(systemDark=dark) {
                    when(scene) {
                        "merge" -> ContactMergeDuplicatesScreen(navigator)
                        "sources" -> ContactVisibilityScreen(navigator)
                        "privacy" -> SettingsCategoryScreen(navigator,"privacy")
                        "unmerge" -> Surface {
                            MergedContactCard(navigator,MergedContactDisplay("101","Test Alexandru Butica",null,
                                listOf(ContactSource(null,null,rawContactId="11"),
                                    ContactSource(account.name,account.type,rawContactId="21")),group,true),
                                displayOrder=0,onUnmergeAll={false})
                        }
                        else -> MoveToAccountDialog(availableAccounts=listOf(account),onDismiss={},
                            onAccountSelected={a,p -> selected+=a to p})
                    }
                }
            }
        }
        val privateTitle=context.getString(R.string.private_contacts)
        for(mode in listOf(false,true)) for(scale in listOf(1f,1.5f)) {
            for(page in listOf("merge","unmerge","sources","privacy","move")) {
                rule.runOnIdle {scene=page; dark=mode; font=scale}
                rule.mainClock.advanceTimeBy(1200)
                rule.waitForIdle()
                rule.onNodeWithText(privateTitle).assertDoesNotExist()
                rule.onNodeWithText("Private (App Only)").assertDoesNotExist()
                if(page=="merge") {
                    rule.waitUntil(10_000) {rule.onAllNodesWithText(account.name,substring=true).fetchSemanticsNodes().size==2}
                    rule.onAllNodesWithText(account.name,substring=true).assertCountEquals(2)
                    rule.onNodeWithText(context.getString(R.string.merge_duplicates,2)).assertIsDisplayed()
                }
                if(page=="unmerge") {
                    rule.onNodeWithText(context.getString(R.string.contact_source_local)).assertIsDisplayed()
                    rule.onNodeWithText(account.name,substring=true).assertIsDisplayed()
                }
                capture("$page-${if(mode) "dark" else "light"}-font${(scale*100).toInt()}")
            }
        }
        rule.onNodeWithText("Local (Device Only)").performClick()
        rule.runOnIdle {assertEquals(listOf(null to false),selected)}
        rule.runOnIdle {scene="merge"; font=1f}
        rule.waitUntil(10_000) {rule.onAllNodesWithText(context.getString(R.string.merge_duplicates,2)).fetchSemanticsNodes().size==1}
        rule.onNodeWithText(context.getString(R.string.merge_duplicates,2)).performClick()
        rule.onNodeWithText(context.getString(R.string.contact_merge_confirm)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        assertEquals(0,writes)
        rule.onNodeWithContentDescription(context.getString(R.string.copy_diagnostics)).performClick()
        rule.onNodeWithText(context.getString(R.string.contact_merge_copy_notice)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.copy_diagnostics)).performClick()
        rule.onNode(hasText(context.getString(R.string.copy_diagnostics)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.runOnIdle {
            val report=context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
            assertTrue(report.contains("account_type=com.google"))
            assertTrue(report.contains("raw_id=11"))
        }
        rule.onNodeWithText(context.getString(R.string.merge_duplicates,2)).performClick()
        rule.onNode(hasText(context.getString(R.string.merge_duplicates,2)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.waitUntil(10_000) {writes==1 && rule.onAllNodesWithText(context.getString(R.string.contact_maintenance_error)).fetchSemanticsNodes().isNotEmpty()}
        rule.onAllNodesWithText(account.name,substring=true).assertCountEquals(2)
        capture("merge-provider-error")
    }
    private fun capture(name:String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val view=rule.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.let { window ->
                val dialog = window.decorView
                val canvas = Canvas(bitmap)
                if (dialog.height < bitmap.height) {
                    canvas.translate((bitmap.width-dialog.width)/2f,(bitmap.height-dialog.height)/2f)
                }
                dialog.draw(canvas)
            }
            val output=File(System.getProperty("user.dir"),"build/reports/visual/contact-maintenance")
            output.mkdirs()
            File(output,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
}
