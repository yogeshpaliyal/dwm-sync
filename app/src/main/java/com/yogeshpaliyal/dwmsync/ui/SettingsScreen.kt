package com.yogeshpaliyal.dwmsync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.dwmsync.Settings
import com.yogeshpaliyal.dwmsync.Sync
import com.yogeshpaliyal.dwmsync.SyncLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onResetSetup: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val hc = rememberHealthConnect()

    var url by remember { mutableStateOf(settings.baseUrl) }
    var token by remember { mutableStateOf("") }
    var accountMsg by remember { mutableStateOf<String?>(null) }
    var autoSync by remember { mutableStateOf(settings.autoSync) }
    var interval by remember { mutableStateOf(settings.intervalHours) }
    var samsungOnly by remember { mutableStateOf(settings.samsungOnly) }
    var confirm by remember { mutableStateOf<String?>(null) } // "clear" | "reset"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Account
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Devs Who Move account", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = url, onValueChange = { url = it; accountMsg = null },
                        label = { Text("Site URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = token, onValueChange = { token = it; accountMsg = null },
                        label = { Text("New agent token") },
                        placeholder = { Text(if (settings.token.isNotBlank()) "Token saved — paste to replace" else "Paste token") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        accountMsg = when {
                            !url.trim().startsWith("https://") -> "Site URL must start with https://"
                            else -> {
                                settings.baseUrl = url
                                if (token.isNotBlank()) { settings.token = token; token = "" }
                                "Saved"
                            }
                        }
                    }) { Text("Save") }
                    accountMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            HealthConnectCard(hc)

            // Sync
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sync", style = MaterialTheme.typography.titleMedium)
                    SwitchRow("Auto-sync", "Upload new workouts in the background", autoSync) {
                        autoSync = it
                        settings.autoSync = it
                        if (it) Sync.schedule(context, interval) else Sync.cancel(context)
                    }
                    if (autoSync) {
                        Text("Check every", style = MaterialTheme.typography.bodyMedium)
                        Settings.INTERVAL_OPTIONS.forEach { h ->
                            RadioRow(if (h == 1) "1 hour" else "$h hours", interval == h) {
                                interval = h
                                settings.intervalHours = h
                                Sync.schedule(context, h)
                            }
                        }
                    }
                    SwitchRow("Only Samsung Health workouts", "Avoids duplicates from other apps", samsungOnly) {
                        samsungOnly = it
                        settings.samsungOnly = it
                    }
                }
            }

            // Data
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Data", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { confirm = "clear" }) { Text("Clear sync history") }
                    OutlinedButton(onClick = { confirm = "reset" }) { Text("Run setup again") }
                }
            }
        }
    }

    confirm?.let { which ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (which == "clear") "Clear sync history?" else "Run setup again?") },
            text = {
                Text(
                    if (which == "clear") {
                        "Removes the list on this phone only. Nothing is deleted from Devs Who Move, and re-imports won't create duplicates."
                    } else {
                        "Auto-sync stops until you finish setup. Your token and history are kept."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (which == "clear") {
                        SyncLog.get(context).clear()
                    } else {
                        Sync.cancel(context)
                        settings.onboarded = false
                        onResetSetup()
                    }
                    confirm = null
                }) { Text(if (which == "clear") "Clear" else "Continue") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
