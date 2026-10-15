package dev.goodwy.rphone.controller

import android.app.Application
import android.net.Uri
import android.telecom.Call
import android.telecom.TelecomManager
import dev.goodwy.rphone.controller.util.CallPhotoHydrationOutcome
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.modal.data.Contact
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CallPhotoSessionCoordinatorTest {
    @Test fun firstIncomingPublicationHasTelecomPhotoBeforeContactLookupCompletes() = runTest {
        val uri = "content://com.android.contacts/display_photo/8"
        val incoming = call(state = Call.STATE_RINGING, photo = uri)
        val selected = notificationCallPhotoUri(incoming, null)
        val published = mutableListOf<String>()
        assertTrue(publishCallPhotoNotification(selected != null,
            decode = { assertEquals(uri, selected); "decoded-photo" }, current = { true },
            build = { it ?: "standard-avatar" }, publish = published::add))
        assertEquals(listOf("decoded-photo"), published)
    }

    @Test fun notificationPhotoKeepsCachedPriorityAndDoesNotLeakRestrictedOrEndedIdentity() {
        val uri = "content://com.android.contacts/display_photo/8"
        assertEquals("cached", notificationCallPhotoUri(call(photo = uri), "cached"))
        assertEquals(uri, notificationCallPhotoUri(call(photo = uri), ""))
        assertNull(notificationCallPhotoUri(call(), null))
        assertNull(notificationCallPhotoUri(call(photo = uri), null, sdk = 33))
        assertEquals("cached", notificationCallPhotoUri(call(photo = uri), "cached", sdk = 33))
        for (excluded in listOf(call(presentation = TelecomManager.PRESENTATION_RESTRICTED, photo = uri),
                call(conference = true, photo = uri), call(state = Call.STATE_DISCONNECTED, photo = uri),
                call("*222#", photo = uri))) assertNull(notificationCallPhotoUri(excluded, "cached"))
    }

    @Test fun unreadableInitialPhotoStillPublishesFallbackAndStaleCallNeverPublishes() = runTest {
        val incoming = call(state = Call.STATE_RINGING, photo = "content://com.android.contacts/display_photo/8")
        val selected = notificationCallPhotoUri(incoming, null)
        val published = mutableListOf<String>()
        assertTrue(publishCallPhotoNotification<String, String>(selected != null, { null }, { true },
            { it ?: "standard-avatar" }, published::add))
        assertEquals(listOf("standard-avatar"), published)
        assertFalse(publishCallPhotoNotification(true, { "decoded-photo" }, { false },
            { it ?: "standard-avatar" }, published::add))
        assertEquals(1, published.size)
    }

    @Test fun headlessOwnerPublishesLocalPhotoThenHydratedPhotoWithoutActivity() = runTest {
        var source = contact("content://com.android.contacts/display_photo/1")
        val state = CallStateManager(useCase { source }, backgroundScope)
        val gate = CompletableDeferred<Unit>()
        var hydrationCalls = 0
        val coordinator = CallPhotoSessionCoordinator(backgroundScope, state, {
            hydrationCalls++
            gate.await()
            source = source.copy(photoUri = "content://com.android.contacts/display_photo/2")
            CallPhotoHydrationOutcome.Updated
        }, MutableSharedFlow(), { true })
        val call = call()
        coordinator.update(call)
        runCurrent()
        assertEquals(source.photoUri, state.callerMetadataMap.value[NUMBER]?.photoUri)
        assertEquals(1, hydrationCalls)
        gate.complete(Unit)
        runCurrent()
        assertEquals("content://com.android.contacts/display_photo/2", state.callerMetadataMap.value[NUMBER]?.photoUri)
        coordinator.close()
        assertTrue(state.callerMetadataMap.value.isEmpty())
    }

    @Test fun providerChangesRefreshSameUriRevisionAndNewPhotoWhileHydrationDisabled() = runTest {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var source = contact("content://com.android.contacts/display_photo/1")
        val state = CallStateManager(useCase { source }, backgroundScope)
        val coordinator = CallPhotoSessionCoordinator(backgroundScope, state,
            { CallPhotoHydrationOutcome.Disabled }, changes, { true })
        coordinator.update(call())
        runCurrent()
        val firstRevision = state.metadataVersion.value
        changes.tryEmit(Unit)
        runCurrent()
        advanceTimeBy(101)
        runCurrent()
        assertTrue(state.metadataVersion.value > firstRevision)
        assertEquals(source.photoUri, state.callerMetadataMap.value[NUMBER]?.photoUri)
        source = source.copy(photoUri = null)
        changes.tryEmit(Unit)
        runCurrent()
        advanceTimeBy(101)
        runCurrent()
        assertNull(state.callerMetadataMap.value[NUMBER]?.photoUri)
        coordinator.close()
    }

    @Test fun delayedLookupCannotResurrectEndedOrReplaceNewerMetadata() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        var lookups = 0
        val state = CallStateManager(useCase {
            if (++lookups == 1) withContext(NonCancellable) { firstGate.await(); contact("old") }
            else contact("new")
        }, backgroundScope)
        state.onNewCallReceived(NUMBER, null)
        runCurrent()
        state.onCallEnded(NUMBER)
        state.onNewCallReceived(NUMBER, null)
        runCurrent()
        assertEquals("new", state.callerMetadataMap.value[NUMBER]?.photoUri)
        firstGate.complete(Unit)
        runCurrent()
        assertEquals("new", state.callerMetadataMap.value[NUMBER]?.photoUri)
        state.onCallEnded(NUMBER)
        assertTrue(state.callerMetadataMap.value.isEmpty())
    }

    @Test fun changedHandleCancelsOldHydrationAndDoesNotApplyItsCompletion() = runTest {
        val gate = CompletableDeferred<Unit>()
        val state = CallStateManager(useCase { contact() }, backgroundScope)
        val coordinator = CallPhotoSessionCoordinator(backgroundScope, state, { number ->
            if (number == NUMBER) withContext(NonCancellable) { gate.await() }
            CallPhotoHydrationOutcome.Updated
        }, MutableSharedFlow(), { true })
        val call = call()
        coordinator.update(call)
        runCurrent()
        ReflectionHelpers.setField(call.details, "mHandle", Uri.fromParts("tel", OTHER, null))
        coordinator.update(call)
        runCurrent()
        assertNull(state.callerMetadataMap.value[NUMBER])
        assertNotNull(state.callerMetadataMap.value[OTHER])
        val revision = state.metadataVersion.value
        gate.complete(Unit)
        runCurrent()
        assertNull(state.callerMetadataMap.value[NUMBER])
        assertEquals(revision, state.metadataVersion.value)
        coordinator.close()
    }

    @Test fun sameNumberCallsRemainUntilLastSessionEndsAndLateHandleStartsEnrichment() = runTest {
        val state = CallStateManager(useCase { contact() }, backgroundScope)
        var hydrations = 0
        val coordinator = CallPhotoSessionCoordinator(backgroundScope, state,
            { hydrations++; CallPhotoHydrationOutcome.Disabled }, MutableSharedFlow(), { true })
        val first = call(null)
        coordinator.update(first)
        runCurrent()
        assertEquals(0, hydrations)
        ReflectionHelpers.setField(first.details, "mHandle", Uri.fromParts("tel", NUMBER, null))
        coordinator.update(first)
        val second = call()
        coordinator.update(second)
        runCurrent()
        coordinator.remove(first)
        assertNotNull(state.callerMetadataMap.value[NUMBER])
        coordinator.remove(second)
        assertTrue(state.callerMetadataMap.value.isEmpty())
        coordinator.close()
    }

    @Test fun disconnectingStopsWorkButKeepsIdentityUntilMissedDeliveryOrRemoval() = runTest {
        val state = CallStateManager(useCase { contact("photo") }, backgroundScope)
        val gate = CompletableDeferred<Unit>()
        val coordinator = CallPhotoSessionCoordinator(backgroundScope, state,
            { gate.await(); CallPhotoHydrationOutcome.Updated }, MutableSharedFlow(), { true })
        val call = call()
        coordinator.update(call)
        runCurrent()
        ReflectionHelpers.setField(call.details, "mState", Call.STATE_DISCONNECTING)
        ReflectionHelpers.setField(call, "mState", Call.STATE_DISCONNECTING)
        coordinator.update(call)
        assertEquals("photo", state.callerMetadataMap.value[NUMBER]?.photoUri)
        val revision = state.metadataVersion.value
        gate.complete(Unit)
        runCurrent()
        assertEquals(revision, state.metadataVersion.value)
        coordinator.remove(call)
        assertTrue(state.callerMetadataMap.value.isEmpty())
        coordinator.close()
    }

    @Test fun restrictedConferenceUssdAndEndedCallsNeverRequestPhotoAndFallbackIsApiGuarded() = runTest {
        var lookups = 0
        var hydrations = 0
        val state = CallStateManager(useCase { lookups++; contact() }, backgroundScope)
        val coordinator = CallPhotoSessionCoordinator(backgroundScope, state,
            { hydrations++; CallPhotoHydrationOutcome.Updated }, MutableSharedFlow(), { true })
        listOf(call(presentation = TelecomManager.PRESENTATION_RESTRICTED), call("*222#"),
            call(state = Call.STATE_DISCONNECTED), call(conference = true)).forEach(coordinator::update)
        runCurrent()
        assertEquals(0, lookups)
        assertEquals(0, hydrations)
        val valid = call(photo = "content://com.android.contacts/display_photo/8")
        assertEquals("content://com.android.contacts/display_photo/8", telecomCallerPhotoUri(valid))
        assertNull(telecomCallerPhotoUri(valid, sdk = 33))
        coordinator.update(valid)
        runCurrent()
        assertEquals("content://com.android.contacts/display_photo/8", state.callerMetadataMap.value[NUMBER]?.photoUri)
        coordinator.close()
    }

    @Test fun actualCoordinatorPropagatesAndRefreshesTheCallRegionWithoutChangingNumber() = runTest {
        val received = mutableListOf<Pair<String, String?>>()
        val state = CallStateManager(GetCallerNameUseCase(object : ICallerRepository {
            override suspend fun getContactByNumber(number: String): Contact? = error("Region path required")
            override suspend fun getContactByNumberInRegion(number: String, regionIso: String?): Contact {
                received += number to regionIso
                return contact()
            }
        }), backgroundScope)
        var region: String? = "RO"
        val coordinator = CallPhotoSessionCoordinator(backgroundScope, state,
            { CallPhotoHydrationOutcome.Disabled }, MutableSharedFlow(), { true }, regionForCall = { region })
        val incoming = call()
        coordinator.update(incoming); runCurrent()
        assertEquals(listOf(NUMBER to "RO"), received)
        assertEquals(NUMBER, state.callerMetadataMap.value[NUMBER]?.number)
        region = null
        coordinator.update(incoming); runCurrent()
        assertEquals(NUMBER to null, received.last())
        coordinator.close()
    }

    private fun useCase(load: suspend () -> Contact) = GetCallerNameUseCase(object : ICallerRepository {
        override suspend fun getContactByNumber(number: String): Contact = load()
    })

    private fun contact(photo: String? = null) = Contact("12", givenName = "Test Alexandru Butica", photoUri = photo)

    private fun call(number: String? = NUMBER, presentation: Int = TelecomManager.PRESENTATION_ALLOWED,
        state: Int = Call.STATE_CONNECTING, conference: Boolean = false, photo: String? = null): Call {
        val call = Shadow.newInstanceOf(Call::class.java)
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        ReflectionHelpers.setField(details, "mHandle", number?.let { Uri.fromParts("tel", it, null) })
        ReflectionHelpers.setField(details, "mHandlePresentation", presentation)
        ReflectionHelpers.setField(details, "mState", state)
        ReflectionHelpers.setField(details, "mCallProperties", if (conference) Call.Details.PROPERTY_CONFERENCE else 0)
        ReflectionHelpers.setField(details, "mContactPhotoUri", photo?.let(Uri::parse))
        ReflectionHelpers.setField(call, "mDetails", details)
        ReflectionHelpers.setField(call, "mState", state)
        ReflectionHelpers.setField(call, "mChildren", ArrayList<Call>())
        ReflectionHelpers.setField(call, "mUnmodifiableChildren", emptyList<Call>())
        ReflectionHelpers.setField(call, "mChildrenIds", ArrayList<String>())
        ReflectionHelpers.setField(call, "mChildrenCached", true)
        return call
    }

    companion object { private const val NUMBER = "0700000001"; private const val OTHER = "0700000002" }
}
