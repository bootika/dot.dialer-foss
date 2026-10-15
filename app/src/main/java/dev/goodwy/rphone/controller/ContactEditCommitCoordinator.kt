package dev.goodwy.rphone.controller

import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.device_only
import dev.goodwy.rphone.private_only
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A screen may stop awaiting on rotation, without cancelling the ViewModel-owned write. */
internal class ContactEditCommitCoordinator(private val scope: CoroutineScope) {
    private data class Commit(val draft: Contact,val rawId: String?,val work: Deferred<Contact>)
    private val commits=mutableMapOf<String,Commit>()
    private val identities=mutableMapOf<String,String>()
    private val _revision=MutableStateFlow(0L)
    val revision=_revision.asStateFlow()
    fun notifyChanged() { _revision.value++ }
    fun editedId(originalId: String)=identities[originalId] ?: originalId
    fun contains(sessionId: String)=commits[sessionId]?.work?.isCancelled == false
    fun release(sessionId: String) { commits.remove(sessionId) }
    suspend fun persist(repository: IContactsRepository,prefs: PreferenceManager,draft: Contact,rawId: String?): Contact {
        val saved=if(rawId == null) {
            repository.saveContactAndRead(draft).also { rememberLastContactAccount(prefs,draft) }
        } else {
            repository.updateRawContact(rawId,draft)
            requireNotNull(repository.getRawContactData(rawId))
        }
        if(saved.id != draft.id && draft.id != "0") identities[draft.id]=saved.id
        notifyChanged()
        return saved
    }
    suspend fun commit(sessionId: String,draft: Contact,rawId: String?,write: suspend () -> Contact): Contact {
        val previous=commits[sessionId]
        val work=if(previous != null && !previous.work.isCancelled &&
            (previous.work.isActive || (previous.draft == draft && previous.rawId == rawId))) previous.work
        else scope.async { write() }.also { commits[sessionId]=Commit(draft,rawId,it) }
        return work.await()
    }
}

/** Preference failure must not turn an already verified provider write into a failed contact save. */
internal suspend fun rememberLastContactAccount(prefs: PreferenceManager,contact: Contact) {
    try {
        val fallback=if(contact.isPrivate) private_only else device_only
        prefs.setString(PreferenceManager.KEY_LAST_USED_ACCOUNT_NAME,contact.accountName ?: fallback)
        prefs.setString(PreferenceManager.KEY_LAST_USED_ACCOUNT_TYPE,contact.accountType ?: fallback)
    } catch(error: CancellationException) { throw error
    } catch(error: Exception) {
        android.util.Log.w("ContactsViewModel","Saved contact; last account preference unavailable",error)
    }
}
