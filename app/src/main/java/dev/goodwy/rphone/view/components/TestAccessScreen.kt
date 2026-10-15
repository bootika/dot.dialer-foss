package dev.goodwy.rphone.view.components

import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.license.TestAccessState

@Composable
fun TestAccessScreen(state: TestAccessState, retry: () -> Unit) {
    val context = LocalContext.current
    val actionColor = Color(0xFF006B50)
    val linkColors = ButtonDefaults.textButtonColors(contentColor = actionColor)
    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch (error: android.content.ActivityNotFoundException) {
            android.util.Log.w("DotTestAccess", "open: ${error.javaClass.simpleName}")
        } catch (error: SecurityException) {
            android.util.Log.w("DotTestAccess", "open: ${error.javaClass.simpleName}")
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF0F0F0)).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("dot.dialer", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF202020))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(when (state) {
            TestAccessState.CHECKING -> R.string.test_access_checking
            TestAccessState.UNAVAILABLE -> R.string.test_access_unavailable
            else -> R.string.test_access_blocked
        }), color = Color(0xFF202020))
        Spacer(Modifier.height(20.dp))
        Button(onClick = retry, colors = ButtonDefaults.buttonColors(containerColor = actionColor, contentColor = Color.White)) { Text(stringResource(R.string.test_access_retry)) }
        TextButton(colors = linkColors, onClick = { open(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=dot.dialer".toUri())) }) {
            Text("Google Play")
        }
        TextButton(colors = linkColors, onClick = { open(Intent(Intent.ACTION_SENDTO, "mailto:diler@dotdesign.ro".toUri())) }) { Text("diler@dotdesign.ro") }
        TextButton(colors = linkColors, onClick = { open(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }) { Text(stringResource(R.string.test_access_phone_settings)) }
    }
}
