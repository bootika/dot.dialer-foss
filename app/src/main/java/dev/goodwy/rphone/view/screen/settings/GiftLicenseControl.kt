package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.license.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun GiftLicenseControl(manager: GiftLicenseManager) {
    val active by manager.isPro.collectAsStateWithLifecycle()
    var showing by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") } // Secret is deliberately not saved in instance state.
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    DotSettingsCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (active) R.string.gift_active else R.string.gift_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.gift_description), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { showing = true; code = ""; error = null }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.gift_activate)) }
        }
    }
    if (showing) AlertDialog(
        onDismissRequest = { if (!busy) { showing = false; code = "" } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.gift_activate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it.take(80); error = null }, enabled = !busy,
                    label = { Text(stringResource(R.string.gift_code)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && code.isNotBlank(), onClick = {
                busy = true; error = null
                scope.launch {
                    try { manager.activate(code); showing = false; code = "" }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        error = when ((failure as? GiftLicenseException)?.reason) {
                            GiftLicenseError.INVALID -> R.string.gift_invalid
                            GiftLicenseError.LIMIT -> R.string.gift_limit
                            else -> R.string.gift_unavailable
                        }
                    } finally { busy = false }
                }
            }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.gift_activate)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { showing = false; code = "" }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.cancel)) } },
    )
}
