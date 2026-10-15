package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.goodwy.rphone.R
import kotlinx.coroutines.launch

// ─── Password Setup Dialog ───────────────────────────────────────────────────

@Composable
fun PasswordDialogContent(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.set_password),
    isVerify: Boolean = false,
    verifySecret: suspend (String) -> Boolean = { false },
    showCloseButton: Boolean = true
) {
    val verificationScope = rememberCoroutineScope()
    var verifying by remember { mutableStateOf(false) }
    var recoveryAvailable by remember { mutableStateOf(false) }
    val retryMessage = stringResource(R.string.authentication_retry_later)
    val unavailable = stringResource(R.string.authentication_unavailable)
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    Column(
        Modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = if (isVerify) title else stringResource(R.string.set_password),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (showCloseButton) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(0.3f),
                            RoundedCornerShape(50)
                        )
                        .padding(2.dp)
                ) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.close))
                }
            }
        }

        if (!isVerify) {
            Text(
                stringResource(R.string.authentication_password_minimum),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorText = "" },
            label = { Text(if (isVerify) stringResource(R.string.password) else stringResource(R.string.enter_password)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            isError = errorText.isNotEmpty()
        )

        if (!isVerify) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorText = "" },
                label = { Text(stringResource(R.string.confirm_password)) },
                singleLine = true,
                visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showConfirm = !showConfirm }) {
                        Icon(if (showConfirm) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                isError = errorText.isNotEmpty()
            )
        }

        AnimatedVisibility(visible = errorText.isNotEmpty()) {
            Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (recoveryAvailable) AuthenticationRecovery(onRecovered = { onConfirm("") })
        val incorrectPassword = stringResource(R.string.incorrect_password)
        val stringMinimumPassword = stringResource(R.string.authentication_password_minimum)
        val passwordDontMatch = stringResource(R.string.password_dont_match)
        Row(Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, end = 1.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)
            ) {
                Text(
                    stringResource(R.string.cancel),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Button(
                enabled = !verifying,
                onClick = {
                    when {
                        isVerify -> {
                            val submitted = password
                            verifying = true
                            verificationScope.launch {
                                try {
                                    if (verifySecret(submitted)) onConfirm(submitted)
                                    else errorText = incorrectPassword
                                } catch (error: dev.goodwy.rphone.controller.lock.AuthenticationRetryException) {
                                    errorText = retryMessage
                                } catch (error: Exception) {
                                    android.util.Log.w("Authentication", "Verification failed: ${error.javaClass.simpleName}")
                                    recoveryAvailable = true
                                    errorText = unavailable
                                } finally { verifying = false }
                            }
                        }
                        password.length < 8 -> errorText = stringMinimumPassword
                        password != confirmPassword -> errorText = passwordDontMatch
                        else -> onConfirm(password)
                    }
                },
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                stringResource(R.string.confirm),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun PasswordSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.set_password),
    isVerify: Boolean = false,
    verifySecret: suspend (String) -> Boolean = { false },
    showCloseButton: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn,
            dismissOnBackPress = showCloseButton,
            dismissOnClickOutside = showCloseButton
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            PasswordDialogContent(
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                title = title,
                isVerify = isVerify,
                verifySecret = verifySecret,
                showCloseButton = showCloseButton
            )
        }
    }
}
