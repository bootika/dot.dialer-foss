package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import dev.goodwy.rphone.view.screen.settings.QuickActionGestureSetting
import dev.goodwy.rphone.view.theme.DotDialerTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36], application=Application::class, qualifiers="w360dp-h800dp-mdpi")
class QuickActionNativeUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val context get()=RuntimeEnvironment.getApplication()
    @After fun cleanup(){stopKoin()}

    @Test fun gestureSettingStartsOffAndFreeTapRequestsPlanWithoutEnabling() {
        var allowed=false;var upgrades=0
        val prefs=PreferenceManager(context,featureAllowed={allowed},onPlanRequired={upgrades++})
        runBlocking {prefs.restoreAllPreferences(emptyMap(),replacePortable=true)}
        rule.setContent {DotDialerTheme {Surface {QuickActionGestureSetting(prefs)}}}
        rule.onNode(isToggleable()).assertIsOff()
        rule.onNodeWithText(context.getString(R.string.quick_action_gesture)).performClick()
        assertTrue(upgrades>0);rule.onNode(isToggleable()).assertIsOff()
        allowed=true;prefs.notifyPlanChanged()
        rule.onNode(isToggleable()).performClick()
        rule.waitUntil(5000){prefs.getBoolean("home_quick_action_gesture",false)}
        rule.onNode(isToggleable()).assertIsOn()
        allowed=false;prefs.notifyPlanChanged();rule.onNode(isToggleable()).assertIsOff()
        allowed=true;prefs.notifyPlanChanged();rule.onNode(isToggleable()).assertIsOn()
    }

    @Test fun holdReleaseWithoutSelectionClosesWithoutAction() {
        var action=-1;var more=0;var taps=0
        startKoin {modules(module {single {PreferenceManager(context)}})}
        rule.setContent {DotDialerTheme {Surface {
            HomeResultRow("Test Alexandru Butica","0700000101",null,"0700000101",
                quickActionsEnabled=true,onQuickAction={action=it},swipeEnabled=false,
                swipeRightAction=0,swipeLeftAction=0,haptics=false,hapticsStrength="light",hapticsIntensity=.5f,
                onAction={},onAvatarClick={},onClick={taps++},onLongClick={more++},isLastInSection=true,contactNameSize=18,avatarSize=46)
        }}}
        for (move in listOf(0f,1f)) {
            val start=rule.onNodeWithText("Test Alexandru Butica").fetchSemanticsNode().boundsInRoot.center
            rule.onRoot().performTouchInput {down(start)}
            rule.mainClock.advanceTimeBy(700);rule.waitForIdle()
            rule.onNodeWithText(context.getString(R.string.copy_action)).assertIsDisplayed()
            rule.onRoot().performTouchInput {if(move>0)moveTo(start+Offset(move,0f));up()}
            rule.onNodeWithText(context.getString(R.string.copy_action)).assertDoesNotExist()
            assertEquals(-1,action);assertEquals(0,more);assertEquals(0,taps)
        }
    }

    @Test fun continuousHoldSlideReleaseChoosesActionAndOutsideReleaseCancels() {
        var action=-1;var swipes=0;var taps=0
        startKoin {modules(module {single {PreferenceManager(context)}})}
        rule.setContent {DotDialerTheme {Surface {
            HomeResultRow("Test Alexandru Butica","0700000101",null,"0700000101",
                quickActionsEnabled=true,onQuickAction={action=it},swipeEnabled=true,
                swipeRightAction=PreferenceManager.SWIPE_ACTION_CALL,swipeLeftAction=PreferenceManager.SWIPE_ACTION_DELETE_ENTRY,
                haptics=false,hapticsStrength="light",hapticsIntensity=.5f,
                onAction={swipes++},onAvatarClick={},onClick={taps++},onLongClick={},
                isLastInSection=true,contactNameSize=18,avatarSize=46)
        }}}
        val start=rule.onNodeWithText("Test Alexandru Butica").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {down(start)}
        rule.mainClock.advanceTimeBy(700);rule.waitForIdle()
        val target=rule.onNodeWithText(context.getString(R.string.copy_action)).fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {moveTo(target)}
        rule.mainClock.advanceTimeBy(150);rule.waitForIdle()
        capture("quick-gesture-hover")
        val tile=rule.onNodeWithText(context.getString(R.string.copy_action)).fetchSemanticsNode().boundsInRoot
        val image=android.graphics.BitmapFactory.decodeFile("../evidence/local-verification/quick-tray-tap-20261018/quick-gesture-hover.png")
        val pixel=image.getPixel(tile.left.toInt()+1,tile.top.toInt()+1)
        assertEquals(75.0,android.graphics.Color.red(pixel).toDouble(),1.0)
        assertEquals(86.0,android.graphics.Color.green(pixel).toDouble(),1.0)
        assertEquals(81.0,android.graphics.Color.blue(pixel).toDouble(),1.0)
        image.recycle()
        rule.onRoot().performTouchInput {up()}
        rule.onNodeWithText(context.getString(R.string.copy_action)).assertDoesNotExist()
        assertEquals(3,action);assertEquals(0,swipes);assertEquals(0,taps)
        action=-1
        rule.onRoot().performTouchInput {down(start)}
        rule.mainClock.advanceTimeBy(700);rule.waitForIdle()
        rule.onRoot().performTouchInput {moveTo(Offset(1f,500f));up()}
        assertEquals(-1,action);assertEquals(0,swipes);assertEquals(0,taps)
        rule.onNodeWithText(context.getString(R.string.more)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.copy_action)).assertDoesNotExist()
        rule.onRoot().performTouchInput {down(start)}
        rule.mainClock.advanceTimeBy(700);rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.copy_action)).assertIsDisplayed()
        rule.onRoot().performTouchInput {cancel()}
        rule.onNodeWithText(context.getString(R.string.copy_action)).assertDoesNotExist()
        assertEquals(-1,action)
    }

    @Test fun tapsToggleTrayAndTileTapsExecuteWithoutLongPress() {
        var calls=0;var action=-1;var allowed=true;var upgrades=0
        var tapMode by mutableStateOf(true)
        var holdMode by mutableStateOf(false)
        startKoin {modules(module {single {PreferenceManager(context)}})}
        rule.setContent {DotDialerTheme {Surface {
            HomeResultRow("Test Alexandru Butica","0700000101",null,"0700000101",
                quickActionsEnabled=holdMode,quickActionsOnTap=tapMode,onQuickActionsOpen={if(!allowed)upgrades++;allowed},
                onQuickAction={action=it},swipeEnabled=false,swipeRightAction=0,swipeLeftAction=0,
                haptics=false,hapticsStrength="light",hapticsIntensity=.5f,onAction={},onAvatarClick={},
                onClick={calls++},isLastInSection=true,contactNameSize=18,avatarSize=46)
        }}}
        val row=rule.onNodeWithText("Test Alexandru Butica")
        for (hold in listOf(false,true)) {
            holdMode=hold
        row.performClick();rule.onNodeWithText("Copy").assertIsDisplayed();capture("quick-tap-open")
        row.performClick();rule.onNodeWithText("Copy").assertDoesNotExist();assertEquals(0,calls)
        listOf(context.getString(R.string.call),context.getString(R.string.sms),"WhatsApp","Copy").forEachIndexed {i,label->
            row.performClick();rule.onNodeWithText(label).performTouchInput{click()}
            assertEquals(i,action);rule.onNodeWithText("Copy").assertDoesNotExist()
        }
        }
        allowed=false;row.performClick();assertEquals(1,upgrades)
        rule.onNodeWithText("Copy").assertDoesNotExist();assertEquals(0,calls)
        tapMode=false;row.performClick();assertEquals(1,calls)
    }

    @Test fun extensionKeypadFollowsLiveStateRightsAndMetadataWithoutReplacingDigits() {
        var allowed=true;var upgrades=0
        val prefs=PreferenceManager(context,featureAllowed={allowed},onPlanRequired={upgrades++})
        runBlocking {prefs.restoreAllPreferences(emptyMap(),replacePortable=true)
            PhoneExtensions.replace(prefs,emptyList(),mapOf("+40700000101" to PhoneExtension("204","Sales")))}
        val commands=mutableListOf<String>();val call=QuickCallFixture.call(commands=commands)
        val repository=CallRepositoryImpl().apply {updateAllCalls(listOf(call));updateCurrentCallSession(CallSession(call,Call.STATE_ACTIVE))}
        startKoin {modules(module {single {prefs};single<ICallRepository>{repository}})}
        var state by mutableIntStateOf(Call.STATE_ACTIVE)
        var dark by mutableStateOf(false);var scale by mutableFloatStateOf(1f)
        var typed by mutableStateOf("")
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            DotDialerTheme(systemDark=dark){Surface {InCallKeypad(call,typed,{typed+=it},state)}}}}
        rule.onNodeWithTag("inCallKey-1").assertIsDisplayed()
        rule.onNodeWithText("Send 204 · Sales").assertIsEnabled()
        assertFalse(commands.contains("playDtmfTone"));capture("extension-keypad-light")
        QuickCallFixture.state(call,Call.STATE_HOLDING);state=Call.STATE_HOLDING
        rule.onNodeWithText("Send 204 · Sales").assertIsNotEnabled()
        QuickCallFixture.state(call,Call.STATE_ACTIVE);state=Call.STATE_ACTIVE
        allowed=false;prefs.notifyPlanChanged()
        rule.onNodeWithText("Send 204 · Sales").performClick();assertEquals(1,upgrades)
        assertFalse(commands.contains("playDtmfTone"))
        allowed=true;prefs.notifyPlanChanged();dark=true;scale=1.5f
        rule.onNodeWithText("Send 204 · Sales").assertIsDisplayed();capture("extension-keypad-dark-font150")
        runBlocking {PhoneExtensions.replace(prefs,listOf("+40700000101"),emptyMap())}
        rule.onNodeWithText("Send 204 · Sales").assertDoesNotExist()
        rule.onNodeWithTag("inCallKey-1").performClick();assertEquals("1",typed)
    }

    @Test fun contactExtensionFieldsFilterDigitsAndHaveReadablePaidAndFreeStates() {
        var allowed=true
        val prefs=PreferenceManager(context,featureAllowed={allowed})
        var extension by mutableStateOf(PhoneExtension())
        rule.setContent {DotDialerTheme {Surface {ContactExtensionFields(extension,prefs){extension=it}}}}
        rule.onNodeWithText(context.getString(R.string.phone_extension)).performTextInput("2a04#")
        assertEquals("204#",extension.digits)
        rule.onNodeWithText(context.getString(R.string.extension_label)).performTextInput("Sales")
        capture("extension-editor-light")
        allowed=false;prefs.notifyPlanChanged()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        assertEquals("204#",extension.digits)
    }

    @Test fun trayIsFourContiguousSquaresInLightDarkAndLargeText() {
        val state=HomeQuickGestureState().apply{expanded=true}
        var selected=-1
        var dark by mutableStateOf(false)
        var scale by mutableStateOf(1f)
        rule.setContent {DotDialerTheme(systemDark=dark) {
            CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
                Surface {HomeQuickActionTray(state){selected=it}}
            }
        }}
        val labels=listOf(context.getString(R.string.call),context.getString(R.string.sms),"WhatsApp",context.getString(R.string.copy_action))
        for (mode in 0..2) {
            dark=mode==1;scale=if(mode==2)1.5f else 1f
            state.expanded=true
            rule.waitForIdle()
            rule.onAllNodes(hasClickAction()).assertCountEquals(4)
            val bounds=rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().map{it.boundsInRoot}.sortedBy{it.left}
            bounds.forEach {assertEquals(it.width,it.height,.5f)}
            bounds.zipWithNext().forEach{(a,b)->assertEquals(a.right,b.left,.5f);assertEquals(a.width,b.width,.5f)}
            rule.onNodeWithText(context.getString(R.string.more)).assertDoesNotExist()
            capture("quick-squares-$mode")
            labels.forEachIndexed {index,label->
                state.expanded=true
                rule.onNodeWithText(label).performTouchInput{click()}
                assertEquals(index,selected)
                rule.runOnIdle{assertFalse(state.expanded)}
            }
        }
    }

    private fun capture(name:String) {
        rule.waitForIdle()
        val decor=rule.activity.window.decorView
        val bitmap=Bitmap.createBitmap(decor.width,decor.height,Bitmap.Config.ARGB_8888)
        rule.runOnIdle {decor.draw(Canvas(bitmap))}
        val directory=File("../evidence/local-verification/quick-tray-tap-20261018").apply{mkdirs()}
        File(directory,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
}
