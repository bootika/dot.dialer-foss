package dev.goodwy.rphone.controller.license

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.goodwy.rphone.view.screen.settings.LocalPlanAccessManager
import dev.goodwy.rphone.view.theme.DotAccent
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.core.license.AppPlan
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.*
import org.json.JSONObject

/** Existing optimizer UI cases exercise Premium explicitly, using the actual RSA verifier/state machine. */
@Composable
internal fun VerifiedPremiumTheme(systemDark: Boolean=isSystemInDarkTheme(),content: @Composable ()->Unit) {
    val inherited=LocalPlanAccessManager.current
    val audience=LocalContext.current.packageName
    val worker=remember {CoroutineScope(SupervisorJob()+Dispatchers.IO)}
    val manager=remember(inherited) {inherited ?: verifiedPlanFixture(audience,AppPlan.PREMIUM,worker)}
    DisposableEffect(worker) {onDispose {worker.cancel()}}
    CompositionLocalProvider(LocalPlanAccessManager provides manager) {
        DotDialerTheme(systemDark=systemDark,accent=DotAccent.Jade,content=content)
    }
}

internal fun verifiedPlanFixture(audience: String,plan: AppPlan,worker: CoroutineScope, trial: Boolean = false): PlanAccessManager {
    val clock = if (trial) System.currentTimeMillis() / 1000 else 1000L
    val keys=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
    val id="6c43ee4d-1111-4111-8111-010203040506"
    val manager=PlanAccessManager(audience,{id},{null},{},PlanGrantVerifier(Base64.getEncoder().encodeToString(keys.public.encoded)),worker,{request ->
        val body=JSONObject().put("v",1).put("aud",audience).put("installation",id).put("license","b".repeat(32)).put("entitlement","plan_access")
            .put("tier",plan.name.lowercase(java.util.Locale.ROOT)).put("trial_started",if (trial) clock else 0).put("trial_ends",if (trial) clock + 30 * 86400 else 0)
            .put("iat",clock).put("exp",clock + 86400).put("nonce",request.getString("nonce"))
        val encoder=Base64.getUrlEncoder().withoutPadding();val payload=encoder.encodeToString(body.toString().encodeToByteArray())
        val signature=Signature.getInstance("SHA256withRSA").apply {initSign(keys.private);update(payload.encodeToByteArray())}.sign()
        JSONObject().put("grant",payload+"."+encoder.encodeToString(signature))
    },{clock},{1000})
    runBlocking {withTimeout(5000) {while(manager.state.value.revision<3 || manager.state.value.busy)delay(1)}}
    check(manager.effective==if (trial) AppPlan.PREMIUM else plan)
    return manager
}
