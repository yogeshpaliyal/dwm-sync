package com.yogeshpaliyal.dwmsync.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.dwmsync.BuildConfig
import com.yogeshpaliyal.dwmsync.Settings
import com.yogeshpaliyal.dwmsync.Sync
import com.yogeshpaliyal.dwmsync.SyncLog
import com.yogeshpaliyal.dwmsync.formatDate
import com.yogeshpaliyal.dwmsync.localDate
import com.yogeshpaliyal.dwmsync.plural
import java.time.LocalDate

private enum class Dialog { Url, Token, Interval, ClearHistory, RerunSetup }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onImport: () -> Unit, onResetSetup: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val hc = rememberHealthConnect()

    var url by remember { mutableStateOf(settings.baseUrl) }
    var tokenSavedAt by remember { mutableStateOf(settings.tokenSavedAt) }
    var autoSync by remember { mutableStateOf(settings.autoSync) }
    var interval by remember { mutableStateOf(settings.intervalHours) }
    var sources by remember { mutableStateOf(settings.sources) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SectionHeader("Account")
            PrefRow("Site address", url, onClick = { dialog = Dialog.Url })
            PrefRow("Agent token", tokenSummary(tokenSavedAt), onClick = { dialog = Dialog.Token })

            SectionHeader("Syncing")
            SwitchPref("Sync automatically", "Uploads new workouts in the background", autoSync) {
                autoSync = it
                settings.autoSync = it
                if (it) Sync.schedule(context, interval) else Sync.cancel(context)
            }
            if (autoSync) {
                PrefRow("Check for new workouts", "Every ${plural(interval, "hour", "hours")}", onClick = { dialog = Dialog.Interval })
            }
            PrefRow("Import older workouts", "Upload workouts from before setup", onClick = onImport)

            SectionHeader("Workout apps")
            Hint("Checked apps are synced. When two apps record the same workout, the higher one is uploaded.")
            SourcesList(hc, sources) {
                sources = it
                settings.sources = it
            }

            SectionHeader("Health Connect")
            HealthConnectStatus(hc, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

            SectionHeader("History on this phone")
            PrefRow("Clear sync history", "Empties the list here. Nothing is deleted from Devs Who Run.", onClick = { dialog = Dialog.ClearHistory })
            PrefRow("Run setup again", onClick = { dialog = Dialog.RerunSetup })

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            Text(
                "DWR Sync ${BuildConfig.VERSION_NAME}",
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    when (dialog) {
        Dialog.Url -> TextFieldDialog(
            title = "Site address",
            initial = url,
            label = "Address",
            onDismiss = { dialog = null },
        ) { value ->
            validateUrl(value) ?: run { settings.baseUrl = value; url = settings.baseUrl; null }
        }

        Dialog.Token -> TextFieldDialog(
            title = "Replace agent token",
            initial = "",
            label = "New token",
            help = "Create a token on devswhorun.dev under Your agents, with permission to write activities.",
            secret = true,
            onDismiss = { dialog = null },
        ) { value ->
            validateToken(value) ?: run { settings.token = value; tokenSavedAt = settings.tokenSavedAt; null }
        }

        Dialog.Interval -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Check for new workouts") },
            text = {
                Column {
                    Settings.INTERVAL_OPTIONS.forEach { h ->
                        RadioRow("Every ${plural(h, "hour", "hours")}", interval == h) {
                            interval = h
                            settings.intervalHours = h
                            Sync.schedule(context, h)
                            dialog = null
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
        )

        Dialog.ClearHistory -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Clear sync history?") },
            text = { Text("The list on this phone is emptied. Your workouts stay on Devs Who Run, and importing them again won't create duplicates there.") },
            confirmButton = {
                TextButton(onClick = { SyncLog.get(context).clear(); dialog = null }) { Text("Clear history") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )

        Dialog.RerunSetup -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Run setup again?") },
            text = { Text("Automatic sync pauses until you finish setup. Your token and history are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    Sync.cancel(context)
                    settings.onboarded = false
                    dialog = null
                    onResetSetup()
                }) { Text("Run setup") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )

        null -> Unit
    }
}

private fun tokenSummary(savedAt: Long): String {
    if (savedAt == 0L) return "Saved"
    val saved = localDate(savedAt)
    val expires = saved.plusDays(Settings.TOKEN_LIFETIME_DAYS)
    val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expires)
    return when {
        daysLeft < 0 -> "Expired around ${formatDate(expires)}. Tap to add a new one."
        daysLeft <= 14 -> "Expires around ${formatDate(expires)}, in ${plural(daysLeft.toInt(), "day", "days")}. Tap to replace."
        else -> "Saved ${formatDate(saved)}. Expires around ${formatDate(expires)}."
    }
}
