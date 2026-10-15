package dev.goodwy.rphone.controller

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.repository.ContactsRepository
import dev.goodwy.rphone.modal.repository.ContactEditPersistenceAuditTest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ContactPreferencesRestoreTest {
    @Test fun `restored sources sort and name order reach an already running viewmodel`() = runBlocking<Unit> {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val store = ViewModelStore()
        try {
            val app = RuntimeEnvironment.getApplication()
            val prefs = PreferenceManager(app)
            prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_CONTACT_SORT_ORDER to 0,
                PreferenceManager.KEY_CONTACT_DISPLAY_ORDER to 0, PreferenceManager.KEY_VISIBLE_ACCOUNTS to "com.google|alex@dotdesign.ro"))
            val vm = ContactsViewModel(app, ContactsRepository(app, ContactEditPersistenceAuditTest.MemoryPrivateDao()), prefs)
            store.put("contacts", vm)
            prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_CONTACT_SORT_ORDER to 1,
                PreferenceManager.KEY_CONTACT_DISPLAY_ORDER to 1, PreferenceManager.KEY_VISIBLE_ACCOUNTS to ""))
            withTimeout(5_000) {
                assertEquals(1, vm.sortOrder.first { it == 1 })
                assertEquals(1, vm.displayOrder.first { it == 1 })
                assertEquals(emptySet<String>(), vm.visibleAccountsFlow.first { it?.isEmpty() == true })
            }
            prefs.restoreAllPreferences(emptyMap(), replacePortable = true)
            withTimeout(5_000) {
                assertEquals(0, vm.sortOrder.first { it == 0 })
                assertNull(vm.visibleAccountsFlow.first { it == null })
            }
        } finally {
            val job = store.get("contacts")?.viewModelScope?.coroutineContext?.get(Job)
            store.clear(); job?.join(); Dispatchers.resetMain()
        }
    }
}
