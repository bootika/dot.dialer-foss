package dev.goodwy.rphone.controller.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.goodwy.rphone.modal.data.Contact
import java.io.File

fun shareContactsAsVCard(
    context: Context,
    contacts: List<Contact>,
    chooserTitle: String? = null,
) {
    if (contacts.isEmpty()) return
    val shareDirectory = File(context.cacheDir, "shared_contacts").apply { mkdirs() }
    shareDirectory.listFiles()?.forEach { old ->
        if (System.currentTimeMillis() - old.lastModified() > VCARD_CACHE_MAX_AGE_MS) old.delete()
    }
    val baseName = contacts.singleOrNull()?.displayName
        ?.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
        ?.trim('_')
        ?.take(48)
        ?.ifBlank { null }
        ?: "contacts"
    val file = File(shareDirectory, "${baseName}_${System.currentTimeMillis()}.vcf")
    file.writeText(contacts.joinToString(separator = "") { it.toVCard() }, Charsets.UTF_8)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/x-vcard"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun Contact.toVCard(): String = buildString {
    append("BEGIN:VCARD\r\n")
    append("VERSION:3.0\r\n")
    append("N:")
    append(familyName.vCardEscape()).append(';')
    append(givenName.vCardEscape()).append(';')
    append(middleName.vCardEscape()).append(';')
    append(namePrefix.vCardEscape()).append(';')
    append(nameSuffix.vCardEscape()).append("\r\n")
    append("FN:").append(displayName.vCardEscape()).append("\r\n")
    nickname.takeIf(String::isNotBlank)?.let { append("NICKNAME:").append(it.vCardEscape()).append("\r\n") }
    company.takeIf(String::isNotBlank)?.let { append("ORG:").append(it.vCardEscape()).append("\r\n") }
    jobTitle.takeIf(String::isNotBlank)?.let { append("TITLE:").append(it.vCardEscape()).append("\r\n") }
    phoneNumbers.distinct().filter(String::isNotBlank).forEach {
        append("TEL;TYPE=CELL:").append(it.vCardEscape()).append("\r\n")
    }
    emails.map { it.value }.distinct().filter(String::isNotBlank).forEach {
        append("EMAIL:").append(it.vCardEscape()).append("\r\n")
    }
    addresses.map { it.formattedAddress }.distinct().filter(String::isNotBlank).forEach {
        append("ADR:;;").append(it.vCardEscape()).append(";;;;\r\n")
    }
    notes?.takeIf(String::isNotBlank)?.let { append("NOTE:").append(it.vCardEscape()).append("\r\n") }
    append("END:VCARD\r\n")
}

private fun String.vCardEscape(): String = replace("\\", "\\\\")
    .replace("\r\n", "\\n")
    .replace("\n", "\\n")
    .replace(";", "\\;")
    .replace(",", "\\,")

private const val VCARD_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1000L
