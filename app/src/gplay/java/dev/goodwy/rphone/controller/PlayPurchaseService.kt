package dev.goodwy.rphone.controller

import android.content.Context
import dev.goodwy.rphone.controller.license.GiftLicenseStorage
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Private bearer inputs. Deliberately not a data class: logs must never reveal credentials. */
internal class PlayPurchaseReceipt(val product: String, private val token: String) {
    init { require(product.isNotBlank() && token.isNotBlank() && token.length <= 8192) }
    fun json(): JSONObject = JSONObject().put("product_id", product).put("token", token)
    override fun toString(): String = "PlayPurchaseReceipt([redacted])"
}

internal class PlayPurchaseIdentity(val installation: String, val secret: String) {
    init {
        require(installation.matches(Regex("[A-Za-z0-9_-]{16,128}")))
        require(secret.matches(Regex("[a-f0-9]{64}")))
    }
    override fun toString(): String = "PlayPurchaseIdentity([redacted])"
}

/** No URL/query/log contains a receipt, credential, principal or nonce. */
internal class PlayPurchaseService(
    private val audience: String,
    private val identity: PlayPurchaseIdentity,
    private val transport: (String, String, JSONObject) -> JSONObject,
    private val nonce: () -> String = ::randomSecret,
    private val reserveSequence: () -> Long = { error("Durable purchase request counter unavailable") },
) {
    var principal: String? = null
        private set

    fun register(): JSONObject = request("register", null, false)
    fun verify(receipts: List<PlayPurchaseReceipt>, restore: Boolean): JSONObject = request("verify", receipts, restore)
    fun refresh(): JSONObject = request("refresh", null, false)

    private fun request(action: String, receipts: List<PlayPurchaseReceipt>?, restore: Boolean): JSONObject {
        val challenge = nonce()
        require(challenge.matches(Regex("[a-f0-9]{64}")))
        val sequence=reserveSequence()
        require(sequence in 1..9_007_199_254_740_991L)
        val body = JSONObject().put("v", 1).put("package", audience)
            .put("installation", identity.installation).put("nonce", challenge).put("sequence",sequence)
        if (receipts != null) body.put("purchases", JSONArray(receipts.map(PlayPurchaseReceipt::json)))
            .put("ownership_complete", true).put("restore", restore)
        val response = transport(action, identity.secret, body)
        val resolved = response.getString("principal")
        require(resolved.matches(Regex("[a-f0-9]{64}")))
        // A register reply bootstraps checkout identity only; callers must not treat it as restore.
        return JSONObject().put("principal", resolved).put("grant", response.getJSONObject("grant"))
            .put("request_nonce", challenge)
            .put("request_sequence",sequence)
    }

    fun acceptPrincipal(response: JSONObject) {
        val value = response.getString("principal")
        require(value.matches(Regex("[a-f0-9]{64}")))
        principal = value
    }

    companion object {
        fun identity(context: Context): PlayPurchaseIdentity {
            val store = GiftLicenseStorage(context, "dot.dialer.play-device.v1", "play-device-v1.bin")
            synchronized(PlayPurchaseService::class.java) {
                val record = store.read()
                if (record != null) {
                    require(record.keys().asSequence().toSet() == setOf("installation", "secret", "sequence"))
                    val id = record.getString("installation"); val secret = record.getString("secret")
                    require(id.matches(Regex("[A-Za-z0-9_-]{16,128}")) && secret.matches(Regex("[a-f0-9]{64}")))
                    require(record.get("sequence") is Int || record.get("sequence") is Long)
                    require(record.getLong("sequence") in 0 until 9_007_199_254_740_991L)
                    return PlayPurchaseIdentity(id, secret)
                }
                val id = PlayPurchaseIdentity(UUID.randomUUID().toString(), randomSecret())
                // Never register a credential that could not be persisted for the next launch.
                store.write(JSONObject().put("installation", id.installation).put("secret", id.secret).put("sequence",0))
                return id
            }
        }

        fun reserveSequence(context: Context, identity: PlayPurchaseIdentity): Long = synchronized(PlayPurchaseService::class.java) {
            val store=GiftLicenseStorage(context,"dot.dialer.play-device.v1","play-device-v1.bin")
            val record=checkNotNull(store.read())
            reserveSequence(identity,record,store::write)
        }

        fun reserveSequence(identity: PlayPurchaseIdentity,record: JSONObject,commit: (JSONObject)->Unit):Long {
            require(record.getString("installation")==identity.installation && record.getString("secret")==identity.secret)
            require(record.get("sequence") is Int || record.get("sequence") is Long)
            val previous=record.getLong("sequence")
            require(previous in 0 until 9_007_199_254_740_991L)
            val next=previous+1
            commit(JSONObject(record.toString()).put("sequence",next))
            return next
        }

        fun http(action: String, secret: String, body: JSONObject): JSONObject {
            require(action in setOf("register", "verify", "refresh"))
            val connection = URL("https://dialer.dotdesign.ro/api/v1/play/$action").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8000; connection.readTimeout = 30000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $secret")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                check(connection.responseCode == 200) { "Purchase verification unavailable" }
                val bytes = connection.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(out.size() + count <= 32768)
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
                return JSONObject(String(bytes, Charsets.UTF_8))
            } finally { connection.disconnect() }
        }
    }
}

private fun randomSecret(): String = ByteArray(32).also(SecureRandom()::nextBytes)
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
