package dev.goodwy.rphone.recordings

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/** Private crash-recoverable PCM journal. No audio or call identity leaves the app for capture. */
internal class CallCaptureStore(private val repo: RecordingsRepository) {
    private val root=File(repo.context.filesDir,"recordings/capture").apply {mkdirs()}
    private val manifest=AtomicFile(File(root,"pending.json"))
    fun begin(title: String,key: String,transcribe: Boolean,language: String,identity: RecordingContactIdentity=RecordingContactIdentity("")): Pair<SavedRecording,File> {
        check(!manifest.baseFile.exists())
        val id=UUID.randomUUID().toString()
        val data=JSONObject().put("id",id).put("title",title.take(160)).put("callKey",key)
            .put("createdAt",System.currentTimeMillis()).put("transcribe",transcribe).put("language",language)
            .put("phoneNumber",identity.number).put("contactKey",identity.key).put("contactName",identity.name)
        val stream=manifest.startWrite()
        try {stream.write(data.toString().toByteArray());manifest.finishWrite(stream)}catch(e: Exception){manifest.failWrite(stream);throw e}
        val asset=asset(data,0)
        val file=repo.files.audio(asset)
        RandomAccessFile(file,"rw").use {writeHeader(it,0);it.fd.sync()}
        return asset to file
    }
    suspend fun recover() {
        if(!manifest.baseFile.exists())return
        val data=manifest.openRead().use {require(it.available()<8192);JSONObject(it.readBytes().toString(Charsets.UTF_8))}
        val initial=asset(data,0)
        val file=repo.files.audio(initial)
        val size=((file.length()-44).coerceAtLeast(0)/4*4).coerceAtMost(MAX_BYTES)
        if(size==0L){file.delete();manifest.delete();return}
        RandomAccessFile(file,"rw").use {it.setLength(size+44);writeHeader(it,size);it.fd.sync()}
        if(repo.catalog.recording(initial.id)==null)repo.catalog.add(asset(data,size))
        val source=AtomicFile(File(root,"${initial.id}.json"))
        val out=source.startWrite()
        try {out.write("{\"version\":1,\"channels\":\"uplink_downlink\"}".toByteArray());source.finishWrite(out)}
        catch(e: Exception){source.failWrite(out);throw e}
        repo.changed()
        // Queue is idempotent. Keep the journal on a provider failure for retry at next entry.
        if(data.getBoolean("transcribe") && repo.catalog.jobs().none {it.recordingId==initial.id && it.operation=="transcribe"}) {
            if(!repo.moduleEnabled())return
            // Persist explicit processing intent without an online check; sync validates before sending.
            repo.catalog.enqueue(initial.id,"transcribe","@capture:${data.getString("language")}")
            RecordingSyncJob.schedule(repo.context)
            repo.changed()
        }
        manifest.delete()
    }
    private fun asset(data: JSONObject,bytes: Long): SavedRecording {
        val id=data.getString("id");require(UUID.fromString(id).toString()==id)
        return SavedRecording(id,data.getString("title"),data.getLong("createdAt"),bytes*1000/64000,bytes+44,
            "$id.wav",data.getString("callKey"),"audio/wav","audio/raw",data.optString("phoneNumber"),data.optString("contactKey"),data.optString("contactName"))
    }
    companion object {
        // RIFF has a 32-bit length. No per-call timer; leave space for its 36-byte header.
        const val MAX_BYTES=4294967256L
        const val STORAGE_RESERVE=64L*1024*1024
        fun requireStorage(available: Long){check(available>STORAGE_RESERVE){"Recording storage low"}}
        fun writeHeader(file: RandomAccessFile,bytes: Long) {
            require(bytes in 0..MAX_BYTES && bytes%4==0L)
            fun le16(n: Int){file.write(n);file.write(n ushr 8)}
            fun le32(n: Long){le16(n.toInt());le16((n ushr 16).toInt())}
            file.seek(0);file.writeBytes("RIFF");le32(bytes+36);file.writeBytes("WAVEfmt ");le32(16)
            le16(1);le16(2);le32(16000);le32(64000);le16(4);le16(16);file.writeBytes("data");le32(bytes)
        }
    }
}
