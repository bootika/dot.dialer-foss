package dev.goodwy.rphone.controller.license

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

internal data class PlanInvitationPreview(val label: String,val company: String,val tier: String,val days: Int,val until: Long)

internal fun loadPlanInvitation(token: String,audience: String): PlanInvitationPreview {
    require(token.matches(Regex("[a-f0-9]{64}")))
    val connection=URL("https://dialer.dotdesign.ro/api/v1/invitation").openConnection() as HttpURLConnection
    try {
        connection.requestMethod="POST";connection.doOutput=true;connection.instanceFollowRedirects=false
        connection.connectTimeout=10_000;connection.readTimeout=10_000
        connection.setRequestProperty("Content-Type","application/json")
        connection.outputStream.use {it.write(JSONObject().put("token",token).put("package",audience).toString().encodeToByteArray())}
        require(connection.responseCode==200)
        val bytes=connection.inputStream.use {input ->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(1024)
            while(true) {val count=input.read(buffer);if(count<0)break;require(output.size()+count<=8192);output.write(buffer,0,count)}
            output.toByteArray()
        }
        val body=JSONObject(String(bytes,Charsets.UTF_8))
        val tier=body.getString("tier");require(tier in setOf("plus","premium"))
        return PlanInvitationPreview(body.getString("label").take(80),body.getString("company").take(80),tier,body.getInt("days"),body.getLong("until"))
    } finally {connection.disconnect()}
}
