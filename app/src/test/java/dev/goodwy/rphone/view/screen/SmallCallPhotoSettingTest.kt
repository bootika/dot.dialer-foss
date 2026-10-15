package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.SmallCallPhotoPreference
import dev.goodwy.rphone.view.screen.settings.CallPhotoAppearanceSettings
import dev.goodwy.rphone.view.screen.settings.components.SettingsEditor
import dev.goodwy.rphone.view.screen.settings.components.settingsOptions
import dev.goodwy.rphone.view.screen.settings.settingsFocusIndex
import dev.goodwy.rphone.view.theme.DotDialerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class SmallCallPhotoSettingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Volatile private var full: Boolean? = null
    @Test fun newlyConfirmedWhatsAppSourceUpdatesTheVisibleCallWithoutChangingPhotoUri() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        runBlocking { prefs.restoreAllPreferences(mapOf(
            PreferenceManager.KEY_CALL_SCREEN_APPEARANCE to PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO,
            SmallCallPhotoPreference.KEY to false), replacePortable = true) }
        File(context.noBackupFilesDir, "call-photo-origins.json").delete()
        val provider = dev.goodwy.rphone.controller.util.CallPhotoOriginsTest.OriginProvider()
        val controller = org.robolectric.android.controller.ContentProviderController.of(provider)
            .create(android.provider.ContactsContract.AUTHORITY)
        val bitmap = createBitmap(512,512).apply { eraseColor(Color.BLUE) }
        val bytes = java.io.ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray() }
        val uri = Uri.parse("content://com.android.contacts/display_photo/77")
        listOf(context.contentResolver,rule.activity.contentResolver).forEach { resolver ->
            org.robolectric.Shadows.shadowOf(resolver).registerInputStreamSupplier(uri) { java.io.ByteArrayInputStream(bytes) }
        }
        rule.setContent {
            val version by prefs.settingsChanged.collectAsStateWithLifecycle()
            val result = rememberUseFullCallPhotoAppearance(PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO,
                prefs,version,uri.toString(),0)
            SideEffect { full = result }
        }
        rule.waitUntil(5_000) { full == true }
        val before = dev.goodwy.rphone.controller.util.CallPhotoOrigins.revision.value
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            dev.goodwy.rphone.controller.util.CallPhotoOrigins(context).rememberApplied(7,bytes,"WhatsApp")
        }
        assertTrue(dev.goodwy.rphone.controller.util.CallPhotoOrigins.revision.value > before)
        rule.waitForIdle()
        rule.waitUntil(5_000) { full == false }
        assertFalse(prefs.getBoolean(SmallCallPhotoPreference.KEY,true))
        controller.shutdown()
        File(context.noBackupFilesDir,"call-photo-origins.json").delete()
        runBlocking { prefs.restoreAllPreferences(emptyMap(),replacePortable=true) }
    }

    @Test fun switchPersistsAndOnlyOverridesSmallPhotosWhileRevisionUpdatesSameUri() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        val fullStyle = PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO
        runBlocking { prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE to fullStyle), replacePortable = true) }
        assertTrue(prefs.getBoolean(SmallCallPhotoPreference.KEY, SmallCallPhotoPreference.DEFAULT))
        val file = File(context.cacheDir, "revised-call-photo.png")
        fun replacePhoto(size: Int) {
            val bitmap = createBitmap(size, size).apply { eraseColor(Color.BLUE) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        replacePhoto(256)
        org.robolectric.Shadows.shadowOf(rule.activity.contentResolver).registerInputStreamSupplier(Uri.parse("content://avatar.fixture/revision")) { java.io.ByteArrayInputStream(file.readBytes()) }
        var revision by mutableIntStateOf(0)
        var uri by mutableStateOf<String?>("content://avatar.fixture/revision")
        rule.setContent {
            val version by prefs.settingsChanged.collectAsStateWithLifecycle()
            val appearance by rememberCallScreenAppearance(prefs, version)
            val result = rememberUseFullCallPhotoAppearance(appearance, prefs, version, uri, revision)
            SideEffect { full = result }
            DotDialerTheme {
                CallPhotoAppearanceSettings(prefs, appearance, listOf("Full photo" to fullStyle)) {
                    prefs.setString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, it)
                }
            }
        }
        rule.waitUntil(5_000) { full == false }
        val title = context.getString(dev.goodwy.rphone.R.string.small_call_photos_in_circle)
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = createBitmap(view.width,view.height)
            view.draw(android.graphics.Canvas(bitmap))
            val output = File(System.getProperty("user.dir"),"build/reports/visual/small-call-photo-settings.png")
            requireNotNull(output.parentFile).mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        rule.onNode(isToggleable()).assertIsOn()
        rule.onNodeWithText(title).performClick()
        rule.waitUntil(5_000) { full == true && !prefs.getBoolean(SmallCallPhotoPreference.KEY, true) }
        assertEquals(fullStyle, prefs.getString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, null))
        assertEquals(false, runBlocking { prefs.getPersistedPreferences() }[SmallCallPhotoPreference.KEY])
        rule.onNode(isToggleable()).assertIsOff()
        rule.onNodeWithText(title).performClick()
        rule.waitUntil(5_000) { full == false }
        replacePhoto(512)
        org.robolectric.Shadows.shadowOf(rule.activity.contentResolver).registerInputStreamSupplier(Uri.parse("content://avatar.fixture/revision")) { java.io.ByteArrayInputStream(file.readBytes()) }
        assertEquals(dev.goodwy.rphone.controller.util.CallPhotoDimensions(512,512), runBlocking {
            dev.goodwy.rphone.controller.util.CallPhotoSizeResolver(rule.activity).resolve(uri) })
        rule.runOnIdle { revision++ }
        rule.waitForIdle()
        rule.waitUntil(5_000) { full == true }
        replacePhoto(192)
        org.robolectric.Shadows.shadowOf(rule.activity.contentResolver).registerInputStreamSupplier(Uri.parse("content://avatar.fixture/revision")) { java.io.ByteArrayInputStream(file.readBytes()) }
        assertEquals(dev.goodwy.rphone.controller.util.CallPhotoDimensions(192,192), runBlocking {
            dev.goodwy.rphone.controller.util.CallPhotoSizeResolver(rule.activity).resolve(uri) })
        rule.runOnIdle { revision++ }
        rule.waitForIdle()
        rule.waitUntil(5_000) { full == false }
        rule.onNodeWithText(title).performClick()
        rule.waitUntil(5_000) { !prefs.getBoolean(SmallCallPhotoPreference.KEY, true) }
        rule.runOnIdle { uri = "file:///missing-photo.png" }
        rule.waitUntil(5_000) { full == true }
        assertEquals(1, settingsFocusIndex("C09", SettingsEditor.CALL_UI))
        assertTrue(settingsOptions.any { it.id == "C09" && !it.advanced && it.editor == SettingsEditor.CALL_UI })
        runBlocking { prefs.restoreAllPreferences(emptyMap(), replacePortable = true) }
    }
}
