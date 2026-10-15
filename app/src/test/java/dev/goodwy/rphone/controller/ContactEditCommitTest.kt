package dev.goodwy.rphone.controller

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.repository.ContactEditPersistenceAuditTest
import dev.goodwy.rphone.modal.repository.ContactsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36], application=Application::class)
class ContactEditCommitTest {
    @Test fun `cancelled screen wait does not cancel commit or duplicate creation on return`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val context=RuntimeEnvironment.getApplication()
            val delegate=ContactsRepository(context,ContactEditPersistenceAuditTest.MemoryPrivateDao())
            val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
            var writes=0
            val repository=object : IContactsRepository by delegate {
                override suspend fun saveContactAndRead(contact: Contact): Contact {
                    writes++; entered.complete(Unit); release.await()
                    return contact.copy(id="p17")
                }
            }
            val vm=ContactsViewModel(context,repository,PreferenceManager(context))
            store.put("contacts", vm)
            val draft=Contact(id="0",givenName="Test Alexandru Butica",isPrivate=true)
            val first=launch { vm.commitContactEdit("edit-session",draft,null) }
            entered.await(); first.cancelAndJoin()
            Assert.assertTrue(vm.hasContactEditCommit("edit-session"))
            val returned=async { vm.commitContactEdit("edit-session",draft,null) }
            release.complete(Unit)
            Assert.assertEquals("p17",returned.await().id)
            Assert.assertEquals("p17",vm.commitContactEdit("edit-session",draft,null).id)
            Assert.assertEquals(1,writes)
            vm.releaseContactEditCommit("edit-session")
            Assert.assertFalse(vm.hasContactEditCommit("edit-session"))
        } finally {
            closeTestViewModels(store)
        }
    }

    @Test fun `failed commit can be retried and never emits a successful revision`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val context=RuntimeEnvironment.getApplication()
            val delegate=ContactsRepository(context,ContactEditPersistenceAuditTest.MemoryPrivateDao())
            var fail=true; var writes=0
            val repository=object : IContactsRepository by delegate {
                override suspend fun saveContactAndRead(contact: Contact): Contact {
                    writes++; if(fail) throw SecurityException("Write refused")
                    return contact.copy(id="p18")
                }
            }
            val vm=ContactsViewModel(context,repository,PreferenceManager(context))
            store.put("contacts", vm)
            val before=vm.contactRevision.value
            val draft=Contact(id="0",givenName="Test Alexandru Butica",isPrivate=true)
            try { vm.commitContactEdit("retry-session",draft,null); Assert.fail("Expected failure") }
            catch (_: SecurityException) { }
            Assert.assertEquals(before,vm.contactRevision.value)
            fail=false
            Assert.assertEquals("p18",vm.commitContactEdit("retry-session",draft,null).id)
            Assert.assertEquals(2,writes)
            Assert.assertTrue(vm.contactRevision.value>before)
        } finally {
            closeTestViewModels(store)
        }
    }

    private suspend fun TestScope.closeTestViewModels(store: ViewModelStore) {
        try {
            // A failed/cancelled test must still await VM children before replacing Main.
            withContext(NonCancellable) {
                val job = store.get("contacts")?.viewModelScope?.coroutineContext?.get(Job)
                store.clear()
                job?.join()
            }
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
