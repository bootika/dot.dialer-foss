package dev.goodwy.rphone.recordings

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import io.github.bootika.dotdialer.core.recordings.DictateCapabilities
import java.io.File
import org.json.JSONObject

/** Retains authenticated companion evidence; a failed check never manufactures a grant. */
internal class CaptureAuthorization(context: Context) {
    private val app=context.applicationContext
    private val file=AtomicFile(File(app.noBackupFilesDir,"call-capture/authorization.json"))
    @Synchronized fun observe(caps: DictateCapabilities) {
        if(!caps.connected || caps.licenseState=="unavailable")return
        if(caps.licenseState!="valid" || !caps.apiKeyConfigured || !caps.transcription){file.delete();return}
        val evidence=JSONObject().put("wall",System.currentTimeMillis()).put("elapsed",SystemClock.elapsedRealtime())
            .put("boot",Settings.Global.getInt(app.contentResolver,Settings.Global.BOOT_COUNT,-1))
            .put("speakers",caps.separateSpeakerChannels)
        val out=file.startWrite()
        try{out.write(evidence.toString().toByteArray());file.finishWrite(out)}catch(e: Exception){file.failWrite(out);throw e}
    }
    @Synchronized fun valid(separateSpeakers: Boolean=false): Boolean {
        return try {
        val saved=file.openRead().use{require(it.available()<=1024);JSONObject(it.readBytes().toString(Charsets.UTF_8))}
        val wallAge=System.currentTimeMillis()-saved.getLong("wall")
        val sameBoot=saved.getInt("boot")==Settings.Global.getInt(app.contentResolver,Settings.Global.BOOT_COUNT,-2)
        val elapsedAge=if(sameBoot)SystemClock.elapsedRealtime()-saved.getLong("elapsed")else wallAge
        fresh(wallAge,elapsedAge) && (!separateSpeakers || saved.getBoolean("speakers"))
    }catch(missing: java.io.FileNotFoundException){
        // No saved grant is a normal, fail-closed state; openRead still recovers AtomicFile backups.
        if(file.baseFile.exists())captureFailures.report(CaptureFailureStage.AUTHORIZATION,missing)
        false
    }catch(error: Exception){captureFailures.report(CaptureFailureStage.AUTHORIZATION,error);false}
    }
    fun clear(){file.delete()}
    companion object {
        const val MAX_AGE=10L*24*60*60*1000
        fun fresh(wallAge: Long,elapsedAge: Long)=wallAge in 0..MAX_AGE && elapsedAge in 0..MAX_AGE
    }
}
