package dev.goodwy.rphone.controller.util

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class WhatsAppAvatarReviewStore(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val profiles = ContactOptimizationProfiles(context)
    val storageFailed=profiles.storageFailed
    private val lock = Any()
    private var account: String? = null
    private val mutableItems = MutableStateFlow<List<WhatsAppAvatarReviewItem>>(emptyList())
    val items = mutableItems.asStateFlow()
    private val allMutable = MutableStateFlow<List<WhatsAppAvatarReviewItem>>(emptyList())
    val allItems = allMutable.asStateFlow()
    private val sourceMutable = MutableStateFlow<String?>(null)
    val selectedSource = sourceMutable.asStateFlow()
    private val sourcesMutable = MutableStateFlow<List<String>>(emptyList())
    val availableSources = sourcesMutable.asStateFlow()
    private var requestedSource: String? = null
    private var batchAccount: String? = null
    private var batchRows = emptyList<WhatsAppAvatarReviewItem>()
    private var batchChanges = 0
    private val delayedDeletes = mutableListOf<WhatsAppAvatarReviewItem>()
    fun selectSource(value: String, source: String) = synchronized(lock) {
        require(source in ContactReviewSources.keys)
        val file=AtomicFile(File(profiles.directory(value), "review-source.json"))
        val output=file.startWrite()
        try { output.write(JSONObject().put("source",source).toString().encodeToByteArray());file.finishWrite(output) }
        catch(error:Exception) { file.failWrite(output);profiles.reportStorageFailure();throw error }
        if(account==value) { requestedSource=source;publish(readQueue(value)) }
    }
    private fun publish(rows: List<WhatsAppAvatarReviewItem>) {
        val choices=rows.contactPhotoProposals().mapNotNull(ContactReviewSources::of).distinct()
        val active=requestedSource ?: choices.lastOrNull()
        requestedSource=active;sourceMutable.value=active
        sourcesMutable.value=(listOfNotNull(active)+choices).distinct()
        allMutable.value=rows
        mutableItems.value=rows.filter { ContactReviewSources.of(it)==active }
    }
    fun beginSearch(value: String, source: String) = synchronized(lock) {
        val old=readQueue(value);val next=old.filterNot { ContactReviewSources.of(it)==source }
        persist(value,next);old.filter { ContactReviewSources.of(it)==source }.forEach(::deleteFiles)
        selectSource(value,source);beginBatch(value)
    }
    fun beginBatch(value: String) = synchronized(lock) {
        check(batchAccount==null);batchRows=readFileQueue(value);batchAccount=value;batchChanges=0
    }
    fun finishBatch(value: String): Boolean = synchronized(lock) {
        if(batchAccount!=value) return@synchronized true
        val success=runCatching { flushBatch() }.onFailure { profiles.reportStorageFailure() }.isSuccess
        batchAccount=null;batchRows=emptyList();batchChanges=0;delayedDeletes.clear()
        if(account==value) runCatching { publish(readFileQueue(value)) }.onFailure { profiles.reportStorageFailure() }
        success
    }
    private fun flushBatch() {
        val value=batchAccount ?: return
        persist(value,batchRows);batchChanges=0
        delayedDeletes.forEach(::deleteFilesNow);delayedDeletes.clear()
    }
    private fun saveRows(value: String, rows: List<WhatsAppAvatarReviewItem>) {
        if(batchAccount==value) { batchRows=rows;batchChanges++;if(batchChanges>=32)flushBatch() }
        else persist(value,rows)
    }
    fun isAvailablePhoto(item: WhatsAppAvatarReviewItem): Boolean {
        val file=candidatePreviewFile(item) ?: return false
        val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        runCatching { file.inputStream().use { android.graphics.BitmapFactory.decodeStream(it,null,options) } }
            .getOrElse { return false }
        return options.outWidth>0 && options.outHeight>0
    }
    fun selectAccount(value: String?) = synchronized(lock) {
        account = value
        requestedSource=null;publish(emptyList())
        requestedSource=value?.let { name -> runCatching {
            val source=JSONObject(AtomicFile(File(profiles.directory(name),"review-source.json")).openRead().bufferedReader().use { it.readText() }).optString("source")
            source.takeIf { it in ContactReviewSources.keys }
        }.getOrNull() }
        if(value==null)publish(emptyList()) else runCatching { publish(readQueue(value)) }.onFailure { profiles.reportStorageFailure() }
    }
    fun forAccount(value: String): List<WhatsAppAvatarReviewItem> = synchronized(lock) { readQueue(value) }
    fun candidateBytes(item: WhatsAppAvatarReviewItem) = readBytes(item, item.candidateFileName)
    fun previousBytes(item: WhatsAppAvatarReviewItem) = item.previousFileName?.let { readBytes(item, it) }
    // Queue photo filenames are immutable UUIDs; File equality keeps Coil requests stable across progress updates.
    fun candidatePreviewFile(item: WhatsAppAvatarReviewItem) = previewFile(item, item.candidateFileName)
    fun previousPreviewFile(item: WhatsAppAvatarReviewItem) = item.previousFileName?.let { previewFile(item, it) }
    private fun previewFile(item: WhatsAppAvatarReviewItem, name: String): File? = runCatching {
        safeFile(item.accountName, name)?.takeIf { it.isFile && it.canRead() && it.length() in 1..8L * 1024 * 1024 }
    }.getOrNull()
    private fun readBytes(item: WhatsAppAvatarReviewItem, name: String): ByteArray? = runCatching {
        safeFile(item.accountName, name)?.takeIf { it.isFile && it.length() <= 8 * 1024 * 1024 }?.readBytes()
    }.getOrNull()

    fun enqueue(item: WhatsAppAvatarReviewItem, candidate: ByteArray?, previous: ByteArray?) = synchronized(lock) {
        require(item.kind != "photo" || (candidate != null && candidate.size <= 8 * 1024 * 1024 &&
            candidate.toContactPhotoImageInfo() != null))
        val old = readQueue(item.accountName)
        if (old.any { it.contactId == item.contactId && it.matchedNumber == item.matchedNumber &&
                it.displayName == item.displayName && it.kind == item.kind && it.source == item.source &&
                it.proposedNumber == item.proposedNumber && it.candidateWidth >= item.candidateWidth &&
                sameNullablePhoto(candidateBytes(it), candidate) &&
                it.companyDomain==item.companyDomain && sameNullablePhoto(previousBytes(it), previous) }) return@synchronized
        if (candidate != null) safeFile(item.accountName, item.candidateFileName)?.writeBytes(candidate) ?: error("Invalid image name")
        if (previous != null) item.previousFileName?.let { safeFile(item.accountName, it)?.writeBytes(previous) }
        val next = (old.filterNot { it.contactId != 0L && it.rawContactId == item.rawContactId &&
            it.source == item.source && it.kind == item.kind && it.phoneDataId == item.phoneDataId &&
            (item.source!="Company logo" || it.companyDomain==item.companyDomain) } + item)
        saveRows(item.accountName, next)
        val retainedIds=next.map { it.id }.toHashSet()
        old.filter { it.id !in retainedIds }.forEach(::deleteFiles)
        if (account == item.accountName) publish(next)
    }
    fun remove(id: String, accountName: String? = account) = synchronized(lock) {
        accountName ?: return@synchronized
        val old = readQueue(accountName)
        saveRows(accountName, old.filterNot { it.id == id })
        old.filter { it.id == id }.forEach(::deleteFiles)
        if (account == accountName) publish(old.filterNot { it.id == id })
    }
    fun clear(accountName: String) = synchronized(lock) {
        val old = readQueue(accountName)
        saveRows(accountName, emptyList())
        old.forEach(::deleteFiles)
        AtomicFile(File(profiles.directory(accountName),"review-source.json")).delete()
        if (account == accountName) { requestedSource=null;publish(emptyList()) }
    }
    private fun deleteFiles(item: WhatsAppAvatarReviewItem) {
        if(batchAccount==item.accountName) delayedDeletes.add(item) else deleteFilesNow(item)
    }
    private fun deleteFilesNow(item: WhatsAppAvatarReviewItem) {
        safeFile(item.accountName, item.candidateFileName)?.delete()
        item.previousFileName?.let { safeFile(item.accountName, it)?.delete() }
    }
    private fun safeFile(account: String, name: String): File? {
        if (!Regex("[a-f0-9-]{36}-(candidate|previous)\\.png").matches(name)) return null
        return File(profiles.directory(account), name)
    }
    private fun readQueue(accountName: String): List<WhatsAppAvatarReviewItem> =
        if(batchAccount==accountName) batchRows else readFileQueue(accountName)
    private fun readFileQueue(accountName: String): List<WhatsAppAvatarReviewItem> {
        val directory = profiles.directory(accountName)
        val file = AtomicFile(File(directory, "review-queue.json"))
        val array = if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) JSONArray() else try {
            check(maxOf(file.baseFile.length(),File(file.baseFile.path+".bak").length())<=MAX_METADATA_BYTES)
            JSONArray(file.openRead().bufferedReader().use { it.readText() })
        } catch(error:Exception) { profiles.reportStorageFailure();throw error }
        val next = (0 until array.length()).mapNotNull { i -> runCatching {
            val j = array.getJSONObject(i)
            WhatsAppAvatarReviewItem(
                j.getString("id"), j.getLong("contactId"), j.getLong("rawContactId"),
                j.optLong("phoneDataId").takeIf { it > 0 }, j.getString("displayName"),
                j.optString("matchedNumber").takeIf { it.isNotBlank() }, accountName,
                j.getString("candidateFileName"), j.optString("previousFileName").takeIf { it.isNotBlank() },
                j.getInt("candidateWidth"), j.getInt("candidateHeight"),
                j.optInt("previousWidth").takeIf { it > 0 }, j.optInt("previousHeight").takeIf { it > 0 },
                j.getString("sourcePackage"), WhatsAppAvatarMatchStrength.valueOf(j.getString("matchStrength")),
                j.getLong("createdAtMillis"), j.optInt("originalWidth", j.getInt("candidateWidth")),
                j.optInt("originalHeight", j.getInt("candidateHeight")), j.optString("source", "WhatsApp"),
                j.optString("kind", "photo"), j.optString("originalNumber").takeIf(String::isNotBlank),
                j.optString("proposedNumber").takeIf(String::isNotBlank),
                j.optString("displayNumber").takeIf(String::isNotBlank),j.optString("companyDomain").takeIf(String::isNotBlank),
                j.optString("companyName").takeIf(String::isNotBlank),j.optBoolean("companyDiscovery"))
        }.getOrNull() }.filter {
            it.createdAtMillis in (now() - RETENTION_MILLIS)..(now() + 60_000) &&
                (it.kind != "photo" || isAvailablePhoto(it))
        }
        val retained = next.flatMap { listOfNotNull(it.candidateFileName, it.previousFileName) }.toSet()
        directory.listFiles()?.filter { safeFile(accountName,it.name)!=null && it.name !in retained }?.forEach { it.delete() }
        // Remove expired/corrupt records; do not leave an apparent actionable queue.
        if (next.size != array.length()) runCatching { persist(accountName, next) }
        return next
    }
    private fun persist(accountName: String, items: List<WhatsAppAvatarReviewItem>) {
        val array = JSONArray()
        for (i in items) array.put(JSONObject().put("id", i.id).put("contactId", i.contactId)
            .put("rawContactId", i.rawContactId).put("phoneDataId", i.phoneDataId ?: 0)
            .put("displayName", i.displayName).put("matchedNumber", i.matchedNumber ?: "")
            .put("candidateFileName", i.candidateFileName).put("previousFileName", i.previousFileName ?: "")
            .put("candidateWidth", i.candidateWidth).put("candidateHeight", i.candidateHeight)
            .put("previousWidth", i.previousWidth ?: 0).put("previousHeight", i.previousHeight ?: 0)
            .put("sourcePackage", i.sourcePackage).put("matchStrength", i.matchStrength.name)
            .put("createdAtMillis", i.createdAtMillis).put("originalWidth", i.originalWidth)
            .put("originalHeight", i.originalHeight).put("source", i.source).put("kind", i.kind)
            .put("originalNumber", i.originalNumber ?: "").put("proposedNumber", i.proposedNumber ?: "")
            .put("displayNumber", i.displayNumber ?: "").put("companyDomain",i.companyDomain ?: "")
            .put("companyName",i.companyName ?: "").put("companyDiscovery",i.companyDiscovery))
        val file = AtomicFile(File(profiles.directory(accountName), "review-queue.json"))
        val bytes=array.toString().encodeToByteArray()
        check(bytes.size<=MAX_METADATA_BYTES) { "Review metadata storage limit" }
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    companion object {
        const val MAX_METADATA_BYTES=32L*1024*1024
        const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
        fun newFileName(id: String, kind: String) = id + "-" + kind + ".png"
    }
}
