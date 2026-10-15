package dev.goodwy.rphone.controller

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import java.io.File
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import androidx.compose.ui.test.onAllNodesWithText
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
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

/** Exercises the production Activity and metadata revision, omitted by screen-only fixtures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallActivityPhotoArrivalTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var activity: ActivityScenario<CallActivity>? = null
    private val serviceContact = CompletableDeferred<Contact?>()

    @After fun cleanup() { activity?.close(); serviceContact.complete(null); stopKoin() }

    @Test fun outgoingActivitySurvivesPhotoAndMetadataRevisionArrival() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(context)
        prefs.setString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO)
        prefs.setBoolean(PreferenceManager.KEY_SHOW_CALL_SCREEN_AVATAR, true)
        // Bitmap pixel assertions exercise the software fallback; optical attachment is tested separately.
        prefs.setBoolean(PreferenceManager.KEY_LIQUID_GLASS, false)
        prefs.setBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, false)
        prefs.setBoolean(PreferenceManager.KEY_BLUR_CALL_SCREEN, false)
        runBlocking { prefs.getPersistedPreferences() }
        val image = File(context.filesDir, "activity-photo-arrival.jpg")
        Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.rgb(70, 130, 90))
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
        }
        val contact = Contact(id = "local-test", givenName = "Test Alexandru", familyName = "Butica", company = "Atelier local", photoUri = Uri.fromFile(image).toString())
        val currentContact = AtomicReference(contact)
        val contacts = Proxy.newProxyInstance(IContactsRepository::class.java.classLoader, arrayOf(IContactsRepository::class.java)) { _, method, _ ->
            when (method.name) { "getContactByNumber", "getContactByNumberInRegion" -> currentContact.get(); "toString" -> "Local contact fixture"; else -> null }
        } as IContactsRepository
        val repo = CallRepositoryImpl()
        val manager = CallStateManager(GetCallerNameUseCase(object : ICallerRepository {
            override suspend fun getContactByNumber(number: String): Contact? = serviceContact.await()?.let { currentContact.get() }
        }))
        val model = CallViewModel(context, repo, manager)
        startKoin { modules(module { single { prefs }; single<IContactsRepository> { contacts }; single { model }; single { manager } }) }
        rule.mainClock.autoAdvance = false
        activity = ActivityScenario.launch(Intent(context, CallActivity::class.java).putExtra(CallActivity.EXTRA_PENDING_OUTGOING_CALL, true))
        frames()
        val call = newCall()
        rule.runOnIdle { repo.updateAllCalls(listOf(call)); repo.updateCurrentCallSession(CallSession(call, Call.STATE_CONNECTING)) }
        frames()
        rule.waitUntil(5_000) { rule.mainClock.advanceTimeByFrame(); rule.onAllNodesWithText("Atelier local").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5000) { rule.mainClock.advanceTimeByFrame(); photographIsVisible() }
        assertNull(repo.audioState.value)
        rule.runOnIdle { repo.updateAudioState(CallAudioState(true, CallAudioState.ROUTE_SPEAKER, 9)) }
        frames()
        rule.onAllNodesWithTag("callFullPhotoSharp", useUnmergedTree = true).assertCountEquals(1)
        activity!!.onActivity { assertFalse(it.isFinishing) }
        rule.runOnIdle { repo.updateAudioState(null) }
        frames()
        rule.onAllNodesWithTag("callFullPhotoSharp", useUnmergedTree = true).assertCountEquals(1)
        activity!!.onActivity { assertFalse(it.isFinishing) }
        manager.onNewCallReceived(NUMBER, null)
        rule.runOnIdle { serviceContact.complete(contact) }
        rule.waitUntil(5_000) { rule.mainClock.advanceTimeByFrame(); manager.callerMetadataMap.value[NUMBER]?.photoUri != null }
        frames()
        rule.onAllNodesWithTag("callPrimaryAvatar", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("callFullPhotoSharp", useUnmergedTree = true).assertCountEquals(1)
        activity!!.onActivity { assertFalse(it.isFinishing) }
        rule.runOnIdle { currentContact.set(contact.copy(company = "")) }
        manager.onNewCallReceived(NUMBER, null, forcePhotoRefresh = true)
        repeat(8) {
            frames()
            rule.onAllNodesWithTag("callPrimaryAvatar",useUnmergedTree=true).assertCountEquals(0)
            assertTrue(photographIsVisible())
        }
        rule.waitUntil(5_000) { rule.mainClock.advanceTimeByFrame(); rule.onAllNodesWithText("Atelier local").fetchSemanticsNodes().isEmpty() }
        frames()
        rule.onAllNodesWithTag("callFullPhotoSharp", useUnmergedTree = true).assertCountEquals(1)
        activity!!.onActivity { assertFalse(it.isFinishing) }
        rule.runOnIdle { ReflectionHelpers.setField(call, "mState", Call.STATE_DIALING); repo.updateCurrentCallSession(CallSession(call, Call.STATE_DIALING)) }
        frames()
        manager.onNewCallReceived(NUMBER, null, forcePhotoRefresh = true)
        repeat(8) {
            frames()
            rule.onAllNodesWithTag("callPrimaryAvatar",useUnmergedTree=true).assertCountEquals(0)
            assertTrue(photographIsVisible())
        }
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        activity!!.onActivity { assertFalse(it.isFinishing) }
        rule.runOnIdle {
            ReflectionHelpers.setField(call, "mState", Call.STATE_DISCONNECTED)
            repo.updateAllCalls(emptyList())
            repo.updateCurrentCallSession(null)
            manager.onCallEnded(NUMBER)
        }
        repeat(6) {
            rule.mainClock.advanceTimeByFrame();rule.waitForIdle()
            rule.onAllNodesWithTag("callPrimaryAvatar",useUnmergedTree=true).assertCountEquals(0)
            rule.onAllNodesWithTag("callFullPhotoSharp",useUnmergedTree=true).assertCountEquals(1)
            assertTrue(photographIsVisible())
        }
        rule.mainClock.advanceTimeBy(4)
        rule.waitForIdle()
        activity!!.onActivity { assertFalse(it.isFinishing) }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        activity!!.onActivity {
            assertTrue(it.isFinishing)
            assertNull(org.robolectric.Shadows.shadowOf(it).nextStartedActivity)
        }
    }

    private fun photographIsVisible():Boolean {
        var visible=false
        activity!!.onActivity { host ->
            val view=host.window.decorView
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val pixel=image.getPixel(view.width/2,view.height*2/5)
            visible=android.graphics.Color.green(pixel)>android.graphics.Color.red(pixel)+10 &&
                android.graphics.Color.green(pixel)>android.graphics.Color.blue(pixel)+10
            image.recycle()
        }
        return visible
    }
    private fun frames() { repeat(3) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle() }
    private fun newCall(): Call {
        val call = Shadow.newInstanceOf(Call::class.java)
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        ReflectionHelpers.setField(details, "mState", Call.STATE_CONNECTING)
        ReflectionHelpers.setField(details, "mHandle", Uri.fromParts("tel", NUMBER, null))
        ReflectionHelpers.setField(details, "mHandlePresentation", TelecomManager.PRESENTATION_ALLOWED)
        ReflectionHelpers.setField(details, "mCallerDisplayNamePresentation", TelecomManager.PRESENTATION_ALLOWED)
        ReflectionHelpers.setField(details, "mExtras", Bundle())
        ReflectionHelpers.setField(call, "mDetails", details)
        ReflectionHelpers.setField(call, "mState", Call.STATE_CONNECTING)
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
