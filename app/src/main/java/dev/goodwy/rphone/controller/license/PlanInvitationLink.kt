package dev.goodwy.rphone.controller.license

import java.net.URI

internal object PlanInvitationLink {
    fun token(value: String, audience: String): String? = runCatching {
        if(audience !in setOf("dot.dialer","dot.dialer.test"))return@runCatching null
        if(value.trim().matches(Regex("[a-f0-9]{64}"))) return@runCatching value.trim()
        val uri=URI(value.trim())
        if(uri.rawQuery!=null && uri.fragment!=null)return@runCatching null
        val scope=if(audience=="dot.dialer.test") "test" else "app"
        if(uri.scheme!="https" || uri.host!="dialer.dotdesign.ro" || uri.port!=-1 || uri.userInfo!=null || uri.path!="/invite/$scope/")return@runCatching null
        val code=if(uri.rawQuery==null) uri.fragment else uri.rawQuery.takeIf {it.matches(Regex("code=[a-f0-9]{64}"))}?.removePrefix("code=")
        code?.takeIf {it.matches(Regex("[a-f0-9]{64}"))}
    }.getOrNull()
}
