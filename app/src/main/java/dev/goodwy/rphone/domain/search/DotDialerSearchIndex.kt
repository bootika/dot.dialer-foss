package dev.goodwy.rphone.domain.search

import dev.goodwy.rphone.controller.util.NoteEntry
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import io.github.bootika.dotdialer.core.search.LocalSearchIndex
import io.github.bootika.dotdialer.core.search.SearchDocument
import io.github.bootika.dotdialer.core.search.SearchDocumentKind
import io.github.bootika.dotdialer.core.search.SearchOptions
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer

sealed interface DotDialerSearchPayload {
    data class ContactResult(val contact: Contact) : DotDialerSearchPayload
    data class CallResult(val call: CallLogEntry) : DotDialerSearchPayload
    data class NoteResult(val note: NoteEntry) : DotDialerSearchPayload
}

/** Android-model adapter for the shared, platform-independent local search engine. */
object DotDialerSearchIndex {
    fun build(
        contacts: List<Contact>,
        callLogs: List<CallLogEntry>,
        notes: List<NoteEntry> = emptyList(),
        showAllCalls: Boolean = false,
    ): LocalSearchIndex<DotDialerSearchPayload> {
        val contactById = contacts.associateBy(Contact::id)
        val contactIdByNumber = buildMap<String, String> {
            contacts.forEach { contact ->
                contact.phoneNumbers.forEach { number ->
                    phoneKey(number)?.let { key ->
                        val existing = get(key)?.let(contactById::get)
                        if (existing == null || (existing.photoUri == null && contact.photoUri != null)) {
                            put(key, contact.id)
                        }
                    }
                }
            }
        }
        val usageByContact = mutableMapOf<String, Usage>()
        val callsByNumber = linkedMapOf<String, MutableList<CallLogEntry>>()

        callLogs.forEach { call ->
            val resolvedContactId = call.contactId
                ?.takeIf(contactById::containsKey)
                ?: phoneKey(call.number)?.let(contactIdByNumber::get)

            if (resolvedContactId != null) {
                usageByContact[resolvedContactId] =
                    (usageByContact[resolvedContactId] ?: Usage()).plus(call)
            }
            val key = phoneKey(call.number) ?: call.number
            val resolvedContact = resolvedContactId?.let(contactById::get)
            callsByNumber.getOrPut(key) { mutableListOf() }.add(
                if (resolvedContact != null) {
                    call.copy(
                        contactId = resolvedContact.id,
                        name = resolvedContact.displayName.ifBlank { call.name ?: call.number },
                        // A resolved provider contact is authoritative, including when its
                        // current photo is null. Falling back here resurrects a stale call-log
                        // avatar after the user removes or changes the contact photo.
                        photoUri = resolvedContact.photoUri,
                    )
                } else {
                    call
                },
            )
        }

        val documents = buildList<SearchDocument<DotDialerSearchPayload>> {
            contacts.forEach { contact ->
                val usage = usageByContact[contact.id] ?: Usage()
                add(
                    SearchDocument(
                        key = "contact:${contact.id}",
                        kind = SearchDocumentKind.CONTACT,
                        payload = DotDialerSearchPayload.ContactResult(contact),
                        primaryText = contact.displayName,
                        secondaryTexts = buildList {
                            add(contact.nickname)
                            add(contact.company)
                            add(contact.jobTitle)
                            addAll(contact.emails.map { it.value })
                            addAll(contact.addresses.map { it.formattedAddress })
                            addAll(contact.events.map { it.date })
                            contact.notes?.let(::add)
                        }.filter(String::isNotBlank),
                        phoneNumbers = contact.phoneNumbers,
                        interactionCount = usage.count,
                        lastInteractionMillis = usage.lastInteractionMillis,
                    )
                )
            }

            callsByNumber.forEach { (numberKey, calls) ->
                val newest = calls.maxBy(CallLogEntry::date)
                val merged = newest.copy(
                    types = calls.flatMap(CallLogEntry::types),
                    ids = calls.flatMap(CallLogEntry::ids).distinct(),
                )
                val totalCount = calls.sumOf(CallLogEntry::count)
                (if (showAllCalls) calls else listOf(merged)).forEach { visibleCall ->
                add(
                    SearchDocument(
                        key = if (showAllCalls) "recent:$numberKey:${visibleCall.id}" else "recent:$numberKey",
                        kind = SearchDocumentKind.RECENT_CALL,
                        payload = DotDialerSearchPayload.CallResult(visibleCall),
                        primaryText = visibleCall.name?.takeUnless { it == visibleCall.number } ?: visibleCall.number,
                        secondaryTexts = listOfNotNull(visibleCall.name?.takeUnless { it == visibleCall.number }),
                        phoneNumbers = listOf(visibleCall.number),
                        interactionCount = if (showAllCalls) visibleCall.count else totalCount,
                        lastInteractionMillis = visibleCall.date,
                    )
                )
                }
                if (newest.contactId == null) {
                    add(
                        SearchDocument(
                            key = "unsaved:$numberKey",
                            kind = SearchDocumentKind.UNSAVED_CALL,
                            payload = DotDialerSearchPayload.CallResult(merged),
                            primaryText = merged.name?.takeUnless { it == merged.number } ?: merged.number,
                            secondaryTexts = listOfNotNull(merged.name?.takeUnless { it == merged.number }),
                            phoneNumbers = listOf(merged.number),
                            interactionCount = totalCount,
                            lastInteractionMillis = newest.date,
                        )
                    )
                }
            }

            notes.forEach { note ->
                add(
                    SearchDocument(
                        key = "note:${note.file.absolutePath}",
                        kind = SearchDocumentKind.NOTE,
                        payload = DotDialerSearchPayload.NoteResult(note),
                        primaryText = note.contactName.ifBlank { note.phoneNumber },
                        secondaryTexts = listOf(note.content),
                        phoneNumbers = listOf(note.phoneNumber),
                        lastInteractionMillis = note.lastModified,
                    )
                )
            }
        }

        return LocalSearchIndex.build(documents)
    }

    private fun phoneKey(number: String): String? =
        SearchTextNormalizer.phoneIdentityKey(number)

    private data class Usage(
        val count: Int = 0,
        val lastInteractionMillis: Long = 0L,
    ) {
        fun plus(call: CallLogEntry): Usage = Usage(
            count = count + call.count,
            lastInteractionMillis = maxOf(lastInteractionMillis, call.date),
        )
    }

}

data class DotDialerHomeSections(
    val recents: List<SearchDocument<DotDialerSearchPayload>>,
    val contacts: List<SearchDocument<DotDialerSearchPayload>>,
)

/**
 * Projects the shared local index into the two sections of the streamlined home screen.
 * Matching is still performed once by [LocalSearchIndex]; only presentation order differs:
 * recent numbers are chronological, while the address book is alphabetical.
 */
object DotDialerHomeDirectory {
    private val homeKinds = setOf(
        SearchDocumentKind.RECENT_CALL,
        SearchDocumentKind.CONTACT,
    )

    fun sections(
        index: LocalSearchIndex<DotDialerSearchPayload>,
        query: String,
        foldDiacritics: Boolean,
        includeT9: Boolean = true,
        limit: Int = Int.MAX_VALUE,
        repeatContactsInSearchResults: Boolean = false,
        contactOrder: Map<String, Int> = emptyMap(),
        visibleContactIds: Set<String>? = null,
    ): DotDialerHomeSections {
        val documents = if (query.isBlank()) {
            index.documents(homeKinds)
        } else {
            index.search(
                query,
                SearchOptions(
                    foldDiacritics = foldDiacritics,
                    includeT9 = includeT9,
                    enabledKinds = homeKinds,
                    limit = limit,
                ),
            ).map { it.document }
        }

        val recents = documents
            .filter { it.kind == SearchDocumentKind.RECENT_CALL }
            .sortedWith(
                compareByDescending<SearchDocument<DotDialerSearchPayload>> {
                    it.lastInteractionMillis
                }.thenByDescending { it.interactionCount }
                    .thenBy { it.key },
            )
        val recentContactIds = if (query.isNotBlank() && !repeatContactsInSearchResults) {
            recents.mapNotNullTo(mutableSetOf()) { document ->
                (document.payload as DotDialerSearchPayload.CallResult).call.contactId
            }
        } else {
            emptySet()
        }
        val directoryContacts = documents
                .filter { it.kind == SearchDocumentKind.CONTACT }
                .filter { document ->
                    val contact =
                        (document.payload as DotDialerSearchPayload.ContactResult).contact
                    (visibleContactIds == null || contact.id in visibleContactIds) &&
                        contact.id !in recentContactIds
                }
                .sortedWith(
                    compareBy<SearchDocument<DotDialerSearchPayload>> { document ->
                        val contact = document.payload as DotDialerSearchPayload.ContactResult
                        contactOrder[contact.contact.id] ?: Int.MAX_VALUE
                    }.thenBy {
                        SearchTextNormalizer.normalize(it.primaryText, true)
                    }.thenBy { it.key },
                )

        return DotDialerHomeSections(
            recents = recents,
            contacts = directoryContacts,
        )
    }
}

/**
 * Alphabetical contact projection used by the dialpad.
 *
 * The immutable search index is built once when the provider snapshot changes.  An empty dialpad
 * query returns the complete address book, while a numeric query reuses the same precomputed T9
 * and phone-number fields.  Search relevance decides membership only; presentation order always
 * remains alphabetical so rows do not jump around as digits are entered.
 */
object DotDialerDialpadDirectory {
    private val contactKind = setOf(SearchDocumentKind.CONTACT)

    fun contacts(
        index: LocalSearchIndex<DotDialerSearchPayload>,
        query: String,
        foldDiacritics: Boolean,
        includeT9: Boolean = true,
    ): List<SearchDocument<DotDialerSearchPayload>> {
        val matches = if (query.isBlank()) {
            index.documents(contactKind)
        } else {
            index.search(
                query,
                SearchOptions(
                    foldDiacritics = foldDiacritics,
                    includeT9 = includeT9,
                    enabledKinds = contactKind,
                    limit = Int.MAX_VALUE,
                ),
            ).map { it.document }
        }

        return matches.sortedWith(
            compareBy<SearchDocument<DotDialerSearchPayload>> {
                SearchTextNormalizer.normalize(it.primaryText, foldDiacritics = true)
            }.thenBy { it.key },
        )
    }
}
