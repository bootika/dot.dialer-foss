package dev.goodwy.rphone.view.screen.recordings

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
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @After fun cleanup() {worker.cancel()}
    @Test fun unreleasedIntegrationHidesEntryAndProcessingButRetainsSavedAssets() {
        val context=rule.activity.application as Application
        val manager=verifiedPlanFixture(context.packageName,AppPlan.PREMIUM,worker)
        val repo=RecordingsRepository(context,{manager},integrationEnabled={false})
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
    @Test fun savedLibraryIsReadableAcrossPlansThemesAndFontSizesWithoutConnectingOrUploading() {
        val context=rule.activity
        val managers=AppPlan.entries.associateWith {verifiedPlanFixture(context.packageName,it,worker)}
        val repositories=managers.mapValues {RecordingsRepository(context.applicationContext as Application,{it.value},integrationEnabled={true})}
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
