package dev.goodwy.rphone.view.screen.recordings

import android.app.Application
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.controller.license.verifiedPlanFixture
import dev.goodwy.rphone.recordings.*
import dev.goodwy.rphone.view.screen.settings.LocalPlanAccessManager
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.core.license.AppPlan
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class,qualifiers="w400dp-h800dp-mdpi")
class CallCaptureActionsTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @After fun cleanup(){scope.cancel()}
    @Test fun openMoreTracksCallMutationsAndOffRemovesAllRecordingEntries() {
        val context=rule.activity.application as Application
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.os.UserManager::class.java)).setUserUnlocked(true)
        val backend=IntegratedCallCapture.get(context);backend.enable()
        val manager=verifiedPlanFixture(context.packageName,AppPlan.PREMIUM,scope)
        val repo=RecordingsRepository(context,{manager},RecordingSyncEngineTest.FixtureBridge(),{true},backend.optedIn)
        val call=Shadow.newInstanceOf(Call::class.java)
        ReflectionHelpers.setField(call,"mCallbackRecords",CopyOnWriteArrayList<Any>())
        val details=Shadow.newInstanceOf(Call.Details::class.java)
        ReflectionHelpers.setField(details,"mState",Call.STATE_DIALING)
        ReflectionHelpers.setField(call,"mDetails",details)
        ReflectionHelpers.setField(call,"mState",Call.STATE_DIALING)
        ReflectionHelpers.setField(call,"mChildrenIds",arrayListOf<String>())
        ReflectionHelpers.setField(call,"mChildren",arrayListOf<Call>())
        ReflectionHelpers.setField(call,"mUnmodifiableChildren",emptyList<Call>())
        ReflectionHelpers.setField(call,"mChildrenCached",true)
        ReflectionHelpers.setField(call,"mConferenceableCalls",arrayListOf<Call>())
        ReflectionHelpers.setField(call,"mUnmodifiableConferenceableCalls",emptyList<Call>())
        rule.setContent {CompositionLocalProvider(LocalPlanAccessManager provides manager,LocalRecordingsRepository provides repo){
            DotDialerTheme {Column {CallCaptureActions(call,"Test Alexandru Butica"){};CallRecordingsEntry(call,"Test Alexandru Butica")}}
        }}
        rule.onNode(hasText("Record") and hasClickAction()).assertExists()
        fun state(value: Int) {rule.runOnIdle {
            ReflectionHelpers.setField(details,"mState",value);ReflectionHelpers.setField(call,"mState",value)
            ReflectionHelpers.getField<CopyOnWriteArrayList<Any>>(call,"mCallbackRecords").map {ReflectionHelpers.getField<Call.Callback>(it,"mCallback")}.forEach {it.onStateChanged(call,value)}
        }}
        state(Call.STATE_HOLDING);rule.onNode(hasText("Record") and hasClickAction()).assertDoesNotExist()
        state(Call.STATE_ACTIVE);rule.onNode(hasText("Record") and hasClickAction()).assertExists()
        rule.onNode(hasText("Record & transcribe") and hasClickAction()).assertExists()
        rule.onNodeWithText("Recordings & transcripts").assertExists()
        rule.runOnIdle {runBlocking {backend.disable()}}
        rule.onNodeWithText("Record").assertDoesNotExist()
        rule.onNodeWithText("Record & transcribe").assertDoesNotExist()
        rule.onNodeWithText("Recordings & transcripts").assertDoesNotExist()
        assertTrue(repo.catalog.jobs().isEmpty())
    }
}
