package dev.goodwy.rphone.view.components

import dev.goodwy.rphone.view.theme.accentText

import android.telecom.PhoneAccountHandle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallingAccountInfo
import dev.goodwy.rphone.controller.util.getCallingAccounts
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.rememberOutgoingAccount
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimPickerDialog(
    onDismissRequest: () -> Unit,
    onSimSelected: (PhoneAccountHandle) -> Unit,
    number: String? = null,
    availableAccounts: List<CallingAccountInfo>? = null,
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    var scope by remember(number) { mutableIntStateOf(if (number.isNullOrBlank()) 0 else 1) }
    var scopeMenu by remember(number) { mutableStateOf(false) }
    val scopes = listOf(1 to R.string.sim_scope_number, 2 to R.string.sim_scope_all, 0 to R.string.sim_scope_once)
    val phoneAccounts = availableAccounts ?: remember { getCallingAccounts(context) }

    if (phoneAccounts.isNotEmpty()) {
        Dialog(
            onDismissRequest = onDismissRequest,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(
                            top = 20.dp,
                            bottom = 8.dp,
                            start = 20.dp,
                            end = 20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sim_picker_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (!number.isNullOrBlank()) {
                        Text(phoneDisplayText(number), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp))
                    }
                    if (!number.isNullOrBlank()) {
                        Box {
                            Surface(
                                onClick = { scopeMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(scopes.first { it.first == scope }.second),
                                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = scopeMenu, onDismissRequest = { scopeMenu = false },
                                modifier = Modifier.widthIn(min = 240.dp, max = 300.dp)) {
                                scopes.forEach { (scopeId, label) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        leadingIcon = { RadioButton(selected = scope == scopeId, onClick = null) },
                                        onClick = { scope = scopeId; scopeMenu = false },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    RillExpressiveCard {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            phoneAccounts.forEach { info ->
                                val accountColor = info.highlightColor
                                    .takeIf { Color(it).alpha > 0f }
                                    ?.let(::Color)
                                    ?: MaterialTheme.colorScheme.onSurfaceVariant

                                Surface(
                                    onClick = {
                                        rememberOutgoingAccount(prefs, number, info.key, scope)
                                        onSimSelected(info.handle)
                                    },
                                    shape = RoundedCornerShape(cardCornerExtraSmall),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = RoundedCornerShape(50),
                                            color = accountColor.copy(alpha = 0.16f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                CallingAccountIdentityIcon(
                                                    account = info,
                                                    fallbackColor = accountColor,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = info.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium
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
