package dev.goodwy.rphone.data.manager

import dev.goodwy.rphone.domain.model.CallerMetadata
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Singleton manager that holds the state of the current call.
 * This acts as the "Source of Truth" for the presentation layer.
 */
class CallStateManager(
    private val getCallerNameUseCase: GetCallerNameUseCase,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val resolvePhoto: suspend (String?, String?) -> String? = { local, fallback -> local ?: fallback },
    private val unknownPhoto: suspend (String) -> String? = { null },
) {
    private val lock = Any()
    private val lookups = mutableMapOf<String, Job>()
    private val generations = mutableMapOf<String, Long>()
    private var nextGeneration = 0L
    
    private val _callerMetadataMap = MutableStateFlow<Map<String, CallerMetadata>>(emptyMap())
    val callerMetadataMap: StateFlow<Map<String, CallerMetadata>> = _callerMetadataMap.asStateFlow()
    private val _metadataVersion = MutableStateFlow(0)
    val metadataVersion: StateFlow<Int> = _metadataVersion.asStateFlow()

    fun onNewCallReceived(
        number: String,
        cnam: String?,
        fallbackPhotoUri: String? = null,
        forcePhotoRefresh: Boolean = false,
        regionIso: String? = null,
    ): Job? = synchronized(lock) {
        // Android exposes conference parent calls without an address. They still belong in the
        // call graph, but there is no phone number that the contacts provider can resolve.
        if (!shouldResolveCallerMetadata(number)) return@synchronized null

        lookups.remove(number)?.cancel()
        val generation = ++nextGeneration
        generations[number] = generation
        scope.launch(start = CoroutineStart.LAZY) {
            val metadata = try {
                val local = getCallerNameUseCase(number, cnam, regionIso)
                local.copy(photoUri = resolvePhoto(local.photoUri, fallbackPhotoUri) ?:
                    if (!local.isLocalContact) unknownPhoto(number) else null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w("CallPhotos", "Caller lookup failed (${failure.javaClass.simpleName})")
                synchronized(lock) { if (generations[number] == generation) lookups.remove(number) }
                return@launch
            }
            coroutineContext.ensureActive()
            synchronized(lock) {
                if (generations[number] == generation) {
                    val changed = _callerMetadataMap.value[number] != metadata
                    _callerMetadataMap.update { current -> current + (number to metadata) }
                    if (changed || forcePhotoRefresh) _metadataVersion.update { it + 1 }
                    lookups.remove(number)
                }
            }
        }.also { lookups[number] = it; it.start() }
    }
    
    fun cancelPendingLookup(number: String) = synchronized(lock) {
        lookups.remove(number)?.cancel()
        generations.remove(number)
    }

    fun onCallEnded(number: String? = null) = synchronized(lock) {
        if (number == null) {
            lookups.values.forEach { it.cancel() }
            lookups.clear()
            generations.clear()
            _callerMetadataMap.value = emptyMap()
        } else {
            lookups.remove(number)?.cancel()
            generations.remove(number)
            _callerMetadataMap.update { current -> current - number }
        }
        _metadataVersion.update { it + 1 }
    }
}

internal fun shouldResolveCallerMetadata(number: String): Boolean = number.isNotBlank()
