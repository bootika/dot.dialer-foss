package dev.goodwy.rphone.controller

import android.app.Application
import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.CallLogFilter
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.repository.CachingContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.bootika.dotdialer.core.preferences.RecentHistoryPolicy
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CallLogViewModel(
    application: Application,
    private val callLogRepo: ICallLogRepository,
    private val contentResolver: ContentResolver,
    private val preferenceManager: PreferenceManager,
    private val callNotificationManager: CallNotificationManager,
    private val contactsRepository: IContactsRepository,
) : AndroidViewModel(application) {

    private val _allCallLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val allCallLogs: StateFlow<List<CallLogEntry>> = _allCallLogs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFilter = MutableStateFlow(CallLogFilter.All)
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _filteredLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val filteredLogs: StateFlow<List<CallLogEntry>> = _filteredLogs.asStateFlow()
    private var filterJob: Job? = null

    // In-memory cache
    @Volatile private var cachedLogs: List<CallLogEntry> = emptyList()
    private val fetchMutex = Mutex()
    private var debounceJob: Job? = null
    private var contactRefreshJob: Job? = null
    private var acknowledgeMissedCallsJob: Job? = null

    // Disk cache file
    private fun showsAllCalls() = preferenceManager.getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS, false)
    private fun cacheFile(individual: Boolean) = File(getApplication<Application>().cacheDir,
        if (individual) "call_logs_events_cache.json" else "call_logs_cache.json")

    private val callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            debounceJob?.cancel()
            debounceJob = viewModelScope.launch {
                delay(300)
                fetchLogs(forceRefresh = true)
            }
        }
    }

    init {
        contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            callLogObserver
        )
        // Step 1: serve disk cache immediately so UI is instant
        viewModelScope.launch {
            val diskCache = loadFromDisk()
            if (diskCache.isNotEmpty()) {
                val boundedCache = applyCurrentHistoryPolicy(diskCache)
                cachedLogs = boundedCache
                _allCallLogs.value = boundedCache
            }
            // Step 2: refresh from provider in background
            fetchLogsInternal()
        }

        viewModelScope.launch {
            combine(
                _allCallLogs,
                _selectedFilter,
                preferenceManager.settingsChanged
            ) { logs, filter, _ ->
                val blockVisibility = preferenceManager.getInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, 0)
                Triple(logs, filter, blockVisibility)
            }.collect { (logs, filter, blockVisibility) ->
                filterLogs(logs, filter, blockVisibility)
            }
        }

        viewModelScope.launch {
            preferenceManager.settingsChanged
                .map {
                    Triple(preferenceManager.getInt(
                        PreferenceManager.KEY_RECENT_HISTORY_PERIOD_DAYS,
                        RecentHistoryPolicy.DEFAULT_PERIOD_DAYS,
                    ), preferenceManager.getInt(
                        PreferenceManager.KEY_RECENT_HISTORY_MAX_ENTRIES,
                        RecentHistoryPolicy.DEFAULT_MAX_ENTRIES,
                    ), showsAllCalls())
                }
                .distinctUntilChanged()
                .drop(1)
                .collect { fetchLogs(forceRefresh = true) }
        }

        // A contact can receive a photo or a new aggregate ID without the call-log provider
        // changing. Refresh the enriched call rows whenever the address book changes so Recents
        // cannot remain attached to a deleted contact snapshot.
        (contactsRepository as? CachingContactsRepository)?.let { cachingRepository ->
            viewModelScope.launch {
                cachingRepository.changes.collect {
                    contactRefreshJob?.cancel()
                    contactRefreshJob = viewModelScope.launch {
                        delay(CONTACT_REFRESH_DEBOUNCE_MILLIS)
                        fetchLogs(forceRefresh = true)
                    }
                }
            }
        }
    }

    private fun filterLogs(logs: List<CallLogEntry>, filter: CallLogFilter, blockVisibility: Int) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val baseLogs = if (blockVisibility == 0) logs.filter { !it.isBlocked } else logs

            val result = when (filter) {
                CallLogFilter.All -> baseLogs
                CallLogFilter.Missed -> baseLogs.filter { it.type == CallLog.Calls.MISSED_TYPE }
                CallLogFilter.Incoming -> baseLogs.filter { it.type == CallLog.Calls.INCOMING_TYPE }
                CallLogFilter.Outgoing -> baseLogs.filter { it.type == CallLog.Calls.OUTGOING_TYPE }
                CallLogFilter.Rejected -> baseLogs.filter { it.type == CallLog.Calls.REJECTED_TYPE }
                CallLogFilter.Contacts -> baseLogs.filter { it.contactId != null }
            }
            _filteredLogs.value = result
        }
    }

    fun setFilter(newFilter: CallLogFilter) {
        _selectedFilter.value = if (newFilter == _selectedFilter.value) {
            CallLogFilter.All
        } else {
            newFilter
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            contentResolver.unregisterContentObserver(callLogObserver)
        } catch (e: Exception) {
            Log.w(TAG, "Call-log observer was already unregistered", e)
        }
    }

    fun refreshLogs() {
        fetchLogs(forceRefresh = true)
    }

    /** Forces a provider query and completes after the refreshed snapshot has been published. */
    suspend fun refreshLogsAndWait() {
        fetchLogsInternal(forceRefresh = true)
    }

    fun acknowledgeMissedCalls() {
        if (acknowledgeMissedCallsJob?.isActive == true) return
        acknowledgeMissedCallsJob = viewModelScope.launch {
            callNotificationManager.clearAllMissedCallNotifications()
            callLogRepo.markMissedCallsSeen()
        }
    }

    suspend fun loadCompleteCallHistory(): List<CallLogEntry> = callLogRepo.getCompleteCallLogs()

    private fun fetchLogs(forceRefresh: Boolean = false) {
        if (!forceRefresh && cachedLogs.isNotEmpty()) {
            _allCallLogs.value = cachedLogs
            return
        }
        viewModelScope.launch {
            fetchLogsInternal(forceRefresh)
        }
    }

    private suspend fun fetchLogsInternal(forceRefresh: Boolean = true) = fetchMutex.withLock {
        if (!forceRefresh && cachedLogs.isNotEmpty()) {
            _allCallLogs.value = cachedLogs
            return@withLock
        }
        try {
            val individual = showsAllCalls()
            val result = callLogRepo.getCallLogs()
            if (individual != showsAllCalls()) return@withLock
            // Only push an update to the UI if the data actually changed.
            // This prevents a visible "refresh flicker" when the disk cache
            // and the freshly-fetched data are identical (the common case on
            // every app open after the first one).
            val changed = callLogSnapshotsDiffer(cachedLogs, result)
            cachedLogs = result
            saveToDisk(result, individual)
            if (changed) {
                _allCallLogs.value = result
            }
        } finally {
            _isLoading.value = false
        }
    }

    // ── Disk cache helpers ────────────────────────────────────────────────────

    private suspend fun saveToDisk(logs: List<CallLogEntry>, individual: Boolean) = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            logs.forEach { e ->
                val obj = JSONObject()
                obj.put("id", e.id)
                obj.put("number", e.number)
                obj.put("name", e.name ?: e.number)
                obj.put("type", e.type)
                obj.put("date", e.date)
                obj.put("duration", e.duration)
                obj.put("photoUri", e.photoUri ?: "")
                obj.put("contactId", e.contactId ?: "")
                obj.put("simLabel", e.simLabel ?: "")
                obj.put("isBlocked", e.isBlocked)
                val typesArr = JSONArray()
                e.types.forEach { typesArr.put(it) }
                obj.put("types", typesArr)
                val idsArr = JSONArray()
                e.ids.forEach { idsArr.put(it) }
                obj.put("ids", idsArr)
                obj.put("isCallerIdName", e.isCallerIdName)
                obj.put("phoneType", e.phoneType ?: -1)
                obj.put("phoneLabel", e.phoneLabel ?: "")
                arr.put(obj)
            }
            cacheFile(individual).writeText(arr.toString())
        } catch (error: Exception) {
            Log.w(TAG, "Unable to persist the call-log cache", error)
        }
    }

    private suspend fun loadFromDisk(): List<CallLogEntry> = withContext(Dispatchers.IO) {
        try {
            val individual = showsAllCalls()
            val cacheFile = cacheFile(individual)
            if (!cacheFile.exists()) return@withContext emptyList()
            val arr = JSONArray(cacheFile.readText())
            val list = mutableListOf<CallLogEntry>()
            val unknownAvatar = dev.goodwy.rphone.controller.util.UnsavedNumberAvatars.reader(getApplication(),preferenceManager)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val typesArr = obj.optJSONArray("types")
                val types = mutableListOf<Int>()
                if (typesArr != null) {
                    for (j in 0 until typesArr.length()) types.add(typesArr.getInt(j))
                }
                val idsArr = obj.optJSONArray("ids")
                val ids = mutableListOf<Long>()
                if (idsArr != null) {
                    for (j in 0 until idsArr.length()) ids.add(idsArr.getLong(j))
                }
                list.add(
                    CallLogEntry(
                        id = obj.getLong("id"),
                        number = obj.getString("number"),
                        name = obj.getString("name").ifEmpty { null },
                        type = obj.getInt("type"),
                        date = obj.getLong("date"),
                        duration = obj.getLong("duration"),
                        photoUri = obj.getString("photoUri").ifEmpty { null }.let { uri ->
                            if (dev.goodwy.rphone.controller.util.UnsavedNumberAvatars.ownedUri(getApplication(),uri))
                                unknownAvatar(obj.getString("number")) else uri },
                        contactId = obj.getString("contactId").ifEmpty { null },
                        simLabel = obj.getString("simLabel").ifEmpty { null },
                        isBlocked = obj.getBoolean("isBlocked"),
                        types = types,
                        ids = ids,
                        isCallerIdName = obj.optBoolean("isCallerIdName", false),
                        phoneType = obj.getInt("phoneType"),
                        phoneLabel = obj.getString("phoneLabel").ifEmpty { null }
                    )
                )
            }
            if (individual == showsAllCalls()) list else emptyList()
        } catch (error: Exception) {
            Log.w(TAG, "Unable to read the call-log cache", error)
            emptyList()
        }
    }

    private fun applyCurrentHistoryPolicy(logs: List<CallLogEntry>): List<CallLogEntry> {
        if (showsAllCalls()) return RecentHistoryPolicy.apply(
            entries = logs,
            periodDays = preferenceManager.getInt(PreferenceManager.KEY_RECENT_HISTORY_PERIOD_DAYS, RecentHistoryPolicy.DEFAULT_PERIOD_DAYS),
            maxEntries = preferenceManager.getInt(PreferenceManager.KEY_RECENT_HISTORY_MAX_ENTRIES, RecentHistoryPolicy.DEFAULT_MAX_ENTRIES),
            nowMillis = System.currentTimeMillis(), dateMillis = CallLogEntry::date,
        )
        return RecentHistoryPolicy.applyDistinct(
            entries = logs,
            periodDays = preferenceManager.getInt(
                PreferenceManager.KEY_RECENT_HISTORY_PERIOD_DAYS,
                RecentHistoryPolicy.DEFAULT_PERIOD_DAYS,
            ),
            maxEntries = preferenceManager.getInt(
                PreferenceManager.KEY_RECENT_HISTORY_MAX_ENTRIES,
                RecentHistoryPolicy.DEFAULT_MAX_ENTRIES,
            ),
            nowMillis = System.currentTimeMillis(),
            dateMillis = CallLogEntry::date,
            identity = { entry ->
                SearchTextNormalizer.phoneIdentityKey(entry.number) ?: entry.number
            },
        )
    }

    fun deleteCallLog(number: String) {
        viewModelScope.launch {
            callLogRepo.deleteCallLog(number)
            fetchLogs()
        }
    }

    fun deleteCallLogsByIds(ids: List<Long>) {
        viewModelScope.launch {
            callLogRepo.deleteCallLogsByIds(ids)
            fetchLogs()
        }
    }

    fun clearCallLogs() {
        viewModelScope.launch {
            callLogRepo.clearCallLogs()
            fetchLogs()
        }
    }

    private companion object {
        const val TAG = "CallLogViewModel"
        const val CONTACT_REFRESH_DEBOUNCE_MILLIS = 300L
    }
}

/** Includes presentation metadata: photo/contact changes must invalidate the Recents snapshot. */
internal fun callLogSnapshotsDiffer(
    cached: List<CallLogEntry>,
    fresh: List<CallLogEntry>,
): Boolean = cached != fresh
