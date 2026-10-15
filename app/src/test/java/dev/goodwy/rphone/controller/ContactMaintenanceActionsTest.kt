package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContactMaintenanceActionsTest {
    private fun repository() = Proxy.newProxyInstance(IContactsRepository::class.java.classLoader,
        arrayOf(IContactsRepository::class.java)) { _, _, _ -> null } as IContactsRepository

    @Test fun `screen cancellation does not cancel write or allow concurrent mutation`() = runTest {
        val release = CompletableDeferred<Unit>()
        var commits = 0
        var refreshes = 0
        val maintenance = ContactMaintenanceActions(backgroundScope, repository()) { refreshes++ }
        val screen = launch { maintenance.execute { release.await(); commits++ } }
        runCurrent()
        assertTrue(maintenance.busy.value)
        assertTrue(maintenance.execute { commits++ }.isFailure)
        screen.cancel()
        release.complete(Unit)
        runCurrent()
        assertEquals(1, commits)
        assertEquals(1, refreshes)
        assertFalse(maintenance.busy.value)
    }

    @Test fun `failed mutation refreshes possible partial changes and permits retry`() = runTest {
        var refreshes = 0
        val maintenance = ContactMaintenanceActions(backgroundScope, repository()) { refreshes++ }
        val result = maintenance.execute { throw IllegalStateException("Test provider failure") }
        assertTrue(result.isFailure)
        assertEquals(1, refreshes)
        assertFalse(maintenance.busy.value)
        assertTrue(maintenance.execute {}.isSuccess)
        assertEquals(2, refreshes)
    }
}
