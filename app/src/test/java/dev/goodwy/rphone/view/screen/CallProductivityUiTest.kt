package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class CallProductivityUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @After fun clear(){stopKoin()}

    @Test fun freeKeepsEditableNotesAndRequestsUpgradeForIntegratedHistoryThenPowerShowsRealRows() {
        val context=RuntimeEnvironment.getApplication();var allowed = false
        var upgrade=0;var note by mutableStateOf("Discutăm oferta");var saved=0
        var dark by mutableStateOf(false);var font by mutableFloatStateOf(1f)
        val prefs=PreferenceManager(context,featureAllowed={allowed},onPlanRequired={upgrade++})
        val person=Contact("self",givenName="Paul",company="Atelier Nord",phoneNumbers=listOf("+40700000101"))
        val contacts=Proxy.newProxyInstance(IContactsRepository::class.java.classLoader,arrayOf(IContactsRepository::class.java)){proxy,method,args->
            when(method.name){"getChanges"->emptyFlow<Unit>();"getContactByNumber", "getContactByNumberInRegion"->person;"equals"->proxy===args?.firstOrNull();"hashCode"->System.identityHashCode(proxy);"toString"->"Contact fixture";else->error("Unexpected contact operation")}
        } as IContactsRepository
        val logs=Proxy.newProxyInstance(ICallLogRepository::class.java.classLoader,arrayOf(ICallLogRepository::class.java)){proxy,method,args->
            when(method.name){"getCallLogs"->listOf(CallLogEntry(1,"0700000101",null,2,1_700_000_000_000L,90,null,null));"equals"->proxy===args?.firstOrNull();"hashCode"->System.identityHashCode(proxy);else->error("Unexpected history mutation")}
        } as ICallLogRepository
        startKoin{modules(module{single{prefs};single<IContactsRepository>{contacts};single<ICallLogRepository>{logs}})}
        rule.setContent{CompositionLocalProvider(LocalDensity provides Density(1f,font)){
            DotDialerTheme(systemDark=dark){Surface(color=MaterialTheme.colorScheme.background){CallNoteHistoryPanel("Paul","+40700000101",note,{note=it},true,"RO"){saved++}}}
        }}
        rule.onNodeWithText(context.getString(R.string.call_history)).performScrollTo().performClick()
        assertEquals(1,upgrade)
        rule.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("Ofertă actualizată")
        rule.onNodeWithText("Paul").performClick()
        assertEquals("Ofertă actualizată",note);assertEquals(1,saved)
        capture("free-notes")
        rule.runOnIdle{allowed=true;prefs.notifyPlanChanged()}
        rule.waitUntil(5000){rule.onAllNodesWithText("1:30").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("1:30").performScrollTo().assertExists()
        for(mode in listOf(false,true))for(scale in listOf(1f,1.5f)){
            rule.runOnIdle{dark=mode;font=scale};rule.mainClock.advanceTimeBy(1200);rule.waitForIdle()
            capture("history-${if(mode)"dark" else "light"}-${(scale*100).toInt()}")
        }
        rule.runOnIdle{allowed=false;prefs.notifyPlanChanged()}
        rule.onNodeWithText("1:30").assertDoesNotExist()
        assertEquals("Ofertă actualizată",note)
    }

    @Test fun companyMenuUsesVisibleExactCompanyAndSelectingPersonDoesNotCall() {
        var allowed = false;var upgrade=0;var picked=""
        val context=RuntimeEnvironment.getApplication();val prefs=PreferenceManager(context,featureAllowed={allowed},onPlanRequired={upgrade++})
        val peer=Contact("peer",givenName="Ana",familyName="Popescu",company="Atelier Nord",jobTitle="Vânzări")
        val people=listOf(peer,Contact("self",givenName="Paul",company="Atelier Nord"),
            Contact("hidden",givenName="Ascuns",company="Atelier Nord",isHidden=true),Contact("other",givenName="Altă firmă",company="Atelier Nord Est"))
        val contacts=Proxy.newProxyInstance(IContactsRepository::class.java.classLoader,arrayOf(IContactsRepository::class.java)){proxy,method,args->
            when(method.name){"getChanges"->emptyFlow<Unit>();"getContactsFull"->people;"getContactById"->people.find{it.id==args?.firstOrNull()};"equals"->proxy===args?.firstOrNull();"hashCode"->System.identityHashCode(proxy);"toString"->"Contacts fixture";else->error("Unexpected contact mutation")}
        } as IContactsRepository
        startKoin{modules(module{single{prefs};single<IContactsRepository>{contacts}})}
        rule.setContent{DotDialerTheme{Column{ContactCompanyColleagues("Atelier Nord","self","Atelier Nord"){picked=it.id};Text("Apel conectat")}}}
        rule.onNodeWithText("Atelier Nord").performClick();assertEquals(1,upgrade)
        rule.onNodeWithText("Ana Popescu").assertDoesNotExist()
        rule.runOnIdle{allowed=true;prefs.notifyPlanChanged()}
        rule.waitUntil(5000){runCatching{rule.onNodeWithText("Atelier Nord").assertHasClickAction()}.isSuccess}
        rule.onNodeWithText("Atelier Nord").performClick()
        rule.onNodeWithText("Ana Popescu").assertExists();rule.onNodeWithText("Paul").assertDoesNotExist()
        rule.onNodeWithText("Ascuns").assertDoesNotExist();rule.onNodeWithText("Altă firmă").assertDoesNotExist()
        rule.onNodeWithText("Ana Popescu").performClick();rule.waitUntil(5000){picked=="peer"}
        rule.onNodeWithText("Apel conectat").assertExists()
    }

    private fun capture(name:String){
        rule.waitForIdle();rule.runOnIdle{
            val view=rule.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
            val file=File(System.getProperty("user.dir"),"build/reports/visual/quick-call-tools/$name.png")
            file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
}
