package dev.goodwy.rphone.view.screen.settings

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityOptionsCompat
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.VoiceSearchContract
import dev.goodwy.rphone.view.components.VoiceSearchButton
import dev.goodwy.rphone.view.screen.settings.components.SettingsSearchBar
import dev.goodwy.rphone.view.theme.DotDialerTheme
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class VoiceSearchRenderingTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private lateinit var prefs: PreferenceManager
    private val launches=mutableListOf<Pair<Int, Intent>>()
    private var speechUnavailable=false
    private val registry=object: ActivityResultRegistry() {
        override fun <I,O> onLaunch(requestCode:Int, contract:ActivityResultContract<I,O>, input:I, options:ActivityOptionsCompat?) {
            val intent=contract.createIntent(rule.activity,input)
            if (speechUnavailable && intent.action == RecognizerIntent.ACTION_RECOGNIZE_SPEECH) throw android.content.ActivityNotFoundException()
            launches += requestCode to intent
        }
    }
    private val owner=object: ActivityResultRegistryOwner { override val activityResultRegistry=registry }
    @Before fun init() {
        val app = RuntimeEnvironment.getApplication()
        val storage = File(app.cacheDir, "voice-render-" + UUID.randomUUID()).apply { mkdirs() }
        val isolated = object : ContextWrapper(app) {
            override fun getApplicationContext() = this
            override fun createDeviceProtectedStorageContext() = this
            override fun getFilesDir() = File(storage, "files").apply { mkdirs() }
        }
        prefs=PreferenceManager(isolated)
        runBlocking { prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_VOICE_SEARCH_ENABLED to false)) }
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        startKoin { modules(module { single { prefs } }) }
    }
    @After fun cleanup() { stopKoin() }
    private fun grant() = shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
    private fun flush() { rule.waitForIdle();runBlocking { prefs.getPersistedPreferences() };rule.waitForIdle() }
    @Test fun activationRequiresActualGrantAndNeverStartsListening() {
        rule.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            DotDialerTheme { Column { VoiceSearchSetting(prefs);VoiceSearchButton({},prefs=prefs) } }
        } }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.voice_input)).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.voice_search_title)).performClick()
        assertEquals(1,launches.size)
        rule.runOnIdle { registry.dispatchResult(launches.last().first,true) }
        flush()
        assertFalse(prefs.getBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED,false))
        rule.onNodeWithText(rule.activity.getString(R.string.cancel)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.voice_search_title)).performClick()
        rule.runOnIdle { grant(); registry.dispatchResult(launches.last().first,true) }
        flush()
        assertTrue(prefs.getBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED,false))
        assertFalse(launches.any { it.second.action == RecognizerIntent.ACTION_RECOGNIZE_SPEECH })
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.voice_input)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.voice_search_title)).performClick()
        flush()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.voice_input)).assertDoesNotExist()
    }
    @Test fun settingsSearchReceivesTranscriptAndCancelPreservesQueryAcrossThemes() {
        grant();prefs.setBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED,true)
        runBlocking { prefs.getPersistedPreferences() }
        var query by mutableStateOf("before")
        var dark by mutableStateOf(false)
        rule.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner,LocalDensity provides Density(1f,1.5f)) {
            DotDialerTheme(systemDark=dark) { Column { VoiceSearchSetting(prefs);SettingsSearchBar(query,{query=it});Text(query) } }
        } }
        val mic=rule.onNodeWithContentDescription(rule.activity.getString(R.string.voice_input))
        mic.assertIsDisplayed().performClick()
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,launches.last().second.action)
        rule.runOnIdle { registry.dispatchResult(launches.last().first,Activity.RESULT_OK,
            Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,arrayListOf("  Test Alexandru Butica  "))) }
        // Waiting for persisted writes does not await the recognition callback's coroutine.
        rule.waitUntil(10_000) { query == "Test Alexandru Butica" }
        flush();assertEquals("Test Alexandru Butica",query)
        rule.onNode(hasSetTextAction()).assertTextEquals("Test Alexandru Butica")
        mic.performClick()
        rule.runOnIdle { registry.dispatchResult(launches.last().first,Activity.RESULT_CANCELED,null) }
        flush();assertEquals("Test Alexandru Butica",query)
        for(theme in listOf(false,true)) {
            rule.runOnIdle { dark=theme };flush();mic.assertIsDisplayed()
            rule.runOnIdle {
                val view=rule.activity.window.decorView
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
                val file=File("build/reports/visual/voice-search-d98/${if(theme) "dark" else "light"}-150.png")
                requireNotNull(file.parentFile).mkdirs();file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
        }
        rule.runOnIdle { shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.RECORD_AUDIO) }
        mic.performClick()
        assertEquals(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions.ACTION_REQUEST_PERMISSIONS,launches.last().second.action)
    }
    @Test fun missingSpeechProviderIsRecoverableAndLateResultAfterDisableIsIgnored() {
        grant();prefs.setBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED,true)
        runBlocking { prefs.getPersistedPreferences() }
        var query by mutableStateOf("before")
        rule.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            DotDialerTheme { Column { VoiceSearchButton({query=it},prefs=prefs);Text(query) } }
        } }
        speechUnavailable=true
        val mic=rule.onNodeWithContentDescription(rule.activity.getString(R.string.voice_input))
        mic.performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.voice_search_unavailable)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.ok)).performClick()
        assertEquals("before",query)
        speechUnavailable=false;mic.performClick()
        rule.runOnIdle {
            prefs.setBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED,false)
            registry.dispatchResult(launches.last().first,Activity.RESULT_OK,
                Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,arrayListOf("Test Alexandru Butica")))
        }
        flush();assertEquals("before",query)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.voice_input))
                .fetchSemanticsNodes().isEmpty()
        }
        mic.assertDoesNotExist()
    }
    @Test fun contractUsesLocalizedPromptAndIgnoresEmptyOrCancelledResults() {
        val contract=VoiceSearchContract()
        assertEquals(rule.activity.getString(R.string.voice_search_prompt),contract.createIntent(rule.activity,Unit).getStringExtra(RecognizerIntent.EXTRA_PROMPT))
        assertNull(contract.parseResult(Activity.RESULT_CANCELED,Intent()))
        assertNull(contract.parseResult(Activity.RESULT_OK,Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,arrayListOf("  "))))
    }
    @Test fun homeAndContactDirectoryUseTheSameOptInAndUpdateTheirOwnSearchFields() {
        grant();prefs.setBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED,true)
        runBlocking { prefs.getPersistedPreferences() }
        var surface by mutableIntStateOf(0)
        var query by mutableStateOf("")
        rule.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner,LocalDensity provides Density(1f,1.5f)) {
            DotDialerTheme { when(surface) {
                0 -> dev.goodwy.rphone.view.screen.UnifiedHomeTopBar(query,{query=it},false,{},false,{},{},{})
                else -> dev.goodwy.rphone.view.components.ContactDirectoryHeader(query,{query=it})
            } }
        } }
        for(index in 0..1) {
            rule.runOnIdle { surface=index;query="" };flush()
            val mic=rule.onNodeWithContentDescription(rule.activity.getString(R.string.voice_input))
            assertTrue(mic.fetchSemanticsNode().boundsInRoot.width >= 48f)
            mic.assertIsDisplayed().performClick()
            rule.runOnIdle { registry.dispatchResult(launches.last().first,Activity.RESULT_OK,
                Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,arrayListOf("Test Alexandru Butica"))) }
            rule.waitUntil(10_000) { query == "Test Alexandru Butica" }
            flush()
            rule.onNode(hasSetTextAction()).assertTextEquals("Test Alexandru Butica")
        }
        rule.runOnIdle { prefs.setBoolean(PreferenceManager.KEY_VOICE_SEARCH_ENABLED,false) }
        flush()
        // Persisted writes do not await the lifecycle collector and its recomposition.
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.voice_input))
                .fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.voice_input)).assertDoesNotExist()
        rule.onNode(hasSetTextAction()).assertTextEquals("Test Alexandru Butica")
    }
}
