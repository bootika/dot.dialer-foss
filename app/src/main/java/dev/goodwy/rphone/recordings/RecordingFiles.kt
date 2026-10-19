package dev.goodwy.rphone.recordings

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.storage.StorageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

internal class RecordingFiles(private val context: Context) {
    private val root = File(context.filesDir,"recordings").apply { mkdirs() }
    private val audio = File(root,"audio").apply { mkdirs() }
    private val bridge = File(root,"bridge").apply { mkdirs() }
    fun audio(recording: SavedRecording): File {
        require(UUID.fromString(recording.id).toString() == recording.id && recording.fileName.matches(Regex("${recording.id}\\.(m4a|mp3|wav|ogg|flac|aac|webm|amr)")))
        return File(audio,recording.fileName)
    }
    fun import(uri: Uri, title: String, callKey: String, maxBytes: Long): SavedRecording {
        require(uri.scheme == "content" && maxBytes > 0)
        val label=title.ifBlank {
            context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                if(it.moveToFirst())it.getString(0) else null
            } ?: "Recording"
        }.filterNot {it.isISOControl()}.take(160)
        val id=UUID.randomUUID().toString();var file=File(audio,"$id.m4a");val partial=File(audio,"$id.partial")
        try {
            val storage=context.getSystemService(StorageManager::class.java)
            val available=(storage.getAllocatableBytes(storage.getUuidForPath(audio))-32L*1024*1024).coerceAtLeast(0)
            val limit=minOf(maxBytes,available)
            var size=0L
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> partial.outputStream().use { out ->
                val buffer=ByteArray(64*1024)
                while(true) { val count=input.read(buffer);if(count<0)break;size+=count
                    if(size>limit) throw DictateFailure("INPUT_TOO_LARGE")
                    out.write(buffer,0,count)
                }
            } }
            require(size>0)
            val retriever=MediaMetadataRetriever()
            var mime=""
            val duration=try { retriever.setDataSource(partial.absolutePath)
                require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes")
                mime=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf {it>0} ?: error("Invalid duration")
            } finally { retriever.release() }
            val extension=when(mime) {
                "audio/mp4","audio/x-m4a" -> "m4a"
                "audio/mpeg" -> "mp3"
                "audio/wav","audio/x-wav","audio/vnd.wave" -> "wav"
                "audio/ogg","application/ogg","audio/opus" -> "ogg"
                "audio/flac","audio/x-flac" -> "flac"
                "audio/aac","audio/aac-adts" -> "aac"
                "audio/webm" -> "webm"
                "audio/amr","audio/3gpp" -> "amr"
                else -> throw DictateFailure("UNSUPPORTED_AUDIO")
            }
            file=File(audio,"$id.$extension")
            check(partial.renameTo(file))
            return SavedRecording(id,label.ifBlank {"Recording"},System.currentTimeMillis(),duration,size,file.name,callKey,mime,codec(file))
        } catch(error: Exception) { partial.delete();file.delete();throw error }
    }
    private fun codec(file: File): String {
        val extractor=MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).mapNotNull {extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)}
                .firstOrNull {it.startsWith("audio/")} ?: throw DictateFailure("UNSUPPORTED_AUDIO")
        } finally {extractor.release()}
    }
    fun grantAudio(recording: SavedRecording): Uri = grant(audio(recording))
    fun grantText(job: RecordingJob, text: String): Uri {
        require(text.toByteArray(Charsets.UTF_8).size <= DictateProtocol.RESULT_LIMIT)
        return grant(File(bridge,"${UUID.fromString(job.id)}.txt").apply { writeText(text,Charsets.UTF_8) })
    }
    private fun grant(file: File): Uri = FileProvider.getUriForFile(context,"${context.packageName}.provider",file).also {
        context.grantUriPermission(DictateProtocol.PACKAGE,it,Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    fun revoke(recording: SavedRecording) {
        val file=audio(recording)
        if(file.exists())context.revokeUriPermission(FileProvider.getUriForFile(context,"${context.packageName}.provider",file),Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    fun clearJob(job: RecordingJob) {
        val file=File(bridge,"${UUID.fromString(job.id)}.txt")
        if(file.exists()) {context.revokeUriPermission(FileProvider.getUriForFile(context,"${context.packageName}.provider",file),Intent.FLAG_GRANT_READ_URI_PERMISSION);file.delete()}
    }
    fun readResult(uri: Uri, trusted: () -> Boolean): String {
        require(uri.scheme=="content" && trusted())
        val provider=context.packageManager.resolveContentProvider(uri.authority ?: "",0)
        require(provider?.packageName==DictateProtocol.PACKAGE)
        val bytes=requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            java.io.ByteArrayOutputStream().use { output ->
                val buffer=ByteArray(8192)
                while(true) {val count=input.read(buffer);if(count<0)break
                    require(output.size()+count<=DictateProtocol.RESULT_LIMIT);output.write(buffer,0,count)}
                output.toByteArray()
            }
        }
        require(bytes.size<=DictateProtocol.RESULT_LIMIT && trusted())
        return bytes.toString(Charsets.UTF_8)
    }
}
