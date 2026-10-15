package dev.goodwy.rphone.controller

import android.accounts.Account
import android.app.Application
import android.net.Uri
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.device_only
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactGroupMembership
import dev.goodwy.rphone.modal.data.StandardizeStats
import dev.goodwy.rphone.modal.repository.ContactsRepository
import dev.goodwy.rphone.modal.repository.CachingContactsRepository
import dev.goodwy.rphone.private_only
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel(
    application: Application,
    private val contactsRepo: IContactsRepository,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(application) {

    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
    val allContacts: StateFlow<List<Contact>> = _allContacts.asStateFlow()
    private val editCommits = ContactEditCommitCoordinator(viewModelScope)
    val contactRevision: StateFlow<Long> = editCommits.revision
    val maintenance = ContactMaintenanceActions(viewModelScope, contactsRepo) {
        editCommits.notifyChanged()
        refreshContactsAndWait()
    }
    fun editedContactId(originalId: String): String = editCommits.editedId(originalId)

    fun hasContactEditCommit(sessionId: String): Boolean = editCommits.contains(sessionId)

    /** Independent of a screen coroutine, so rotation cannot interrupt a provider commit or duplicate a new contact. */
    suspend fun commitContactEdit(sessionId: String, draft: Contact, rawId: String?): Contact {
        return editCommits.commit(sessionId,draft,rawId) {
            if (rawId != null && !draft.isPrivate) updateRawContact(rawId, draft) else saveContact(draft)
        }
    }

    fun releaseContactEditCommit(sessionId: String) { editCommits.release(sessionId) }

    private val _allContactsFull = MutableStateFlow<List<Contact>>(emptyList())
    val allContactsFull: StateFlow<List<Contact>> = _allContactsFull.asStateFlow()
    private var isFullContactsLoaded = false
    private var providerRefreshJob: Job? = null
    private val contactsFetchMutex = Mutex()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _standardizeProgress = MutableStateFlow<Float?>(null)
    val standardizeProgress: StateFlow<Float?> = _standardizeProgress.asStateFlow()

    private val _availableAccounts = MutableStateFlow<List<Account>>(emptyList())
    val availableAccounts = _availableAccounts.asStateFlow()

    private val _availableAccountsForMoving = MutableStateFlow<List<Account>>(emptyList())
    val availableAccountsForMoving = _availableAccountsForMoving.asStateFlow()

    private val _selectedAccount = MutableStateFlow<Account?>(null)
    val selectedAccount = _selectedAccount.asStateFlow()

    private val _showPrivateOnly = MutableStateFlow(false)
    val showPrivateOnly = _showPrivateOnly.asStateFlow()

    private val _showLocalOnly = MutableStateFlow(false)
    val showLocalOnly = _showLocalOnly.asStateFlow()

    private val _visibleAccounts = MutableStateFlow(preferenceManager.getVisibleAccounts())
    val visibleAccountsFlow = _visibleAccounts.asStateFlow()

    private val _sortOrder = MutableStateFlow(preferenceManager.getInt(PreferenceManager.KEY_CONTACT_SORT_ORDER, 0))
    val sortOrder = _sortOrder.asStateFlow()

    private val _displayOrder = MutableStateFlow(preferenceManager.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0))
    val displayOrder = _displayOrder.asStateFlow()

    val filteredAvailableAccounts: StateFlow<List<Account>> = combine(
        _availableAccounts,
        _visibleAccounts
    ) { accounts, visibleAccounts ->
        if (visibleAccounts == null) {
            accounts
        } else {
            accounts.filter { account ->
                val key = "${account.type}|${account.name}"
                visibleAccounts.contains(key)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val contactFilterInputs = combine(
        _allContacts,
        _selectedAccount,
        _showPrivateOnly,
        _showLocalOnly,
        _visibleAccounts,
    ) { contacts, account, privateOnly, localOnly, visibleAccounts ->
        ContactFilterInputs(
            contacts = contacts,
            accountName = account?.name,
            accountType = account?.type,
            privateOnly = privateOnly,
            localOnly = localOnly,
            visibleAccounts = visibleAccounts,
        )
    }

    val filteredContacts = combine(contactFilterInputs, _sortOrder, ::filterAndSortContacts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedContacts = combine(filteredContacts, _sortOrder) { contacts, sortOrder ->
        groupContactsByInitial(contacts, sortOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            preferenceManager.settingsChanged.collect {
                _visibleAccounts.value = preferenceManager.getVisibleAccounts()
                _sortOrder.value = preferenceManager.getInt(PreferenceManager.KEY_CONTACT_SORT_ORDER, 0)
                _displayOrder.value = preferenceManager.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0)
            }
        }
        fetchAccounts()
        (contactsRepo as? CachingContactsRepository)?.let { cachingRepository ->
            viewModelScope.launch {
                cachingRepository.changes.collect {
                    editCommits.notifyChanged()
                    providerRefreshJob?.cancel()
                    providerRefreshJob = viewModelScope.launch {
                        delay(CONTACT_PROVIDER_DEBOUNCE_MILLIS)
                        val refreshFullSnapshot = isFullContactsLoaded
                        fetchContacts()
                        if (refreshFullSnapshot) {
                            isFullContactsLoaded = false
                            fetchContactsFull()
                        }
                    }
                }
            }
        }
    }

    fun fetchContacts() {
        viewModelScope.launch {
            loadContacts(forceRefresh = false)
        }
    }

    /** Forces a fresh provider snapshot and completes only after contacts and account sources load. */
    suspend fun refreshContactsAndWait() {
        loadContacts(forceRefresh = true)
        loadAccounts()
    }

    private suspend fun loadContacts(forceRefresh: Boolean) = contactsFetchMutex.withLock {
        if (forceRefresh) {
            (contactsRepo as? CachingContactsRepository)?.invalidate()
            isFullContactsLoaded = false
        }
        if (_allContacts.value.isEmpty()) {
            _isLoading.value = true
        }
        try {
            _allContacts.value = contactsRepo.getContacts()
        } finally {
            _isLoading.value = false
        }
    }

    fun fetchContactsFull() {
        if (!isFullContactsLoaded) {
            isFullContactsLoaded = true
            viewModelScope.launch {
                val fullContacts = contactsRepo.getContactsFull()
                _allContactsFull.value = fullContacts
            }
        }
    }

    companion object {
        private const val CONTACT_PROVIDER_DEBOUNCE_MILLIS = 300L
    }

    suspend fun getFullContactById(contactId: String): Contact? {
        return contactsRepo.getContactById(contactId)
    }

    suspend fun getFullContactByNumber(number: String): Contact? {
        return contactsRepo.getContactByNumber(number)
    }

    fun fetchAccounts() {
        viewModelScope.launch {
            loadAccounts()
        }
    }

    private suspend fun loadAccounts() {
        _availableAccounts.value = contactsRepo.getAvailableAccounts()
        _availableAccountsForMoving.value = contactsRepo.getAvailableAccountsForMoving()
    }

    fun selectAccount(account: Account?) {
        _selectedAccount.value = account
        if (account != null) {
            _showPrivateOnly.value = false
            _showLocalOnly.value = false
        }
    }
    fun setShowPrivateOnly(show: Boolean) {
        _showPrivateOnly.value = false
        if (show) {
            _selectedAccount.value = null
            _showLocalOnly.value = false
        }
    }

    fun setShowLocalOnly(show: Boolean) {
        _showLocalOnly.value = show
        if (show) {
            _selectedAccount.value = null
            _showPrivateOnly.value = false
        }
    }

    fun setVisibleAccounts(accounts: Set<String>?) {
        if (accounts == null) {
            preferenceManager.setString(PreferenceManager.KEY_VISIBLE_ACCOUNTS, null)
        } else {
            preferenceManager.setVisibleAccounts(accounts)
        }
        _visibleAccounts.value = accounts
    }

    fun setSortOrder(order: Int) {
        preferenceManager.setInt(PreferenceManager.KEY_CONTACT_SORT_ORDER, order)
        _sortOrder.value = order
    }

    fun setDisplayOrder(order: Int) {
        preferenceManager.setInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, order)
        _displayOrder.value = order
    }

    fun toggleFavorite(contact: Contact, requestedState: Boolean? = null) {
        viewModelScope.launch {
            val targetState = resolveFavoriteState(contact.isFavorite, requestedState)
            contactsRepo.toggleFavorite(contact.id, targetState)

            val currentOrder = preferenceManager.getFavoritesOrder().toMutableList()
            if (targetState) {
                if (!currentOrder.contains(contact.id)) {
                    currentOrder.add(contact.id)
                    preferenceManager.setFavoritesOrder(currentOrder)
                }
            } else {
                if (currentOrder.contains(contact.id)) {
                    currentOrder.remove(contact.id)
                    preferenceManager.setFavoritesOrder(currentOrder)
                }
            }

            fetchContacts()
        }
    }

    fun addFavorites(contacts: Collection<Contact>) = viewModelScope.launch {
        val additions = contacts.filterNot(Contact::isFavorite).distinctBy(Contact::id)
        if (additions.isEmpty()) return@launch
        additions.forEach { contact -> contactsRepo.toggleFavorite(contact.id, true) }
        val addedIds = additions.map(Contact::id)
        preferenceManager.setFavoritesOrder((preferenceManager.getFavoritesOrder() + addedIds).distinct())
        fetchContacts()
    }

    suspend fun saveContact(contact: Contact): Contact =
        editCommits.persist(contactsRepo,preferenceManager,contact,null).also { fetchContacts() }

    fun getLastUsedAccount(): Account? {
        val lastUsedName = preferenceManager.getString(PreferenceManager.KEY_LAST_USED_ACCOUNT_NAME, null)
        val lastUsedType = preferenceManager.getString(PreferenceManager.KEY_LAST_USED_ACCOUNT_TYPE, null)

        if (lastUsedName == private_only && lastUsedType == private_only) {
            return null
        }

        if (lastUsedName == device_only && lastUsedType == device_only) {
            return null
        }

        if (lastUsedName != null && lastUsedType != null) {
            val lastUsed = Account(lastUsedName, lastUsedType)
            if (_availableAccountsForMoving.value.contains(lastUsed)) {
                return lastUsed
            }
        }

        val mostPopular = getAccountWithMostContacts()
        if (mostPopular != null) {
            return mostPopular
        }

        return null
    }

    private fun getAccountWithMostContacts(): Account? {
        val accounts = _availableAccountsForMoving.value
        if (accounts.isEmpty()) return null

        val accountCounts = mutableMapOf<String, Account>()
        val counts = mutableMapOf<String, Int>()

        _allContacts.value.forEach { contact ->
            if (contact.accountName != null && contact.accountType != null) {
                val key = "${contact.accountName}|${contact.accountType}"
                counts[key] = counts.getOrDefault(key, 0) + 1
                accountCounts[key] = Account(contact.accountName, contact.accountType)
            }
        }

        val maxEntry = counts.maxByOrNull { it.value }
        return maxEntry?.let { accountCounts[it.key] }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            contactsRepo.deleteContact(contactId)

            val currentOrder = preferenceManager.getFavoritesOrder().toMutableList()
            if (currentOrder.contains(contactId)) {
                currentOrder.remove(contactId)
                preferenceManager.setFavoritesOrder(currentOrder)
            }

            fetchContacts()
        }
    }

    fun deleteContacts(contactIds: List<String>) {
        viewModelScope.launch {
            contactsRepo.deleteContacts(contactIds)

            val currentOrder = preferenceManager.getFavoritesOrder().toMutableList()
            var changed = false
            contactIds.forEach { id ->
                if (currentOrder.contains(id)) {
                    currentOrder.remove(id)
                    changed = true
                }
            }
            if (changed) {
                preferenceManager.setFavoritesOrder(currentOrder)
            }

            fetchContacts()
        }
    }

    fun moveContacts(
        contactIds: List<String>,
        account: Account?,
        onComplete: (Map<String, String>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val newIds = contactsRepo.moveContacts(contactIds, account?.name, account?.type)
            fetchContacts()
            onComplete(newIds)
        }
    }

    fun findDuplicates(onResult: (List<List<Contact>>) -> Unit) {
        viewModelScope.launch {
            val duplicates = contactsRepo.findDuplicates()
            onResult(duplicates)
        }
    }

    fun mergeContacts(targetId: String, sourceIds: List<String>) {
        viewModelScope.launch {
            contactsRepo.mergeContacts(targetId, sourceIds)
            fetchContacts()
        }
    }

    fun setCustomRingtone(contactId: String, ringtoneUri: String?) {
        viewModelScope.launch {
            contactsRepo.setCustomRingtone(contactId, ringtoneUri)
        }
    }

    fun formatAllPhoneNumbers() {
        viewModelScope.launch {
            _standardizeProgress.value = 0f
            contactsRepo.formatAllPhoneNumbers { current, total ->
                _standardizeProgress.value = if (total > 0) current.toFloat() / total else 1f
            }
            fetchContacts()
            _standardizeProgress.value = null
        }
    }

    fun previewStandardize(onResult: (StandardizeStats) -> Unit) {
        viewModelScope.launch {
            val stats = withContext(Dispatchers.Default) {
                buildStandardizePreview(contactsRepo.getContacts())
            }
            onResult(stats)
        }
    }


    fun setDefaultPhoneNumber(contactId: String, phoneNumber: String, isPrimary: Boolean) {
        viewModelScope.launch {
            contactsRepo.setDefaultPhoneNumber(contactId, phoneNumber, isPrimary)
            fetchContacts()
        }
    }

    suspend fun getContactGroups(contactId: String): List<ContactGroupMembership> =
        contactsRepo.getContactGroups(contactId)

    fun setContactGroups(contactId: String, selectedGroupIds: Set<Long>) {
        viewModelScope.launch {
            contactsRepo.setContactGroups(contactId, selectedGroupIds)
            fetchContacts()
        }
    }

    suspend fun createContactGroup(contactId: String, title: String): Long? =
        contactsRepo.createContactGroup(contactId, title)

    suspend fun renameContactGroup(groupId: Long, title: String) =
        contactsRepo.renameContactGroup(groupId, title)

    suspend fun deleteContactGroup(groupId: Long) =
        contactsRepo.deleteContactGroup(groupId)

    val contactCountByAccount: Map<String, Int> get() = contactCountByAccount(_allContacts.value)

    suspend fun getContactSources(contactId: String): List<ContactsRepository.ContactSource> =
        contactSources(contactsRepo.getRawContactsForContact(contactId))

    suspend fun getPreferredEditableRawContactId(contact: Contact): String? =
        selectPreferredEditableRawContact(
            sources = contactsRepo.getRawContactsForContact(contact.id),
            preferredAccountName = contact.accountName,
            preferredAccountType = contact.accountType,
        )?.rawContactId

    suspend fun getRawContactData(rawContactId: String): Contact? = contactsRepo.getRawContactData(rawContactId)

    suspend fun updateRawContact(rawContactId: String, contact: Contact): Contact =
        editCommits.persist(contactsRepo,preferenceManager,contact,rawContactId).also { fetchContacts() }

    fun unmergeAll(contactId: String) {
        viewModelScope.launch {
            val result=maintenance.execute { it.unmergeAllSources(contactId) }
            if(result.isFailure) android.widget.Toast.makeText(getApplication(),
                dev.goodwy.rphone.R.string.contact_maintenance_error,android.widget.Toast.LENGTH_LONG).show()
        }
    }

    suspend fun dumpContact(contactId: String): String {
        return contactsRepo.dumpContact(contactId)
    }
}
