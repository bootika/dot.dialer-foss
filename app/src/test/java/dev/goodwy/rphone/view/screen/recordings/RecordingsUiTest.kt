package dev.goodwy.rphone.view.screen.recordings

import kotlinx.coroutines.flow.MutableStateFlow

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.controller.license.*
import dev.goodwy.rphone.recordings.*
import dev.goodwy.rphone.view.screen.settings.LocalPlanAccessManager
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.core.license.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class RecordingsUiTest {
    @Test fun reorganizedSettingsHaveOneHelpAndLocalizedGroupsWithoutChangingPreferences() {
        val context=rule.activity.application as Application
        val manager=verifiedPlanFixture(context.packageName,AppPlan.PREMIUM,worker)
        val fixture=RecordingSyncEngineTest.FixtureBridge()
        val repo=RecordingsRepository(context,{manager},fixture,{true},MutableStateFlow(true))
        var language by mutableStateOf("en");var dark by mutableStateOf(false)
        var libraryOpens=0
        fun localized()=rule.activity.createConfigurationContext(android.content.res.Configuration(rule.activity.resources.configuration)
            .apply {setLocales(android.os.LocaleList.forLanguageTags(language))})
        rule.setContent {
            val localized=localized()
            CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides rule.activity,
                androidx.compose.ui.platform.LocalContext provides localized,
                androidx.compose.ui.platform.LocalConfiguration provides localized.resources.configuration,
                LocalPlanAccessManager provides manager,LocalRecordingsRepository provides repo,
                LocalDensity provides Density(1f,1.5f)) {
                DotDialerTheme(systemDark=dark){Surface(Modifier.fillMaxSize()){RecordingsContent(settingsOnly=true,onOpenLibrary={libraryOpens++})}}
            }
        }
        for(locale in listOf("en","ro","de","fr","es","it","pt-BR","pl","tr","id","vi"))for(theme in listOf(false,true)) {
            rule.runOnIdle {language=locale;dark=theme}
            rule.onNodeWithTag("recordings-library").performScrollToIndex(0)
            val c=localized()
            rule.waitUntil(10000){rule.onAllNodesWithText(c.getString(dev.goodwy.rphone.R.string.recordings_saved)).fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText(c.getString(dev.goodwy.rphone.R.string.recordings_saved)).performScrollTo().performClick()
            rule.onNodeWithText(c.getString(dev.goodwy.rphone.R.string.rec_ui_processing)).performScrollTo().assertIsDisplayed()
            if(locale in listOf("en","ro","de"))captureNormalAccess("v298-$locale-dark$theme-top")
            rule.onNodeWithText(c.getString(dev.goodwy.rphone.R.string.rec_ui_more)).performScrollTo().assertIsDisplayed()
            rule.onAllNodesWithText(c.getString(dev.goodwy.rphone.R.string.guide_more)).assertCountEquals(1)
            rule.onNodeWithText(c.getString(dev.goodwy.rphone.R.string.guide_more)).performScrollTo().assertIsDisplayed()
            if(locale in listOf("en","ro","de"))captureNormalAccess("v298-$locale-dark$theme-bottom")
        }
        assertEquals(22,libraryOpens)
        assertEquals("",repo.outputLanguage());assertEquals("",repo.translationLanguage());assertFalse(repo.optedIn())
        assertTrue(fixture.calls.isEmpty());assertTrue(repo.catalog.jobs().isEmpty())
    }
    @Test fun groupedLibraryKeepsAudioOriginalAndTranslationTogether() {
        val context=rule.activity.application as Application
        context.deleteDatabase("recordings.db")
        val manager=verifiedPlanFixture(context.packageName,AppPlan.PREMIUM,worker)
        val repo=RecordingsRepository(context,{manager},integrationEnabled={false},moduleState=MutableStateFlow(true))
        fun asset(number: String,time: Long): SavedRecording {
            val id=UUID.randomUUID().toString()
            return SavedRecording(id,"Test Alexandru Butica",time,92000,44,"$id.wav","test-$time","audio/wav","audio/raw",number,"test-person","Test Alexandru Butica").also(repo.catalog::add)
        }
        val newest=asset("+40720000001",20000);asset("+40720000002",10000)
        val job=repo.catalog.enqueue(newest.id,"transcribe");repo.catalog.complete(checkNotNull(repo.catalog.job(job)),"Discutie de test.","ro")
        val original=repo.catalog.texts(newest.id).single()
        val translation=repo.catalog.enqueue(newest.id,"translate","en",original.id)
        repo.catalog.complete(checkNotNull(repo.catalog.job(translation)),"Test conversation.","en")
        var dark by mutableStateOf(false);var scale by mutableFloatStateOf(1f)
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,scale),LocalPlanAccessManager provides manager,LocalRecordingsRepository provides repo) {
            DotDialerTheme(systemDark=dark){Surface(Modifier.fillMaxSize()){RecordingsContent()}}
        }}
        rule.waitUntil(10000){rule.onAllNodesWithTag("recording-${newest.id}").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("+40720000001").assertIsDisplayed()
        rule.onNodeWithText("Audio · Original transcript · 1 translation").assertIsDisplayed()
        captureNormalAccess("grouped-library-light")
        rule.runOnIdle {dark=true;scale=1.5f}
        rule.onNodeWithTag("recording-${newest.id}").assertExists()
        captureNormalAccess("grouped-library-dark-large")
        rule.onNodeWithTag("recording-${newest.id}").performScrollTo().performClick()
        rule.onNodeWithText("Export audio").assertExists()
        rule.waitUntil(10000){rule.onAllNodesWithText("Discutie de test.").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Discutie de test.").assertExists()
        rule.onNodeWithText("Test conversation.").assertExists()
        val clipboard=context.getSystemService(android.content.ClipboardManager::class.java)
        for(text in repo.catalog.texts(newest.id)) {
            rule.onNodeWithTag("copy-transcript-${text.id}").performScrollTo().performClick()
            rule.runOnIdle {assertEquals(text.text,clipboard.primaryClip?.getItemAt(0)?.text?.toString())}
        }
        rule.onNodeWithText("Completed",substring=true).assertDoesNotExist()
        rule.onNodeWithTag("recording-history").performScrollTo().performClick()
        rule.onAllNodesWithText("Completed",substring=true).assertCountEquals(2)
        rule.onNodeWithTag("recording-history").performClick()
        for(theme in listOf(false,true))for(font in listOf(1f,1.5f)) {
            rule.runOnIdle {dark=theme;scale=font}
            rule.onNodeWithText("Export audio").performScrollTo()
            captureNormalAccess("v293-details-dark$theme-font$font")
            rule.onNodeWithTag("copy-transcript-${original.id}").performScrollTo()
            captureNormalAccess("v293-transcript-dark$theme-font$font")
        }
        assertEquals(2,repo.catalog.recordings().size)
        assertEquals(2,repo.catalog.texts(newest.id).size)
    }
    @Test fun masterSwitchOffSilencesControlsAndKeepsSavedLibrary() {
        val context=rule.activity.application as Application
        val manager=verifiedPlanFixture(context.packageName,AppPlan.PREMIUM,worker)
        val backend=IntegratedCallCapture.get(context)
        backend.enable()
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.os.UserManager::class.java)).setUserUnlocked(true)
        val repo=RecordingsRepository(context,{manager},moduleState=backend.optedIn)
        var dark by mutableStateOf(false);var scale by mutableFloatStateOf(1f)
        rule.setContent {CompositionLocalProvider(LocalPlanAccessManager provides manager,LocalRecordingsRepository provides repo,LocalDensity provides Density(1f,scale)) {
            DotDialerTheme(systemDark=dark){Surface(Modifier.fillMaxSize()){RecordingsModuleSwitch()}}
        }}
        rule.onNodeWithTag("recordings-master-switch").assertIsDisplayed()
        rule.onNode(isToggleable()).assertIsOn().performClick()
        rule.waitUntil(10000){!backend.enabled()}
        rule.onNode(isToggleable()).assertIsOff()
        rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.recordings_master_off_summary)).assertIsDisplayed()
        rule.onNodeWithText("Set up").assertDoesNotExist()
        captureNormalAccess("recording-switch-off-light")
        rule.runOnIdle {dark=true;scale=1.5f}
        rule.onNode(isToggleable()).assertIsOff().assertIsDisplayed()
        captureNormalAccess("recording-switch-off-dark-large")
    }
    @Test fun moduleOffKeepsAudioAndTranscriptButDoesNotRefreshOrOfferProcessing() {
        val context=rule.activity.application as Application
        context.deleteDatabase("recordings.db")
        val manager=verifiedPlanFixture(context.packageName,AppPlan.PREMIUM,worker)
        val fixture=RecordingSyncEngineTest.FixtureBridge();val master=MutableStateFlow(false)
        val repo=RecordingsRepository(context,{manager},fixture,{true},master)
        val id=UUID.randomUUID().toString();val asset=SavedRecording(id,"Test Alexandru Butica",1000,92000,44,"$id.wav","test-call","audio/wav")
        repo.catalog.add(asset);val job=repo.catalog.enqueue(id,"transcribe");repo.catalog.complete(checkNotNull(repo.catalog.job(job)),"Discutie de test.","ro")
        rule.setContent {CompositionLocalProvider(LocalPlanAccessManager provides manager,LocalRecordingsRepository provides repo){DotDialerTheme {Surface(Modifier.fillMaxSize()){RecordingsContent()}}}}
        rule.waitUntil(10000){rule.onAllNodesWithTag("recording-$id").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Connect dot.dictate").assertDoesNotExist()
        rule.onNodeWithTag("recording-$id").performClick()
        rule.waitUntil(10000){rule.onAllNodesWithText("Discutie de test.").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Export audio").assertExists();rule.onNodeWithText("Discutie de test.").assertExists()
        rule.onNodeWithText("Transcribe").assertDoesNotExist();rule.onNodeWithText("Translate").assertDoesNotExist()
        assertEquals(0,fixture.refreshCalls);assertTrue(fixture.calls.isEmpty())
    }
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @After fun cleanup() {worker.cancel()}
    @Test fun unreleasedIntegrationHidesEntryAndProcessingButRetainsSavedAssets() {
        val context=rule.activity.application as Application
        val manager=verifiedPlanFixture(context.packageName,AppPlan.PREMIUM,worker)
        val repo=RecordingsRepository(context,{manager},integrationEnabled={false},moduleState=MutableStateFlow(true))
        val id=UUID.randomUUID().toString()
        var library by mutableStateOf(false)
        rule.setContent {CompositionLocalProvider(LocalPlanAccessManager provides manager,LocalRecordingsRepository provides repo) {
            DotDialerTheme {Surface(Modifier.fillMaxSize()) {if(library)RecordingsContent()else RecordingsSettingsEntry {library=true}}}
        }}
        rule.waitForIdle()
        rule.onNodeWithText("Recordings & transcripts").assertDoesNotExist()
        rule.runOnIdle {repo.catalog.add(SavedRecording(id,"Test Alexandru Butica",1000,1000,32,"$id.m4a","call-check","audio/mp4"));library=true}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("recording-$id").fetchSemanticsNodes().size==1}
        rule.onNodeWithTag("recordings-import").assertDoesNotExist()
        rule.onNodeWithText("Connect dot.dictate").assertDoesNotExist()
        rule.onNodeWithText("Automatic transcription").assertDoesNotExist()
        rule.onNodeWithTag("recording-$id").performClick()
        rule.onNodeWithText("Transcribe").assertDoesNotExist()
        rule.onNodeWithText("Export audio").assertExists()
        rule.onNodeWithText("Delete recording").assertExists()
        assertEquals(io.github.bootika.dotdialer.core.recordings.RecordingAccess.UNSUPPORTED,repo.allowed())
        assertFalse(repo.bridge.trusted())
    }
    @Test fun testBuildShowsNormalSettingsAndCallMoreWithoutProvider() {
        assertTrue(dev.goodwy.rphone.BuildConfig.DICTATE_INTEGRATION_ENABLED)
        val context=rule.activity.application as Application
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.os.UserManager::class.java)).setUserUnlocked(true)
        for(id in listOf("K13","K14","K15")) assertTrue(
            dev.goodwy.rphone.view.screen.settings.components.settingsOptions.single {it.id==id}
                .isAvailable(dev.goodwy.rphone.BuildConfig.FLAVOR,false))
        val manager=verifiedPlanFixture(context.packageName,AppPlan.PREMIUM,worker)
        val repo=RecordingsRepository(context,{manager},moduleState=MutableStateFlow(true))
        val call=org.robolectric.shadow.api.Shadow.newInstanceOf(android.telecom.Call::class.java)
        val details=org.robolectric.shadow.api.Shadow.newInstanceOf(android.telecom.Call.Details::class.java)
        org.robolectric.util.ReflectionHelpers.setField(call,"mDetails",details)
        var page by mutableIntStateOf(0)
        rule.setContent {CompositionLocalProvider(LocalPlanAccessManager provides manager,LocalRecordingsRepository provides repo) {
            DotDialerTheme {Surface(Modifier.fillMaxSize()) {when(page) {
                0->RecordingsSettingsEntry {page=1}
                1->RecordingsContent(settingsOnly=true)
                else->CallRecordingsEntry(call,"Test Alexandru Butica")
            }}}
        }}
        rule.onNodeWithTag("recordings-master-switch").assertDoesNotExist()
        rule.onNodeWithText("Recordings & transcripts").assertIsDisplayed().performClick()
        rule.waitUntil(10000){rule.onAllNodesWithText("Connect dot.dictate").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Connect dot.dictate").assertExists()
        rule.onNodeWithTag("recordings-import").assertExists()
        rule.onNodeWithText("Automatic transcription").assertExists()
        captureNormalAccess("settings-unpaired")
        rule.runOnIdle {page=2}
        rule.onNodeWithText("Recordings & transcripts").assertIsDisplayed().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("recordings-library").fetchSemanticsNodes().size==1}
        rule.onNodeWithText("Connect dot.dictate").assertDoesNotExist()
        captureNormalAccess("call-more-unpaired")
        assertFalse(repo.bridge.trusted())
        assertFalse(repo.bridge.capabilities.value.connected)
        assertTrue(repo.catalog.jobs().isEmpty())
        assertEquals(io.github.bootika.dotdialer.core.recordings.RecordingAccess.DISCONNECTED,repo.allowed())
    }
    @Test fun integrationStatusRefreshesOnResumeAndNeverTreatsConnectionAloneAsReady() {
        val context=rule.activity.application as Application
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.os.UserManager::class.java)).setUserUnlocked(true)
        val manager=verifiedPlanFixture(context.packageName,AppPlan.PREMIUM,worker)
        val fixture=RecordingSyncEngineTest.FixtureBridge()
        val bridge=object : DictateBridge by fixture {
            var calls=0
            var next=fixture.state.value.copy(targetLanguages=listOf("en"))
            var fail=false
            var waitFor: CompletableDeferred<Unit>?=null
            override suspend fun refresh(): io.github.bootika.dotdialer.core.recordings.DictateCapabilities {
                calls++
                waitFor?.await()
                if(fail)throw DictateFailure("DISCONNECTED")
                fixture.state.value=next
                return next
            }
        }
        val repo=RecordingsRepository(context,{manager},bridge,{true},moduleState=MutableStateFlow(true))
        var open by mutableStateOf(false)
        rule.setContent {CompositionLocalProvider(LocalPlanAccessManager provides manager,LocalRecordingsRepository provides repo) {
            DotDialerTheme {Surface(Modifier.fillMaxSize()) {if(open)RecordingsContent(settingsOnly=true)else RecordingsSettingsEntry {open=true}}}
        }}
        rule.waitUntil(10000) {bridge.calls>0}
        captureNormalAccess("settings-menu-icon")
        rule.onNodeWithText("Recordings & transcripts").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText(context.getString(dev.goodwy.rphone.R.string.rec_ui_api_hint)).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.rec_ui_api_hint)).assertExists()
        rule.onNodeWithText("Requires a dot.dictate license and your API key.").assertDoesNotExist()
        captureNormalAccess("ready")
        fun resumeWith(expected: String, change: ()->Unit) {
            rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            val count=bridge.calls
            change()
            bridge.waitFor=CompletableDeferred()
            rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            rule.waitUntil(10000) {bridge.calls>count && rule.onAllNodesWithTag("recordings-checking").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.rec_ui_api_hint)).assertDoesNotExist()
            bridge.waitFor?.complete(Unit);bridge.waitFor=null
            rule.waitUntil(10000) {bridge.calls>count && rule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.rec_ui_api_hint)).assertDoesNotExist()
        }
        resumeWith("Add your API key in dot.dictate.") {bridge.next=bridge.next.copy(apiKeyConfigured=false)}
        resumeWith("Activate your dot.dictate license.") {bridge.next=bridge.next.copy(licenseState="required")}
        resumeWith("The dot.dictate license could not be verified.") {bridge.next=bridge.next.copy(licenseState="unavailable")}
        captureNormalAccess("license-unavailable")
        resumeWith("Could not check dot.dictate. We will retry automatically.") {bridge.fail=true}
        captureNormalAccess("connection-failed")
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        bridge.fail=false;bridge.next=bridge.next.copy(licenseState="valid",apiKeyConfigured=true,transcription=false)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.waitUntil(10000) {rule.onAllNodesWithText("Update dot.dictate to use this integration.").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.rec_ui_api_hint)).assertDoesNotExist()
        assertTrue(repo.catalog.jobs().isEmpty())
        assertTrue(fixture.calls.isEmpty())
    }
    private fun captureNormalAccess(name: String) {
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        rule.runOnIdle {
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val view=if(dialog?.isShowing==true)requireNotNull(dialog.window).decorView else rule.activity.window.decorView
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val output=File("../evidence/local-verification/dictate-normal-access-20261019/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            image.recycle()
        }
    }
    @Test fun savedLibraryIsReadableAcrossPlansThemesAndFontSizesWithoutConnectingOrUploading() {
        val context=rule.activity
        val managers=AppPlan.entries.associateWith {verifiedPlanFixture(context.packageName,it,worker)}
        val repositories=managers.mapValues {RecordingsRepository(context.applicationContext as Application,{it.value},integrationEnabled={true},moduleState=MutableStateFlow(true))}
        val id=UUID.randomUUID().toString()
        val repo=requireNotNull(repositories[AppPlan.FREE]);repo.catalog.add(SavedRecording(id,"Test Alexandru Butica",1000,60_000,480_000,"$id.m4a","call-1","audio/mp4"))
        val job=repo.catalog.enqueue(id,"transcribe");repo.catalog.complete(requireNotNull(repo.catalog.job(job)),"Discutie de test.","ro")
        var tier by mutableStateOf(AppPlan.FREE);var dark by mutableStateOf(false);var font by mutableFloatStateOf(1f)
        rule.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalPlanAccessManager provides requireNotNull(managers[tier]),
                LocalRecordingsRepository provides requireNotNull(repositories[tier]),
                LocalDensity provides Density(density.density,font)) {
                DotDialerTheme(systemDark=dark) {Surface(Modifier.fillMaxSize()) {RecordingsContent()}}
            }
        }
        rule.waitUntil(10000) {rule.onAllNodesWithTag("recording-$id").fetchSemanticsNodes().size==1}
        for(plan in AppPlan.entries)for(theme in listOf(false,true))for(scale in listOf(1f,2f)) {
            rule.runOnIdle {tier=plan;dark=theme;font=scale}
            rule.mainClock.advanceTimeBy(500);rule.waitForIdle()
            rule.onNodeWithTag("recordings-library").assertExists()
            rule.onNodeWithTag("recording-$id").performScrollTo().assertIsDisplayed()
            val output=File("../evidence/local-verification/dictate-v1-20261019/ui/${plan.name}-dark$theme-font$scale.png")
            rule.runOnIdle {val view=rule.activity.window.decorView;val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(image));output.parentFile?.mkdirs();output.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
        }
        rule.runOnIdle {tier=AppPlan.FREE;font=2f;dark=true}
        rule.onNodeWithTag("recording-$id").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Discutie de test.",useUnmergedTree=true).fetchSemanticsNodes().size==1}
        rule.onNodeWithText("Discutie de test.",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        assertEquals(1,repo.catalog.recordings().size)
        assertEquals("Discutie de test.",repo.catalog.texts(id).single().text)
        assertTrue(repo.catalog.jobs().all {it.state=="completed"})
        assertFalse(repo.bridge.trusted())
        assertFalse(repo.bridge.capabilities.value.connected)
    }
}
