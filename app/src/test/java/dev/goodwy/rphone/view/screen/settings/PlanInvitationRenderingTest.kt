package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.license.*
import dev.goodwy.rphone.view.theme.DotDialerTheme
import io.github.bootika.dotdialer.core.license.AppPlan
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.*
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class PlanInvitationRenderingTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val id="6c43ee4d-1111-4111-8111-010203040506"
    private val code="a".repeat(64)
    private var requests=0
    private fun manager(): PlanAccessManager {
        val keys=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
        val audience=rule.activity.packageName
        return PlanAccessManager(audience,{id},{null},{},PlanGrantVerifier(Base64.getEncoder().encodeToString(keys.public.encoded)),worker,{request ->
            requests++
            val body=JSONObject().put("v",1).put("aud",audience).put("installation",id).put("license","b".repeat(32)).put("entitlement","plan_access")
                .put("tier",if(request.has("invitation")) "premium" else "free").put("trial_started",0).put("trial_ends",0)
                .put("iat",1000).put("exp",87400).put("nonce",request.getString("nonce"))
            if(request.has("invitation"))assertEquals(code,request.getString("invitation"))
            val encoder=Base64.getUrlEncoder().withoutPadding();val payload=encoder.encodeToString(body.toString().encodeToByteArray())
            val signature=Signature.getInstance("SHA256withRSA").apply {initSign(keys.private);update(payload.encodeToByteArray())}.sign()
            JSONObject().put("grant",payload+"."+encoder.encodeToString(signature))
        },{1000},{1000})
    }
    @After fun cleanup() {worker.cancel()}
    @Test fun `preview shows recipient company and terms but only confirmation activates signed rights`() {
        val manager=manager()
        var dark by mutableStateOf(false);var scale by mutableFloatStateOf(1f)
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            DotDialerTheme(systemDark=dark) {PlanInvitationContent("https://dialer.dotdesign.ro/invite/test/#$code",{},manager,
                {_,_->PlanInvitationPreview("Test Alexandru Butica","Example Company","premium",30,0)})}
        }}
        rule.waitUntil(5000) {manager.state.value.revision>=2 && !manager.state.value.busy}
        rule.waitUntil(5000) {rule.onAllNodesWithText("Example Company").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Example Company").assertExists()
        assertEquals(AppPlan.FREE,manager.effective);assertEquals(1,requests)
        for(mode in listOf(false,true))for(font in listOf(1f,1.5f)) {
            rule.runOnIdle {dark=mode;scale=font}
            rule.onNodeWithTag("invitation-activate").performScrollTo().assertIsDisplayed()
            rule.runOnIdle {
                val view=rule.activity.window.decorView;val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(image))
                val directory=File(System.getProperty("user.dir"),"build/reports/visual/plan-invitations").apply {mkdirs()}
                File(directory,"${if(mode) "dark" else "light"}-${(font*100).toInt()}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            }
        }
        assertEquals(1,requests)
        rule.onNodeWithTag("invitation-activate").performClick()
        rule.waitUntil(5000) {manager.effective==AppPlan.PREMIUM && !manager.state.value.busy}
        rule.onNodeWithText(rule.activity.getString(R.string.invitation_activated)).assertExists()
        assertEquals(2,requests)
    }
}
