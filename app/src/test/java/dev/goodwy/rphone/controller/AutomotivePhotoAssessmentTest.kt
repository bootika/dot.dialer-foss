package dev.goodwy.rphone.controller

import android.app.Application
import android.app.Notification
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.core.graphics.createBitmap
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.modal.data.Contact
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

/** Characterizes app-owned data paths only; does not emulate Android Auto or PBAP. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutomotivePhotoAssessmentTest {
    @Test fun suppliedPhotoIsPublishedDuringConnectingDialingRingingAndActive() {
        val context = RuntimeEnvironment.getApplication() as Context
        val manager = CallNotificationManager(context, PreferenceManager(context))
        val photo = createBitmap(64, 64).apply { eraseColor(Color.RED) }
        for (state in listOf(Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_RINGING, Call.STATE_ACTIVE)) {
            val notification = manager.buildCallNotification(call(state), "Test Alexandru Butica", photo, null)
            assertEquals(Notification.CATEGORY_CALL, notification.category)
            val large = requireNotNull(notification.getLargeIcon()).loadDrawable(context) as BitmapDrawable
            assertEquals(Color.RED, large.bitmap.getPixel(large.bitmap.width / 2, large.bitmap.height / 2))
            val person = requireNotNull(notification.extras.getParcelable(
                Notification.EXTRA_CALL_PERSON, android.app.Person::class.java,
            ))
            assertNotNull(person.icon)
            val icon = person.icon!!.loadDrawable(context) as BitmapDrawable
            assertEquals(Color.RED, icon.bitmap.getPixel(icon.bitmap.width / 2, icon.bitmap.height / 2))
        }
    }

    @Test fun metadataWaitsForLookupAndOnlyRefreshesPhotoWhenExplicitlyRequested() = runBlocking {
        val number = "0700000001"
        val lookupStarted = CompletableDeferred<Unit>()
        val releaseLookup = CompletableDeferred<Unit>()
        var contact = Contact("12", givenName = "Test Alexandru Butica", phoneNumbers = listOf(number))
        val repository = object : ICallerRepository {
            override suspend fun getContactByNumber(number: String): Contact {
                lookupStarted.complete(Unit)
                releaseLookup.await()
                return contact
            }
        }
        val manager = CallStateManager(GetCallerNameUseCase(repository))
        manager.onNewCallReceived(number, null)
        withTimeout(10_000) { lookupStarted.await() }
        assertNull(manager.callerMetadataMap.value[number])
        releaseLookup.complete(Unit)
        withTimeout(10_000) { manager.callerMetadataMap.first { number in it } }
        assertNull(manager.callerMetadataMap.value[number]?.photoUri)
        contact = contact.copy(photoUri = "content://com.android.contacts/display_photo/42")
        // A provider change alone is not an input to CallStateManager's metadata snapshot.
        assertNull(manager.callerMetadataMap.value[number]?.photoUri)
        manager.onNewCallReceived(number, null)
        val refreshed = withTimeout(10_000) {
            manager.callerMetadataMap.first { it[number]?.photoUri == contact.photoUri }
        }
        assertEquals(contact.photoUri, refreshed[number]?.photoUri)
        manager.onCallEnded(number)
    }

    private fun call(state: Int): Call {
        val call = Shadow.newInstanceOf(Call::class.java)
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        ReflectionHelpers.setField(details, "mHandle", Uri.fromParts("tel", "0700000001", null))
        ReflectionHelpers.setField(details, "mHandlePresentation", TelecomManager.PRESENTATION_ALLOWED)
        ReflectionHelpers.setField(details, "mState", state)
        ReflectionHelpers.setField(details, "mConnectTimeMillis", if (state == Call.STATE_ACTIVE) 1_000L else 0L)
        ReflectionHelpers.setField(call, "mDetails", details)
        ReflectionHelpers.setField(call, "mState", state)
        ReflectionHelpers.setField(call, "mChildren", ArrayList<Call>())
        ReflectionHelpers.setField(call, "mUnmodifiableChildren", emptyList<Call>())
        ReflectionHelpers.setField(call, "mChildrenIds", ArrayList<String>())
        ReflectionHelpers.setField(call, "mChildrenCached", true)
        return call
    }
}
