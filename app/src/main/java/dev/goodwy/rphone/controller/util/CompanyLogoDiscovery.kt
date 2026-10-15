package dev.goodwy.rphone.controller.util

import android.content.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class CompanyLogoCandidate(val bytes: ByteArray, val domain: String, val clearMatch: Boolean,
    val company: String? = null, val requiresDiscovery: Boolean = false)
internal data class CompanyLogoDiscoveryResult(val candidates: List<CompanyLogoCandidate>, val searchUnavailable: Boolean = false)

internal class CompanyLogoDiscovery(context: Context, contacts: WhatsAppAvatarContactStore,
    private val source: CompanyLogoSource, private val names: CompanyNameSearch = HttpCompanyNameSearch(context)) {
    private val identities=CompanyLogoIdentityReader(context,contacts)
    fun identity(target: WhatsAppAvatarContactTarget)=identities.read(target)
    suspend fun find(target: WhatsAppAvatarContactTarget, extended: Boolean): CompanyLogoDiscoveryResult {
        val identity=identities.read(target) ?: return CompanyLogoDiscoveryResult(emptyList())
        val domains=CompanyLogoDomains.direct(identity,extended)
        if(domains.isNotEmpty()) return CompanyLogoDiscoveryResult(load(domains.map { it to (domains.size==1) },identity,
            domains.any { domain -> identity.websites.none { CompanyLogoDomains.website(it)==domain } }))
        if(!extended || identity.company==null) return CompanyLogoDiscoveryResult(emptyList())
        val result=names.search(identity.company,target.accountName)
        if(result.unavailable) return CompanyLogoDiscoveryResult(emptyList(),true)
        val candidates=mutableListOf<CompanyLogoCandidate>()
        for(hit in result.matches) {
            currentCoroutineContext().ensureActive()
            val normalized=normalizedCompanyName(identity.company)
            // Name-only ranked first result is insufficient: unique full name, domain label,
            // and independent official-page branding must agree. Otherwise offer review only.
            val clear=result.matches.size==1 && normalized.length>=5 && normalized==normalizedCompanyName(hit.name) &&
                normalized.replace(" ","")==hit.domain.substringBefore('.').replace("-","") &&
                source.matchesCompany("https://${hit.domain}/",identity.company)
            source.load("https://${hit.domain}/")?.let { candidates+=CompanyLogoCandidate(it,hit.domain,clear,identity.company,true) }
        }
        return CompanyLogoDiscoveryResult(candidates)
    }
    private suspend fun load(domains: List<Pair<String,Boolean>>, identity: CompanyLogoIdentity, extended: Boolean): List<CompanyLogoCandidate> = buildList {
        for((domain,clear) in domains.take(5)) {
            currentCoroutineContext().ensureActive()
            source.load("https://$domain/")?.let { add(CompanyLogoCandidate(it,domain,clear,identity.company,extended)) }
        }
    }
    fun mayUpdate(target: WhatsAppAvatarContactTarget, state: ContactsOptimizerState): Boolean {
        if(target.photo==null) return true
        return identities.read(target)?.companyContact==true && state.logoMatches(target.rawContactId,target.photo)
    }
    fun reviewStillMatches(target: WhatsAppAvatarContactTarget, item: WhatsAppAvatarReviewItem, extended: Boolean): Boolean {
        val domain=item.companyDomain ?: return true // Historical saved-site proposal retains its existing guards.
        if(item.companyDiscovery && !extended) return false
        val current=identities.read(target) ?: return false
        if(current.company!=item.companyName) return false
        val domains=CompanyLogoDomains.direct(current,extended)
        return if(domains.isNotEmpty()) domain in domains else current.company!=null && extended
    }
}
