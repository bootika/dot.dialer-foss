package dev.goodwy.rphone.recordings

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import dev.goodwy.rphone.controller.util.contactNumberRegion
import dev.goodwy.rphone.controller.util.normalizedContactNumber
import dev.goodwy.rphone.controller.util.PreferenceManager

internal data class RecordingContactIdentity(val number: String,val key: String="",val name: String="")

/** Exact complete number matching only; ambiguous names/numbers never assign a contact. Local metadata only. */
internal fun recordingContactIdentity(context: Context,raw: String): RecordingContactIdentity {
    val region=contactNumberRegion(context,PreferenceManager(context))
    fun canonical(value: String): String {
        if(value.isBlank() || value.length>80 || value.any { !it.isDigit() && it !in "+ ()-.*#" })return ""
        return normalizedContactNumber(value,region) ?: value.filter {it.isDigit() || it in "+*#"}
    }
    val number=canonical(raw)
    if(number.isEmpty())return RecordingContactIdentity("")
    return try {
        val uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(raw))
        val matches=context.contentResolver.query(uri,arrayOf(ContactsContract.PhoneLookup.NUMBER,ContactsContract.PhoneLookup.LOOKUP_KEY,ContactsContract.PhoneLookup.DISPLAY_NAME),null,null,null)?.use {c ->
            buildSet {while(c.moveToNext()) {
                if(canonical(c.getString(0).orEmpty())==number && !c.getString(1).isNullOrBlank() && c.getString(1).length<=512)
                    add(c.getString(1) to c.getString(2).orEmpty().take(160))
            }}
        }.orEmpty()
        val keys=matches.map {it.first}.distinct()
        if(keys.size==1)RecordingContactIdentity(number,keys.single(),matches.first().second)else RecordingContactIdentity(number)
    }catch(_: RuntimeException){RecordingContactIdentity(number)}
}

internal data class RecordingNumberGroup(val number: String,val assets: List<SavedRecording>)
internal data class RecordingContactGroup(val key: String,val title: String,val numbers: List<RecordingNumberGroup>)

/** Identity is never inferred from a display name. Legacy assets remain individually unassigned. */
internal fun groupRecordings(assets: List<SavedRecording>,current: Map<String,RecordingContactIdentity> = emptyMap()): List<RecordingContactGroup> {
    val ordered=assets.sortedWith(compareByDescending<SavedRecording>{it.createdAt}.thenBy{it.id})
    fun identity(asset: SavedRecording)=current[asset.phoneNumber]?.takeIf{it.key.isNotEmpty()} ?: RecordingContactIdentity(asset.phoneNumber,asset.contactKey,asset.contactName)
    return ordered.groupBy {asset ->
        val id=identity(asset)
        when {asset.phoneNumber.isEmpty()->"legacy:${asset.id}";id.key.isNotEmpty()->"contact:${id.key}";else->"number:${asset.phoneNumber}"}
    }.map {(key,items) ->
        val first=items.first();val id=identity(first)
        RecordingContactGroup(key,if(id.key.isNotEmpty())id.name.ifBlank{first.title}else if(first.phoneNumber.isNotEmpty())first.phoneNumber else first.title,
            items.groupBy{it.phoneNumber}.map {(number,list)->RecordingNumberGroup(number,list)})
    }
}
