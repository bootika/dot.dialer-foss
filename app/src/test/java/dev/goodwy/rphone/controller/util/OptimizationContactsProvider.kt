package dev.goodwy.rphone.controller.util

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

internal class OptimizationContactsProvider(private val directory: File) : ContentProvider() {
    data class Raw(val id: Long, val contact: Long, val account: String, val type: String = "com.google")
    data class Phone(val id: Long, var raw: Long, var number: String, val type: Int = 2, val label: String = "Mobile")
    val names = linkedMapOf(10L to "Test Alexandru Butica")
    val raws = mutableListOf(Raw(20, 10, "first@example.test"))
    val phones = mutableListOf(Phone(30, 20, "0720 033 833"))
    var nameQueryIgnoresCase = false
    var photoReadTransform: ((ByteArray) -> ByteArray)? = null
    var writes = 0
    var badReadback: ByteArray? = null
    var pendingPhotoReads = 0
    var afterWrittenPhotoRead: (() -> Unit)? = null
    var beforePhotoOpen: (() -> Unit)? = null
    var photoLookupUnavailable = false
    var maximumPhotoDimension: Int? = 1024
    var selectedPhotoRaw: Long? = null
    val pictures = mutableMapOf<Long, ByteArray>()
    val outputs = mutableMapOf<Long, File>()
    val companies = mutableMapOf<Long, Pair<String, String?>>()
    val emails = mutableMapOf(20L to listOf("contact@example.test"))
    val extraWebsites = mutableMapOf<Long,List<String>>()
    fun install(context: Context) {
        attachInfo(context, ProviderInfo().apply { authority = ContactsContract.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, this)
    }
    override fun onCreate() = true
    override fun getType(uri: Uri): String = ContactsContract.Contacts.CONTENT_ITEM_TYPE
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("No new contacts may be created")
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = error("No contacts may be deleted")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return 0
        val row = phones.singleOrNull { it.id == id } ?: return 0
        if (args?.size != 3 || row.raw.toString() != args[0] || args[1] != ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ||
            row.number != args[2]) return 0
        check(values!!.size() == 1)
        row.number = values.getAsString(ContactsContract.CommonDataKinds.Phone.NUMBER)
        writes++; return 1
    }
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        beforePhotoOpen?.invoke()
        check(uri.pathSegments.first() == "raw_contacts" && uri.lastPathSegment == "display_photo")
        val raw = uri.pathSegments[1].toLong()
        check(raws.any { it.id == raw && it.type == "com.google" })
        val file = File(directory, "photo-" + raw + ".png")
        outputs[raw] = file; writes++
        return AssetFileDescriptor(ParcelFileDescriptor.open(file,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE), 0,
            AssetFileDescriptor.UNKNOWN_LENGTH)
    }
    fun photo(raw: Long): ByteArray? {
        if (outputs.containsKey(raw) && pendingPhotoReads > 0) {
            pendingPhotoReads--
            return pictures[raw]
        }
        val bytes = if (outputs.containsKey(raw) && badReadback != null) badReadback
            else outputs[raw]?.takeIf { it.length() > 0 }?.readBytes() ?: pictures[raw]
        return bytes?.let { photoReadTransform?.invoke(it) ?: it }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor? {
        if (uri == ContactsContract.DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI) {
            val maximum = maximumPhotoDimension ?: return null
            return MatrixCursor(arrayOf(ContactsContract.DisplayPhoto.DISPLAY_MAX_DIM)).apply { addRow(arrayOf(maximum)) }
        }
        if (photoLookupUnavailable && args?.lastOrNull() == ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE) return null
        val columns = projection!!.toList()
        val rows = mutableListOf<Map<String, Any?>>()
        when {
            uri.pathSegments.take(2) == listOf("data", "phones") -> {
                for (phone in phones) {
                    val raw = raws.firstOrNull { it.id == phone.raw } ?: continue
                    rows.add(mapOf("contact_id" to raw.contact, "data1" to phone.number))
                }
            }
            uri.pathSegments.first() == "contacts" -> {
                for ((id, name) in names) {
                    if (uri.pathSegments.size > 1 && uri.lastPathSegment != id.toString()) continue
                    if (selection?.contains("display_name") == true && !name.equals(args?.firstOrNull(), ignoreCase = nameQueryIgnoresCase)) continue
                    rows.add(mapOf("_id" to id, "display_name" to name,
                        "photo_id" to selectedPhotoRaw?.let { 1000L }))
                }
            }
            uri.pathSegments.first() == "raw_contacts" -> {
                for (raw in raws) {
                    if (args?.size == 3 && (raw.contact.toString() != args[0] || raw.type != args[1] || raw.account != args[2])) continue
                    if (args?.size == 2 && (raw.type != args[0] || raw.account != args[1])) continue
                    rows.add(mapOf("_id" to raw.id, "contact_id" to raw.contact, "account_name" to raw.account,
                        "account_type" to raw.type, "deleted" to 0))
                }
            }
            uri.pathSegments == listOf("data", "1000") -> {
                selectedPhotoRaw?.let { rows.add(mapOf("raw_contact_id" to it)) }
            }
            uri.pathSegments.first() == "data" -> {
                val raw = args?.firstOrNull()?.toLongOrNull()
                val requestedMime = args?.getOrNull(1)
                val excludedPhoto = selection?.contains("!= ?") == true
                if (selection?.contains("LOWER(") == true && args?.firstOrNull() == ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE) {
                    raws.forEach { row -> emails[row.id].orEmpty().filter { it==args.getOrNull(1) }.forEach { email ->
                        rows.add(mapOf("contact_id" to row.contact, "data1" to email))
                    }
                    }
                }
                for (phone in phones) {
                    if (raw != null && raw != phone.raw) continue
                    if (uri.pathSegments.size > 1 && uri.lastPathSegment != phone.id.toString()) continue
                    if (!excludedPhoto && requestedMime != null && requestedMime != ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) continue
                    rows.add(mapOf("_id" to phone.id, "raw_contact_id" to phone.raw, "data1" to phone.number,
                        "mimetype" to ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                        "data2" to phone.type, "data3" to phone.label))
                }
                if (!excludedPhoto && requestedMime == ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE && raw != null) {
                    val bytes = photo(raw)
                    if (outputs.containsKey(raw)) afterWrittenPhotoRead?.invoke()
                    bytes?.let { rows.add(mapOf("_id" to 40L, "data14" to null, "data15" to it)) }
                }
                if (requestedMime == ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE || requestedMime==null || excludedPhoto) {
                    for((id,addresses) in emails) if(raw==null || raw==id) addresses.forEachIndexed { index,email ->
                        rows.add(mapOf("_id" to 200L+index,"raw_contact_id" to id,"data1" to email,
                            "mimetype" to ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE))
                    }
                }
                if (requestedMime == null || excludedPhoto) for ((id, company) in companies) {
                    if (raw != null && raw != id) continue
                    rows.add(mapOf("_id" to 100L, "raw_contact_id" to id, "mimetype" to
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE, "data1" to company.first))
                    company.second?.let { rows.add(mapOf("_id" to 101L, "raw_contact_id" to id,
                        "mimetype" to ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE, "data1" to it)) }
                }
                if (requestedMime==null || excludedPhoto) for((id,sites) in extraWebsites) if(raw==null || raw==id)
                    sites.forEachIndexed { index,site -> rows.add(mapOf("_id" to 300L+index,"raw_contact_id" to id,"data1" to site,
                        "mimetype" to ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)) }
            }
        }
        return MatrixCursor(columns.toTypedArray()).apply {
            rows.forEach { row -> addRow(columns.map { row[it] }.toTypedArray()) }
        }
    }
}
