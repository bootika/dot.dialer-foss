package dev.goodwy.rphone.controller.util

import android.content.Context
import android.util.AtomicFile
import androidx.core.net.toUri
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Local photo association only: no name inference, contact insertion, or CallLog mutation. */
internal class UnsavedNumberAvatars(context: Context, account: String, private val now: () -> Long = System::currentTimeMillis) {
    private val directory = File(context.noBackupFilesDir,
        "contact-optimization/" + ContactOptimizationProfiles.accountKey(account) + "/unsaved-avatars").apply { mkdirs() }
    private val index = AtomicFile(File(directory,"index.json"))
    private val lock = locks.computeIfAbsent(directory.absolutePath) { Any() }
    private fun read() = if (!index.baseFile.exists()) JSONObject() else {
        check(index.baseFile.length() <= 256*1024)
        JSONObject(index.openRead().bufferedReader().use { it.readText() })
    }
    private fun save(json: JSONObject) {
        val output=index.startWrite()
        try { output.write(json.toString().encodeToByteArray()); index.finishWrite(output) }
        catch (error: Exception) { index.failWrite(output); throw error }
    }
    fun store(phone: String, bytes: ByteArray, policy: ContactPhotoAutomaticPolicy, manual: Boolean = false, valid: () -> Boolean): Boolean = synchronized(lock) {
        val key=phoneKey(phone) ?: return@synchronized false
        val info=bytes.toContactPhotoImageInfo() ?: return@synchronized false
        if (bytes.size > 8*1024*1024 || !valid()) return@synchronized false
        val json=read(); val prior=json.optJSONObject(key)
        val previous=prior?.let(::imageFile)?.readBytes()
        if (!manual && ((prior?.optBoolean("manual")==true && previous!=null) || !optimizerPhotoChanged(previous,bytes,1024) ||
                !optimizerAccepts(policy,previous?.toContactPhotoImageInfo(),info,1024))) return@synchronized false
        val name=key+"-"+ContactsOptimizerState.digest(bytes)+".png"
        val image=File(directory,name)
        if (!image.exists()) image.writeBytes(bytes)
        if (!valid()) return@synchronized false
        check(image.setLastModified(now()))
        json.put(key,JSONObject().put("file",name).put("at",now()).put("bytes",bytes.size).put("manual",manual).put("source",1).put("cut_corners",notificationAvatarHasCutCorners(bytes)))
        val entries=json.keys().asSequence().map { it to json.getJSONObject(it) }.sortedByDescending { it.second.optLong("at") }.toList()
        var total=0L
        entries.forEachIndexed { position,(id,item) ->
            total+=item.optLong("bytes")
            if (position>=200 || total>32L*1024*1024) json.remove(id)
        }
        save(json)
        val keep=json.keys().asSequence().mapNotNull { json.optJSONObject(it)?.optString("file") }.toSet()
        directory.listFiles()?.filter { imageName.matches(it.name) && it.name !in keep }?.forEach { it.delete() }
        true
    }
    fun resolve(phone: String): String? = synchronized(lock) {
        val key=phoneKey(phone) ?: return@synchronized null
        val item=read().optJSONObject(key) ?: return@synchronized null
        imageFile(item)?.takeIf { it.name.startsWith(key+"-") }?.let { android.net.Uri.fromFile(it).toString() }
    }
    private fun imageFile(item: JSONObject): File? {
        val name=item.optString("file")
        if (!imageName.matches(name)) return null
        return File(directory,name).takeIf { it.isFile && it.length() in 1..8L*1024*1024 }
    }
    fun prune() = synchronized(lock) {
        val json=read()
        val expired=json.keys().asSequence().filter { imageFile(json.getJSONObject(it)) == null }.toList()
        expired.forEach(json::remove)
        if (expired.isNotEmpty()) save(json)
        val keep=json.keys().asSequence().mapNotNull { json.optJSONObject(it)?.optString("file") }.toSet()
        directory.listFiles()?.filter { imageName.matches(it.name) && it.name !in keep }?.forEach { it.delete() }
    }
    companion object {
        const val RETENTION=30L*24*60*60*1000
        private val locks=ConcurrentHashMap<String,Any>()
        private val imageName=Regex("[a-f0-9]{64}-[a-f0-9]{64}\\.png")
        fun pruneExpired(context: Context) {
            File(context.noBackupFilesDir,"contact-optimization").listFiles()?.filter { it.isDirectory && it.name.matches(Regex("[a-f0-9]{64}")) }
                ?.forEach { account ->
                    val folder=File(account,"unsaved-avatars")
                    if (folder.isDirectory) {
                        // Keep assigned avatars after trial expiry. Only unreferenced feature files are garbage.
                        synchronized(locks.computeIfAbsent(folder.absolutePath) {Any()}) {
                            val keep=runCatching {
                                val index=AtomicFile(File(folder,"index.json"))
                                val json=JSONObject(index.openRead().bufferedReader().use {it.readText()})
                                json.keys().asSequence().mapNotNull {json.optJSONObject(it)?.optString("file")}.toSet()
                            }.getOrNull()
                            if(keep!=null) folder.listFiles()?.filter {imageName.matches(it.name) && it.name !in keep}?.forEach {it.delete()}
                        }
                    }
                }
        }
        private fun phoneKey(phone: String) = phone.takeIf { it.matches(Regex("\\+[1-9]\\d{7,14}")) }
            ?.let { ContactsOptimizerState.digest(it.encodeToByteArray()) }
        fun reader(context: Context, prefs: PreferenceManager): (String) -> String? {
            val results=mutableMapOf<String,String?>()
            val account=prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null)
            val profiles=ContactOptimizationProfiles(context)
            val settings=profiles.settings(account)
            val allowed=account!=null && settings.enabled && settings.disclosed && ContactOptimizationAccess.granted(context)
            val store=if (allowed) UnsavedNumberAvatars(context,requireNotNull(account)) else null
            return { number ->
                if (results.containsKey(number)) results[number] else {
                    val result=if (store==null || prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null)!=account) null
                        else resolve(context,number,prefs,store,profiles)
                    results[number]=result; result
                }
            }
        }
        fun resolve(context: Context, number: String, prefs: PreferenceManager = PreferenceManager(context),
            cachedStore: UnsavedNumberAvatars? = null, cachedProfiles: ContactOptimizationProfiles? = null): String? = runCatching {
            val account=prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null) ?: return@runCatching null
            val settings=(cachedProfiles ?: ContactOptimizationProfiles(context)).settings(account)
            if (!settings.enabled || !settings.disclosed || !ContactOptimizationAccess.granted(context)) return@runCatching null
            val phone=normalizedContactNumber(number,contactNumberRegion(context,prefs)) ?: return@runCatching null
            val uri=android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,android.net.Uri.encode(phone))
            val count=context.contentResolver.query(uri,arrayOf(android.provider.ContactsContract.PhoneLookup._ID),null,null,null)?.use { it.count }
            if (count!=0) return@runCatching null
            val result=(cachedStore ?: UnsavedNumberAvatars(context,account)).resolve(phone)
            result.takeIf { prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null)==account }
        }.onFailure { android.util.Log.w("ContactsOptimizer","Unsaved avatar unavailable: "+it.javaClass.simpleName) }.getOrNull()
        fun ownedUri(context: Context, value: String?) = value?.let { valueUri ->
            runCatching {
                val uri=valueUri.toUri();val prefix=File(context.noBackupFilesDir,"contact-optimization").absolutePath.replace('\\','/')+"/"
                val path=uri.path?.let { File(it).absoluteFile.normalize().invariantSeparatorsPath } ?: return@runCatching false
                uri.scheme=="file" && path.startsWith(prefix) &&
                    path.removePrefix(prefix).matches(Regex("[a-f0-9]{64}/unsaved-avatars/[a-f0-9]{64}-[a-f0-9]{64}\\.png"))
            }.getOrDefault(false)
        } == true
    }
}
