package dev.goodwy.rphone.controller

import dev.goodwy.rphone.controller.util.currentTelecomState
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.domain.model.CallerMetadata
import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import dev.goodwy.rphone.view.screen.ExpressiveCallScreen
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallPhotoFirstFrameTest {
    private lateinit var callPrefs: PreferenceManager
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val lookup = CompletableDeferred<Contact?>()
    private val replacementLookup = CompletableDeferred<Contact?>()
    private val metadata = mutableStateOf<CallerMetadata?>(null)
    private val displayed = mutableStateOf<Call?>(null)
    @Volatile private var latest: CallIdentity? = null
    private val identityVersion=mutableStateOf(0)
    private lateinit var photo: String
    private val audio = mutableStateOf<android.telecom.CallAudioState?>(null)

    @After fun cleanup() { lookup.complete(null); replacementLookup.complete(null); stopKoin() }

    @Test fun fullPhotoKeepsFallbackAndArrivesWithoutExitFadeLight() = verifyPending(false)
    @Test fun fullPhotoKeepsFallbackAndArrivesWithoutExitFadeDark() = verifyPending(true)

    private fun verifyPending(dark: Boolean) {
        setup(fullPhoto = true, dark = dark)
        assertFalse(latest!!.photoLookupComplete)
        avatarCount(0)
        rule.onAllNodesWithTag("callActionControls", useUnmergedTree = true).assertCountEquals(1)
        capture("${if (dark) "dark" else "light"}-pending")
        lookup.complete(Contact(id = "local-test", givenName = "Test Alexandru", familyName = "Butica", photoUri = photo))
        awaitLookup()
        frames()
        avatarCount(0)
        rule.onAllNodesWithTag("callFullPhotoSharp", useUnmergedTree = true).assertCountEquals(1)
        capture("${if (dark) "dark" else "light"}-photo-first")
        rule.waitUntil(5_000) {
            rule.mainClock.advanceTimeByFrame()
            var decoded = false
            rule.runOnIdle {
                val view = rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val pixel = bitmap.getPixel(view.width / 2, view.height * 2 / 5)
                decoded = android.graphics.Color.green(pixel) > android.graphics.Color.red(pixel) + 10 &&
                    android.graphics.Color.green(pixel) > android.graphics.Color.blue(pixel) + 10
                bitmap.recycle()
            }
            decoded
        }
        capture("${if (dark) "dark" else "light"}-photo-decoded")
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        avatarCount(0)
        // A replacement caller starts pending again, rather than borrowing the old photo.
        rule.runOnIdle { displayed.value = call("0700000022") }
        frames()
rule.waitUntil(5000) { rule.mainClock.advanceTimeByFrame(); var ready=false; rule.runOnIdle { ready=latest?.number == "0700000022" }; ready }
        assertEquals("0700000022", latest!!.number)
        assertFalse(latest!!.photoLookupComplete)
        assertNull(latest!!.photoUri)
        avatarCount(0)
    }

    @Test fun telecomPhotoIsAvailableInFirstCompositionWithoutWaitingForContactLookup() {
        setup(fullPhoto=true,telecomPhoto=true)
        assertFalse(latest!!.photoLookupComplete)
        assertEquals("content://test-images/caller-photo",latest!!.photoUri)
        avatarCount(0)
        rule.onAllNodesWithTag("callFullPhotoSharp",true).assertCountEquals(1)
    }
    @Test fun servicePhotoIsUsedInInitialCompositionBeforeActivityLookupCompletes() {
        setup(fullPhoto = true, servicePhoto = true)
        assertTrue(latest!!.photoLookupComplete)
        assertEquals(photo, latest!!.photoUri)
        assertEquals("Test Alexandru Butica", latest!!.name)
        avatarCount(0)
        rule.onAllNodesWithTag("callFullPhotoSharp", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun confirmedAbsentPhotoUsesFallbackAndLatePhotoRemovesItImmediately() {
        setup(fullPhoto = true)
        avatarCount(0)
        lookup.complete(null)
        awaitLookup()
        frames()
        avatarCount(1)
        capture("confirmed-no-photo")
        rule.runOnIdle { metadata.value = CallerMetadata(NUMBER, "Test Alexandru Butica", true, photo) }
        rule.waitUntil(5000) {
            rule.mainClock.advanceTimeByFrame()
            var ready=false
            rule.runOnIdle { ready=latest?.photoUri == photo }
            ready
        }
        frames()
        avatarCount(0)
        capture("late-photo-no-exit-fade")
    }

    @Test fun circularModeKeepsItsAvatarWhileLookupIsPending() {
        setup(fullPhoto = false)
        avatarCount(1)
    }

    @Test fun failedLocalLookupSettlesToFallbackWithoutBlockingControls() {
        setup(fullPhoto = true)
        lookup.completeExceptionally(SecurityException("Contacts unavailable"))
        awaitLookup()
        frames()
        assertNull(latest!!.photoUri)
        avatarCount(1)
        rule.onAllNodesWithTag("callActionControls", useUnmergedTree = true).assertCountEquals(1)
    }

    private fun setup(fullPhoto: Boolean, dark: Boolean = false, servicePhoto: Boolean = false, customBackground: Boolean = false, softwareRendering: Boolean = customBackground, telecomPhoto: Boolean = false) {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        callPrefs = prefs
        prefs.setString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, if (fullPhoto) PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO else PreferenceManager.CALL_SCREEN_APPEARANCE_CIRCULAR_AVATAR)
        prefs.setBoolean(PreferenceManager.KEY_SHOW_CALL_SCREEN_AVATAR, true)
        prefs.setBoolean(PreferenceManager.KEY_HIDE_AVATAR_WITH_BACKGROUND, false)
        if (customBackground) prefs.setBoolean(dev.goodwy.rphone.controller.util.AmbientDropletPreference.ENABLED_KEY, false)
        if (customBackground && !softwareRendering) {
            prefs.setBoolean(PreferenceManager.KEY_LIQUID_GLASS, true)
            prefs.setBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, true)
        }
        runBlocking { prefs.getPersistedPreferences() }
        CallBackgroundStore.attach(prefs)
        val background = if (customBackground) File(context.filesDir, "caller-background.jpg").also { file ->
            Bitmap.createBitmap(32, 80, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(android.graphics.Color.RED)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bitmap.recycle()
            }
        }.toURI().toString() else null
        val image = File(context.filesDir, "caller-first-frame.jpg")
        Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.rgb(70, 130, 90))
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
        }
        photo = Uri.fromFile(image).toString()
        @Suppress("UNCHECKED_CAST")
        val contacts = Proxy.newProxyInstance(IContactsRepository::class.java.classLoader, arrayOf(IContactsRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getContactByNumber", "getContactByNumberInRegion" -> suspend { if (args!![0] == NUMBER) lookup.await() else replacementLookup.await() }
                    .startCoroutineUninterceptedOrReturn(args!!.last() as Continuation<Contact?>)
                "toString" -> "Local contacts fixture"
                else -> null
            }
        } as IContactsRepository
        val repo = CallRepositoryImpl()
        val manager = CallStateManager(GetCallerNameUseCase(object : ICallerRepository {
            override suspend fun getContactByNumber(number: String): Contact? = null
        }))
        val viewModel = CallViewModel(context, repo, manager)
        startKoin { modules(module { single { prefs }; single<IContactsRepository> { contacts }; single { viewModel } }) }
        val resolver = CallIdentityResolver(contacts)
        displayed.value = call(NUMBER)
        if(telecomPhoto) ReflectionHelpers.setField(displayed.value!!.details,"mContactPhotoUri",android.net.Uri.parse("content://test-images/caller-photo"))
        if (servicePhoto) metadata.value = CallerMetadata(NUMBER, "Test Alexandru Butica", true, photo)
        rule.mainClock.autoAdvance = false
        val softwareView = object : android.view.View(rule.activity) { override fun isHardwareAccelerated() = false }
        rule.setContent {
            CompositionLocalProvider(LocalView provides if (softwareRendering) softwareView else LocalView.current) {
            DotDialerTheme(systemDark = dark) {
                val active = displayed.value!!
                val identity = resolver.rememberIdentity(active, identityVersion.value, metadata.value)
                androidx.compose.runtime.SideEffect { latest = identity }
                ExpressiveCallScreen(active, active.currentTelecomState(), identity.name, identity.company, identity.number,
                    identity.photoUri, audio.value, liquidGlassBackdrop = rememberLayerBackdrop(),
                    backgroundUri = background,
                    photoLookupComplete = identity.photoLookupComplete)
            }
            }
        }
        frames()
    }

    private fun avatarCount(count: Int) = rule.onAllNodesWithTag("callPrimaryAvatar", useUnmergedTree = true).assertCountEquals(count)
    @Test fun modernEndpointListEnablesAudioButtonDuringActiveFullPhotoCall() {
        setup(fullPhoto = true, servicePhoto = true, softwareRendering = true)
        val context = RuntimeEnvironment.getApplication()
        val handset = android.telecom.CallEndpoint("Handset", android.telecom.CallEndpoint.TYPE_EARPIECE,
            android.os.ParcelUuid(java.util.UUID.randomUUID()))
        val speaker = android.telecom.CallEndpoint("Speaker", android.telecom.CallEndpoint.TYPE_SPEAKER,
            android.os.ParcelUuid(java.util.UUID.randomUUID()))
        val router = dev.goodwy.rphone.controller.util.CallEndpointAudioRouter({ _, _ -> }, { audio.value = it })
        rule.runOnIdle {
            val active = displayed.value!!
            ReflectionHelpers.setField(active, "mState", Call.STATE_ACTIVE)
            ReflectionHelpers.setField(active.details, "mState", Call.STATE_ACTIVE)
            router.legacyChanged(android.telecom.CallAudioState(false, 1, 1))
        }
        frames()
        rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.audio_route_handset)).assertExists()
        rule.onAllNodesWithTag("callRoundActionSurface", true)[2].assertIsNotEnabled()
        rule.runOnIdle { router.currentChanged(handset); router.availableChanged(listOf(handset, speaker)) }
        frames()
        rule.onAllNodesWithTag("callRoundActionSurface", true)[2].assertIsEnabled()
        capture("speaker-control-enabled-from-endpoints")
        rule.runOnIdle { router.currentChanged(speaker) }
        frames()
        rule.onNodeWithText(context.getString(dev.goodwy.rphone.R.string.audio_route_speaker)).assertExists()
        rule.onAllNodesWithTag("callRoundActionSurface", true)[2].assertIsEnabled()
        rule.runOnIdle { router.availableChanged(listOf(handset)) }
        frames()
        rule.onAllNodesWithTag("callRoundActionSurface", true)[2].assertIsNotEnabled()
    }
    @Test fun opticalCircularBackgroundAttachesWithoutUnsupportedShape() {
        setup(fullPhoto = false, servicePhoto = true, customBackground = true, softwareRendering = false)
        avatarCount(1)
        rule.onNodeWithTag("callCustomBackgroundGlass", true).assertExists()
        rule.onNodeWithTag("callCustomBackgroundOptical", true).assertExists()
        rule.onNodeWithTag("callActionControls", true).assertExists()
        // Pixel snapshots use a software Canvas, which cannot execute the RuntimeShader branch.
    }
    @Test fun localCallGlassAndRetiredHideFlagRemainIndependentOfGlobalMasters() {
        setup(fullPhoto = false, servicePhoto = true, customBackground = true, softwareRendering = false)
        rule.runOnIdle {
            callPrefs.setBoolean(PreferenceManager.KEY_LIQUID_GLASS, false)
            callPrefs.setBoolean(PreferenceManager.KEY_BLUR_EFFECTS, false)
            callPrefs.setBoolean(PreferenceManager.KEY_HIDE_AVATAR_WITH_BACKGROUND, true)
        }
        runBlocking { callPrefs.getPersistedPreferences() }
        frames()
        avatarCount(1)
        rule.onNodeWithTag("callCustomBackgroundOptical", true).assertExists()
        rule.runOnIdle { callPrefs.setBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, false) }
        runBlocking { callPrefs.getPersistedPreferences() }
        frames()
        rule.onNodeWithTag("callCustomBackgroundOptical", true).assertDoesNotExist()
        rule.onNodeWithTag("callCustomBackgroundGlass", true).assertExists()
        assertTrue(callPrefs.getBoolean(PreferenceManager.KEY_HIDE_AVATAR_WITH_BACKGROUND, false))
        assertFalse(callPrefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, true))
    }
    @Test fun fullPhotoCallScreenUsesPortraitEvenWhenRectangularBackgroundExists() {
        setup(fullPhoto = true, servicePhoto = true, customBackground = true)
        rule.onNodeWithTag("callCustomBackgroundGlass", true).assertDoesNotExist()
        rule.onNodeWithTag("callFullPhotoSharp", true).assertExists()
        rule.waitUntil(10000) {
            rule.mainClock.advanceTimeBy(40)
            val pixel = backgroundPixel(180, 360)
            android.graphics.Color.green(pixel) > android.graphics.Color.red(pixel) + 10
        }
        rule.onNodeWithTag("callActionControls", true).assertExists()
        capture("full-photo-custom-ignored")
    }
    @Test fun circularCallScreenKeepsAvatarAndIgnoresLiveDialpadTransparencyForCustomImage() {
        setup(fullPhoto = false, servicePhoto = true, customBackground = true)
        avatarCount(1)
        rule.onNodeWithTag("callCustomBackgroundGlass", true).assertExists()
        rule.onNodeWithTag("callFullPhotoSharp", true).assertDoesNotExist()
        rule.waitUntil(10000) {
            rule.mainClock.advanceTimeBy(40)
            val pixel = backgroundPixel(4, 360)
            android.graphics.Color.red(pixel) > android.graphics.Color.green(pixel) + 15
        }
rule.runOnIdle { org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2)) }
        rule.mainClock.advanceTimeBy(1000);rule.waitForIdle()
        capture("circular-custom-fixed50-before-a09")
        val greenBefore = android.graphics.Color.green(backgroundPixel(4, 360))
        rule.runOnIdle { callPrefs.setInt(PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH, 100) }
        runBlocking { callPrefs.getPersistedPreferences() }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        assertEquals(greenBefore, android.graphics.Color.green(backgroundPixel(4, 360)))
        avatarCount(1)
        rule.onNodeWithTag("callActionControls", true).assertExists()
        capture("circular-custom-fixed50-after-a09")
    }
    @Test fun knownServicePhotoSurvivesRefreshAndTerminalMetadataRemovalWithoutInitialCircle() {
        setup(fullPhoto=true,servicePhoto=true)
        lookup.complete(null)
        frames()
        val active=displayed.value!!
        for(state in listOf(Call.STATE_CONNECTING,Call.STATE_DIALING,Call.STATE_ACTIVE,Call.STATE_DISCONNECTING,Call.STATE_DISCONNECTED)) {
            rule.runOnIdle {
                metadata.value=null
                ReflectionHelpers.setField(active,"mState",state)
                ReflectionHelpers.setField(active.details,"mState",state)
                identityVersion.value++
            }
            repeat(8) {
                frames()
                avatarCount(0)
                rule.onNodeWithTag("callFullPhotoSharp",true).assertExists()
                assertEquals("Test Alexandru Butica", latest!!.name)
            }
        }
        capture("terminal-photo-retained")
    }
    @Test fun replacementCallerAndRestrictedPresentationNeverBorrowRetainedPhoto() {
        setup(fullPhoto=true,servicePhoto=true)
        avatarCount(0)
        rule.runOnIdle { displayed.value=call("0700000022");metadata.value=null;identityVersion.value++ }
        rule.waitUntil(5000) { rule.mainClock.advanceTimeByFrame(); var ready=false; rule.runOnIdle { ready=latest?.number == "0700000022" }; ready }
        frames()
        assertNull(latest!!.photoUri)
        avatarCount(0)
        rule.onAllNodesWithTag("callFullPhotoSharp",true).assertCountEquals(0)
        replacementLookup.complete(null)
        awaitLookup();frames();avatarCount(1)
        rule.runOnIdle {
            val active=displayed.value!!
            ReflectionHelpers.setField(active.details,"mHandlePresentation",android.telecom.TelecomManager.PRESENTATION_RESTRICTED)
            metadata.value=CallerMetadata(NUMBER,"Test Alexandru Butica",true,photo)
            identityVersion.value++
        }
        rule.waitUntil(5000) { rule.mainClock.advanceTimeByFrame(); var ready=false; rule.runOnIdle { ready=latest?.number?.isBlank() == true }; ready }
        frames();assertNull(latest!!.photoUri)
        avatarCount(1)
    }
    private fun backgroundPixel(x: Int, y: Int): Int {
        var color = 0
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap));color = bitmap.getPixel(x, y);bitmap.recycle()
        }
        return color
    }
    private fun awaitLookup() = rule.waitUntil(5_000) {
        rule.mainClock.advanceTimeByFrame()
        latest?.photoLookupComplete == true
    }
    private fun frames() { repeat(2) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle() }
    private fun capture(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File(System.getProperty("user.dir"), "build/reports/visual/call-photo-first-frame/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun call(number: String): Call {
        val call = Shadow.newInstanceOf(Call::class.java)
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        ReflectionHelpers.setField(details, "mState", Call.STATE_DIALING)
        ReflectionHelpers.setField(details, "mHandle", Uri.fromParts("tel", number, null))
        ReflectionHelpers.setField(details, "mHandlePresentation", TelecomManager.PRESENTATION_ALLOWED)
        ReflectionHelpers.setField(details, "mCallerDisplayNamePresentation", TelecomManager.PRESENTATION_ALLOWED)
        ReflectionHelpers.setField(details, "mExtras", Bundle())
        ReflectionHelpers.setField(call, "mDetails", details)
        ReflectionHelpers.setField(call, "mState", Call.STATE_DIALING)
        ReflectionHelpers.setField(call, "mCallbackRecords", CopyOnWriteArrayList<Any>())
        ReflectionHelpers.setField(call, "mChildren", arrayListOf<Call>())
        ReflectionHelpers.setField(call, "mUnmodifiableChildren", Collections.emptyList<Call>())
        ReflectionHelpers.setField(call, "mChildrenCached", true)
        ReflectionHelpers.setField(call, "mConferenceableCalls", arrayListOf<Call>())
        ReflectionHelpers.setField(call, "mUnmodifiableConferenceableCalls", Collections.emptyList<Call>())
        return call
    }

    companion object { private const val NUMBER = "0700000011" }
}
