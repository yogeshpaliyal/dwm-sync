package com.yogeshpaliyal.dwmsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { SetupScreen() }
            }
        }
    }
}

@Composable
private fun SetupScreen() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    var url by remember { mutableStateOf(settings.baseUrl) }
    var token by remember { mutableStateOf(settings.token) }
    var days by remember { mutableStateOf(settings.backfillDays.toString()) }
    var samsungOnly by remember { mutableStateOf(settings.samsungOnly) }
    var message by remember { mutableStateOf<String?>(null) }

    val sdkStatus = remember { HealthConnectClient.getSdkStatus(context) }
    val client = remember(sdkStatus) {
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context) else null
    }
    var wanted by remember { mutableStateOf(HealthPermissions.required) }
    var granted by remember { mutableStateOf(emptySet<String>()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted = it }

    LaunchedEffect(client) {
        client?.let {
            wanted = HealthPermissions.toRequest(it)
            granted = it.permissionController.getGrantedPermissions()
        }
    }

    val work by WorkManager.getInstance(context)
        .getWorkInfosFlow(WorkQuery.fromUniqueWorkNames(listOf(Sync.PERIODIC, Sync.NOW)))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val running = work.any { it.state == WorkInfo.State.RUNNING }
    val scheduled = work.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
    val status = remember(work) { settings.lastStatus }

    Column(
        Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Devs Who Move sync", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Uploads workouts from Health Connect to your account as private sessions, every ~3 hours.",
            style = MaterialTheme.typography.bodyMedium,
        )

        // 1. Health Connect
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1 · Health Connect", style = MaterialTheme.typography.titleMedium)
                when {
                    client == null -> {
                        Text("Health Connect isn't installed or needs an update.")
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=com.google.android.apps.healthdata"),
                                )
                            )
                        }) { Text("Open Play Store") }
                    }
                    granted.containsAll(HealthPermissions.required) -> {
                        Text("✓ Exercise and distance access granted")
                        if (!granted.containsAll(wanted)) {
                            Text(
                                "Background or history access is off — background syncs may fail.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { permissionLauncher.launch(wanted) }) { Text("Grant the rest") }
                        }
                    }
                    else -> Button(onClick = { permissionLauncher.launch(wanted) }) { Text("Grant access") }
                }
            }
        }

        // 2. Account
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2 · Devs Who Move", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Site URL") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text("Agent token (activities:write)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = days, onValueChange = { days = it.filter(Char::isDigit).take(4) },
                    label = { Text("First sync: import last N days") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Only Samsung Health workouts")
                        Text("Avoids duplicates from other apps", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = samsungOnly, onCheckedChange = { samsungOnly = it })
                }
            }
        }

        // 3. Sync
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = client != null && granted.containsAll(HealthPermissions.required),
            onClick = {
                when {
                    !url.trim().startsWith("https://") -> message = "Site URL must start with https://"
                    token.isBlank() -> message = "Paste your agent token first"
                    else -> {
                        settings.baseUrl = url
                        settings.token = token
                        settings.backfillDays = days.toIntOrNull() ?: 30
                        settings.samsungOnly = samsungOnly
                        Sync.schedule(context)
                        Sync.runNow(context)
                        message = "Saved. Auto-sync is on."
                    }
                }
            },
        ) { Text(if (scheduled) "Save & sync now" else "Save & turn on auto-sync") }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Text(if (running) "Syncing…" else status)
                Text(
                    if (scheduled) "Auto-sync: on" else "Auto-sync: off",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                settings.watermark = 0L
                message = "Next sync will re-check the last ${settings.backfillDays} days (no duplicates)."
            }) { Text("Re-import") }
            if (scheduled) {
                OutlinedButton(onClick = {
                    Sync.cancel(context); message = "Auto-sync turned off."
                }) { Text("Turn off") }
            }
        }
        Spacer(Modifier.padding(8.dp))
    }
}
