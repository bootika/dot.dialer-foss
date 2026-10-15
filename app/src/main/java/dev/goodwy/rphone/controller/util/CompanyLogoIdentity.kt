package dev.goodwy.rphone.controller.util

import android.provider.ContactsContract
import android.content.Context
import java.text.Normalizer
import java.util.Locale

internal data class CompanyLogoIdentity(val company: String?, val websites: List<String>, val emails: List<String>, val companyContact: Boolean)

internal fun normalizedCompanyName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
    .replace(Regex("(?:\\s+(?:srl|sa|llc|ltd|inc|gmbh|limited))+$"), "").trim()

/** Direct website/email evidence only; shared mail and profile hosts do not identify an employer. */
internal object CompanyLogoDomains {
    private val shared = setOf("gmail.com", "googlemail.com", "yahoo.com", "yahoo.ro", "yahoo.co.uk", "ymail.com",
        "outlook.com", "hotmail.com", "live.com", "msn.com", "icloud.com", "me.com", "mac.com", "aol.com",
        "proton.me", "protonmail.com", "mail.com", "gmx.com", "gmx.de", "yandex.com", "yandex.ru",
        "google.com", "facebook.com", "instagram.com", "linkedin.com", "twitter.com", "x.com", "whatsapp.com")
    fun website(value: String): String? {
        val origin=OfficialCompanyLogoSource.publicOrigin(value.trim().let {
            if (it.startsWith("http://")) "https://"+it.removePrefix("http://") else if (it.contains("://")) it else "https://$it"
        }) ?: return null
        val host=java.net.URI(origin).host.removePrefix("www.")
        if (shared.any { host==it || host.endsWith(".$it") }) return null
        return host
    }
    fun email(value: String): String? {
        if (value.count { it=='@' }!=1 || value.any(Char::isWhitespace)) return null
        return website("https://"+value.substringAfter('@').lowercase(Locale.ROOT))
    }
    fun direct(identity: CompanyLogoIdentity, extended: Boolean): List<String> {
        val sites=identity.websites.mapNotNull(::website).distinct()
        return (sites + if(extended) identity.emails.mapNotNull(::email) else emptyList()).distinct()
    }
}

internal class CompanyLogoIdentityReader(private val context: Context, private val contacts: WhatsAppAvatarContactStore) {
    fun read(target: WhatsAppAvatarContactTarget): CompanyLogoIdentity? {
        if (contacts.revalidateTarget(target)==null) return null
        val companies=linkedSetOf<String>(); val sites=linkedSetOf<String>(); val emails=linkedSetOf<String>()
        context.contentResolver.query(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.MIMETYPE,ContactsContract.Data.DATA1),
            ContactsContract.Data.RAW_CONTACT_ID+" = ?",arrayOf(target.rawContactId.toString()),null)?.use { cursor ->
            while(cursor.moveToNext()) {
                val value=cursor.getString(1)?.trim()?.takeIf(String::isNotBlank) ?: continue
                when(cursor.getString(0)) {
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> companies.add(value)
                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> sites.add(value)
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> emails.add(value)
                }
            }
        } ?: return null
        val company=companies.singleOrNull()?.takeIf { it.length in 3..120 && it.none(Char::isISOControl) }
        return CompanyLogoIdentity(company,sites.toList(),emails.toList(),company!=null &&
            normalizedCompanyName(company)==normalizedCompanyName(target.displayName))
    }
}
