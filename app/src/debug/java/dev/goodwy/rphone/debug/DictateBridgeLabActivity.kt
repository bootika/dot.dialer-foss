package dev.goodwy.rphone.debug

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.goodwy.rphone.recordings.DictateBridgeClient
import kotlinx.coroutines.*
import org.json.JSONObject

/** Non-exported debug-only host for actual OS ActivityResult/Messenger integration checks. */
class DictateBridgeLabActivity : ComponentActivity() {
    private val worker=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var client: DictateBridgeClient
    @Volatile var completion: String?=null
        private set
    private lateinit var label: TextView
    private val setup=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
        if(result.resultCode!=Activity.RESULT_OK)complete(JSONObject().put("approved",false).put("trusted",client.trusted()))
        else worker.launch {
            try {
                client.approveSetup()
                val cap=client.capabilities.value
                val raw=client.request(1,Bundle())
                complete(JSONObject().put("approved",true).put("trusted",client.trusted()).put("connected",cap.connected)
                    .put("licenseState",cap.licenseState).put("apiKeyConfigured",cap.apiKeyConfigured)
                    .put("supportsTranscription",cap.transcription).put("supportsTranslation",cap.translation)
                    .put("supportsCallCapture",raw.getBoolean("supportsCallCapture"))
                    .put("maxInputBytes",cap.maxInputBytes).put("maxInputSeconds",cap.maxInputSeconds))
            } catch(error: Exception) {complete(JSONObject().put("error",error.javaClass.simpleName))}
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label=TextView(this).apply {text=getString(dev.goodwy.rphone.R.string.recordings_title);textSize=20f;setPadding(24,60,24,24)};setContentView(label)
        client=DictateBridgeClient(applicationContext)
        setup.launch(client.setupIntent())
    }
    private fun complete(result: JSONObject) {completion=result.toString();label.text=result.toString(2)}
    override fun onDestroy() {worker.cancel();super.onDestroy()}
}
