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

    @Test fun holdWithoutDragKeepsTappableTrayAndMoreRetainsNormalMenu() {
        var action=-1;var more=0;var taps=0
        startKoin {modules(module {single {PreferenceManager(context)}})}
        rule.setContent {DotDialerTheme {Surface {
            HomeResultRow("Test Alexandru Butica","0700000101",null,"0700000101",
                quickActionsEnabled=true,onQuickAction={action=it},swipeEnabled=false,
                swipeRightAction=0,swipeLeftAction=0,haptics=false,hapticsStrength="light",hapticsIntensity=.5f,
                onAction={},onAvatarClick={},onClick={taps++},onLongClick={more++},isLastInSection=true,contactNameSize=18,avatarSize=46)
        }}}
        rule.onNodeWithText("Test Alexandru Butica").performTouchInput {longClick()}
        rule.onNodeWithText(context.getString(R.string.copy_action)).assertIsDisplayed().performClick()
        assertEquals(3,action);assertEquals("Long press never executes the default tap action",0,taps)
        rule.onNodeWithText("Test Alexandru Butica").performTouchInput {longClick()}
        capture("quick-gesture-light")
        rule.onNodeWithText(context.getString(R.string.more)).performClick();assertEquals(1,more);assertEquals(0,taps)
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
        rule.onRoot().performTouchInput {moveTo(target);up()}
        assertEquals(3,action);assertEquals(0,swipes);assertEquals(0,taps)
        action=-1
        rule.onRoot().performTouchInput {down(start)}
        rule.mainClock.advanceTimeBy(700);rule.waitForIdle()
        rule.onRoot().performTouchInput {moveTo(Offset(1f,500f));up()}
        assertEquals(-1,action);assertEquals(0,swipes);assertEquals(0,taps)
        rule.onNodeWithText(context.getString(R.string.more)).assertDoesNotExist()
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

    private fun capture(name:String) {
        rule.waitForIdle()
        val decor=rule.activity.window.decorView
        val bitmap=Bitmap.createBitmap(decor.width,decor.height,Bitmap.Config.ARGB_8888)
        rule.runOnIdle {decor.draw(Canvas(bitmap))}
        val directory=File("../evidence/local-verification/quick-actions-v272-20261018").apply{mkdirs()}
        File(directory,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
}
