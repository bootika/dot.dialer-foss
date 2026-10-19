package dev.goodwy.rphone.recordings

import org.json.JSONObject

internal data class RecordingResult(val text: String, val language: String) {
    companion object {
        fun parse(raw: String, job: RecordingJob): RecordingResult {
            val result=JSONObject(raw)
            require(result.getInt("protocolVersion")==1 && result.getString("jobId")==job.id &&
                result.getString("recordingId")==job.recordingId && result.getString("operation")==job.operation)
            val text=result.getString("text")
            require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size<=DictateProtocol.RESULT_LIMIT)
            val language=result.optString("language").take(48)
            if(job.operation=="translate") require(language.isEmpty() || language.equals(job.language,true))
            return RecordingResult(text,language.ifBlank {job.language})
        }
    }
}
