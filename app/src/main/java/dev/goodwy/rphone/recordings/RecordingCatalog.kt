package dev.goodwy.rphone.recordings

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import java.util.UUID

internal data class SavedRecording(val id: String, val title: String, val createdAt: Long,
    val durationMs: Long, val bytes: Long, val fileName: String, val callKey: String, val mimeType: String, val codecMime: String = "")
internal data class SavedTranscript(val id: Long, val recordingId: String, val operation: String,
    val language: String, val text: String, val jobId: String)
internal data class RecordingJob(val id: String, val recordingId: String, val operation: String,
    val language: String, val sourceTextId: Long, val state: String, val accepted: Boolean,
    val completedParts: Int, val totalParts: Int, val error: String, val automatic: Boolean)

/** Separate credential-protected catalogue; no migration or mutation of the contact database. */
internal class RecordingCatalog(context: Context) : SQLiteOpenHelper(context, "recordings.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE recordings(id TEXT PRIMARY KEY,title TEXT NOT NULL,createdAt INTEGER NOT NULL,durationMs INTEGER NOT NULL,bytes INTEGER NOT NULL,fileName TEXT NOT NULL,callKey TEXT NOT NULL,mimeType TEXT NOT NULL,codecMime TEXT NOT NULL)")
        db.execSQL("CREATE TABLE recording_texts(id INTEGER PRIMARY KEY AUTOINCREMENT,recordingId TEXT NOT NULL,operation TEXT NOT NULL,language TEXT NOT NULL,text TEXT NOT NULL,jobId TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE recording_jobs(id TEXT PRIMARY KEY,recordingId TEXT NOT NULL,operation TEXT NOT NULL,language TEXT NOT NULL,sourceTextId INTEGER NOT NULL,state TEXT NOT NULL,accepted INTEGER NOT NULL,completedParts INTEGER NOT NULL,totalParts INTEGER NOT NULL,error TEXT NOT NULL,automatic INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion==1 && newVersion==2)
        val hasCodec=db.rawQuery("PRAGMA table_info(recordings)",null).use {cursor ->
            var present=false
            while(cursor.moveToNext())if(cursor.getString(cursor.getColumnIndexOrThrow("name"))=="codecMime")present=true
            present
        }
        if(!hasCodec)db.execSQL("ALTER TABLE recordings ADD COLUMN codecMime TEXT NOT NULL DEFAULT ''")
    }
    fun recordings(): List<SavedRecording> = readableDatabase.rawQuery("SELECT * FROM recordings ORDER BY createdAt DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(c.recording()) }
    }
    fun recording(id: String): SavedRecording? = readableDatabase.rawQuery("SELECT * FROM recordings WHERE id=?", arrayOf(id)).use { if (it.moveToFirst()) it.recording() else null }
    fun add(recording: SavedRecording) {
        require(UUID.fromString(recording.id).toString() == recording.id)
        require(recording.fileName.matches(Regex("${recording.id}\\.(m4a|mp3|wav|ogg|flac|aac|webm|amr)")))
        writableDatabase.insertOrThrow("recordings", null, ContentValues().apply {
            put("id", recording.id); put("title", recording.title); put("createdAt", recording.createdAt)
            put("durationMs", recording.durationMs); put("bytes", recording.bytes); put("fileName", recording.fileName); put("callKey", recording.callKey);put("mimeType",recording.mimeType);put("codecMime",recording.codecMime)
        })
    }
    fun texts(id: String): List<SavedTranscript> = readableDatabase.rawQuery("SELECT * FROM recording_texts WHERE recordingId=? ORDER BY id", arrayOf(id)).use { c ->
        buildList { while(c.moveToNext()) add(SavedTranscript(c.long("id"), c.string("recordingId"),c.string("operation"),c.string("language"),c.string("text"),c.string("jobId"))) }
    }
    fun jobs(): List<RecordingJob> = readableDatabase.rawQuery("SELECT * FROM recording_jobs ORDER BY rowid DESC", null).use { c -> buildList { while(c.moveToNext()) add(c.job()) } }
    fun job(id: String): RecordingJob? = readableDatabase.rawQuery("SELECT * FROM recording_jobs WHERE id=?", arrayOf(id)).use { if(it.moveToFirst()) it.job() else null }
    @Synchronized fun enqueue(recordingId: String, operation: String, language: String = "", sourceTextId: Long = 0, automatic: Boolean = false): String {
        require(recording(recordingId) != null)
        require(operation in setOf("transcribe", "translate"))
        if(operation == "translate") require(texts(recordingId).any { it.id == sourceTextId && it.operation == "transcribe" })
        val existing = jobs().firstOrNull { it.recordingId==recordingId && it.operation==operation && it.language==language && it.sourceTextId==sourceTextId && it.state in setOf("queued","running","blocked") }
        if(existing != null) return existing.id
        val id=UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("recording_jobs", null, ContentValues().apply {
            put("id",id);put("recordingId",recordingId);put("operation",operation);put("language",language);put("sourceTextId",sourceTextId)
            put("state","queued");put("accepted",0);put("completedParts",0);put("totalParts",0);put("error","");put("automatic",if(automatic) 1 else 0)
        });return id
    }
    fun update(id: String, state: String, accepted: Boolean, completed: Int = 0, total: Int = 0, error: String = "") {
        require(state in setOf("queued","running","completed","failed","cancelled","blocked"))
        writableDatabase.update("recording_jobs",ContentValues().apply { put("state",state);if(accepted)put("accepted",1);put("completedParts",completed);put("totalParts",total);put("error",error.take(64)) },"id=? AND state!='cancelling'",arrayOf(id))
    }
    fun accepted(id: String) {writableDatabase.execSQL("UPDATE recording_jobs SET accepted=1 WHERE id=?",arrayOf(id))}
    fun requestCancel(id: String) { writableDatabase.execSQL("UPDATE recording_jobs SET state='cancelling' WHERE id=? AND state IN ('queued','running','blocked')",arrayOf(id)) }
    fun cancelled(id: String) { writableDatabase.execSQL("UPDATE recording_jobs SET state='cancelled' WHERE id=?",arrayOf(id)) }
    fun retry(id: String) { writableDatabase.execSQL("UPDATE recording_jobs SET state='queued',error='' WHERE id=? AND state IN ('failed','blocked')",arrayOf(id)) }
    fun complete(job: RecordingJob, text: String, language: String,automaticLanguage: String=""): Boolean {
        val db=writableDatabase
        return db.transaction {
            if(recording(job.recordingId)==null || this@RecordingCatalog.job(job.id)?.state !in setOf("queued","running"))return@transaction false
            db.insertWithOnConflict("recording_texts",null,ContentValues().apply { put("recordingId",job.recordingId);put("operation",job.operation);put("language",language);put("text",text);put("jobId",job.id) },SQLiteDatabase.CONFLICT_IGNORE)
            update(job.id,"completed",true,job.completedParts,job.totalParts)
            if(job.operation=="transcribe" && automaticLanguage.isNotEmpty()) {
                val source=texts(job.recordingId).single {it.jobId==job.id}
                enqueue(job.recordingId,"translate",automaticLanguage,source.id,true)
            }
            true
        }
    }
    fun delete(id: String) {
        val db=writableDatabase
        db.transaction {
            db.execSQL("UPDATE recording_jobs SET state='cancelling' WHERE recordingId=? AND state IN ('queued','running','blocked')",arrayOf(id))
            db.delete("recording_texts","recordingId=?",arrayOf(id));db.delete("recordings","id=?",arrayOf(id))
        }
    }
    private fun Cursor.recording() = SavedRecording(string("id"),string("title"),long("createdAt"),long("durationMs"),long("bytes"),string("fileName"),string("callKey"),string("mimeType"),string("codecMime"))
    private fun Cursor.job() = RecordingJob(string("id"),string("recordingId"),string("operation"),string("language"),long("sourceTextId"),string("state"),long("accepted")==1L,long("completedParts").toInt(),long("totalParts").toInt(),string("error"),long("automatic")==1L)
    private fun Cursor.string(key: String) = getString(getColumnIndexOrThrow(key))
    private fun Cursor.long(key: String) = getLong(getColumnIndexOrThrow(key))
}
