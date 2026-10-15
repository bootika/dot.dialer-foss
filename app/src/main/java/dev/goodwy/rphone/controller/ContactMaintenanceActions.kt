package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** VM-owned writes survive screen rotation; one write at a time, and errors are never success. */
class ContactMaintenanceActions(
    private val scope: CoroutineScope,
    private val repository: IContactsRepository,
    private val refresh: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    suspend fun duplicateGroups() = repository.findDuplicates()
    suspend fun sources(contactId: String) = repository.getRawContactsForContact(contactId)
        .also { require(it.isNotEmpty()) { "Contact source is unavailable" } }
    suspend fun execute(action: suspend (IContactsRepository) -> Unit): Result<Unit> {
        if (_busy.value) return Result.failure(IllegalStateException("An operation is already running"))
        _busy.value = true
        return scope.async {
            mutex.withLock {
                try { action(repository); refresh(); Result.success(Unit)
                } catch (error: CancellationException) { throw error
                } catch (error: Exception) {
                    // A provider can partially commit before reporting failure. Re-read, don't hide the copy.
                    try { refresh() } catch (cancel: CancellationException) { throw cancel
                    } catch (refreshError: Exception) {
                        android.util.Log.w("ContactMaintenance", "Refresh failed: ${refreshError.javaClass.simpleName}")
                    }
                    Result.failure(error)
                } finally { _busy.value = false }
            }
        }.await()
    }
}
