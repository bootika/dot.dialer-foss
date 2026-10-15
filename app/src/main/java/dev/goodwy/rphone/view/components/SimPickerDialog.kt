package dev.goodwy.rphone.view.components

import dev.goodwy.rphone.view.theme.accentText

import android.telecom.PhoneAccountHandle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallingAccountInfo
import dev.goodwy.rphone.controller.util.getCallingAccounts

@Composable
fun SimPickerDialog(
    onDismissRequest: () -> Unit,
    onSimSelected: (PhoneAccountHandle) -> Unit
) {
    val context = LocalContext.current
    val phoneAccounts = remember { getCallingAccounts(context) }

    if (phoneAccounts.isNotEmpty()) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .wrapContentHeight()
                    .padding(top = 100.dp), 
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            top = 24.dp,
                            bottom = 8.dp,
                            start = 24.dp,
                            end = 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sim_picker_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                    )

                    RillExpressiveCard {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(phoneAccounts, key = CallingAccountInfo::key) { info ->
                                val accountColor = info.highlightColor
                                    .takeIf { Color(it).alpha > 0f }
                                    ?.let(::Color)
                                    ?: MaterialTheme.colorScheme.primary

                                Surface(
                                    onClick = { onSimSelected(info.handle) },
                                    shape = RoundedCornerShape(cardCornerExtraSmall),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(48.dp),
                                            shape = RoundedCornerShape(50),
                                            color = accountColor.copy(alpha = 0.16f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                CallingAccountIdentityIcon(
                                                    account = info,
                                                    fallbackColor = accountColor,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column {
                                            Text(
                                                text = info.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (info.description != null) {
                                                Text(
                                                    text = info.description,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.align(Alignment.End), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    } else {
        SideEffect {
            onDismissRequest()
        }
    }
}
