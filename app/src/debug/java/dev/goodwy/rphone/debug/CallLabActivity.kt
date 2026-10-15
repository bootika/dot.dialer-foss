package dev.goodwy.rphone.debug

import android.os.Bundle
import android.telecom.Connection
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.theme.DotDialerTheme

internal class CallLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = CallLabController(this)
        controller.registerAccount()
        setContent {
            DotDialerTheme {
                CallLabScreen(controller)
            }
        }
    }
}

@Composable
private fun CallLabScreen(controller: CallLabController) {
    val snapshot by CallLabRegistry.snapshot.collectAsState()
    var accountEnabled by remember { mutableStateOf(controller.isAccountEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accountEnabled = controller.isAccountEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.call_lab_title))
                        Text(
                            stringResource(R.string.call_lab_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(
                                if (accountEnabled) {
                                    R.string.call_lab_account_ready
                                } else {
                                    R.string.call_lab_account_disabled
                                },
                            ),
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    controller.registerAccount()
                                    accountEnabled = controller.isAccountEnabled()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.call_lab_register)) }
                            OutlinedButton(
                                onClick = controller::openAccountSettings,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.call_lab_account_settings))
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(
                        R.string.call_lab_status,
                        snapshot.connectionCount,
                        snapshot.conferenceCount,
                        snapshot.latestState?.let(::callStateLabel)
                            ?: stringResource(R.string.call_lab_no_latest),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                CallLabButtonRow(
                    firstLabel = stringResource(R.string.call_lab_incoming),
                    firstAction = { controller.incoming() },
                    secondLabel = stringResource(R.string.call_lab_second_incoming),
                    secondAction = {
                        controller.incoming(
                            CallLabContract.SECONDARY_NUMBER,
                            CallLabContract.SECONDARY_NAME,
                        )
                    },
                )
            }
            item {
                CallLabButtonRow(
                    firstLabel = stringResource(R.string.call_lab_outgoing),
                    firstAction = { controller.outgoing() },
                    secondLabel = stringResource(R.string.call_lab_activate),
                    secondAction = CallLabRegistry::setLatestActive,
                )
            }
            item {
                CallLabButtonRow(
                    firstLabel = stringResource(R.string.call_lab_hold),
                    firstAction = CallLabRegistry::holdLatest,
                    secondLabel = stringResource(R.string.call_lab_resume),
                    secondAction = CallLabRegistry::resumeLatest,
                )
            }
            item {
                CallLabButtonRow(
                    firstLabel = stringResource(R.string.call_lab_conference),
                    firstAction = { CallLabConnectionService.conferenceAll() },
                    secondLabel = stringResource(R.string.call_lab_disconnect),
                    secondAction = CallLabRegistry::disconnectLatest,
                )
            }
            item {
                Button(
                    onClick = CallLabRegistry::reset,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.call_lab_reset)) }
            }
            item {
                Text(
                    stringResource(R.string.call_lab_adb_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CallLabButtonRow(
    firstLabel: String,
    firstAction: () -> Unit,
    secondLabel: String,
    secondAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(onClick = firstAction, modifier = Modifier.weight(1f)) { Text(firstLabel) }
        Button(onClick = secondAction, modifier = Modifier.weight(1f)) { Text(secondLabel) }
    }
}

private fun callStateLabel(state: Int): String = when (state) {
    Connection.STATE_INITIALIZING -> "initializing"
    Connection.STATE_NEW -> "new"
    Connection.STATE_RINGING -> "ringing"
    Connection.STATE_DIALING -> "dialing"
    Connection.STATE_ACTIVE -> "active"
    Connection.STATE_HOLDING -> "holding"
    Connection.STATE_DISCONNECTED -> "disconnected"
    else -> state.toString()
}
