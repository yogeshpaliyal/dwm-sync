package com.yogeshpaliyal.dwmsync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.yogeshpaliyal.dwmsync.AppLabels
import com.yogeshpaliyal.dwmsync.Settings
import com.yogeshpaliyal.dwmsync.Sync
import com.yogeshpaliyal.dwmsync.SyncEntry
import com.yogeshpaliyal.dwmsync.SyncLog
import com.yogeshpaliyal.dwmsync.SyncState
import com.yogeshpaliyal.dwmsync.formatDateTime
import com.yogeshpaliyal.dwmsync.formatDistance
import com.yogeshpaliyal.dwmsync.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onImport: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val entries by SyncLog.get(context).entries.collectAsStateWithLifecycle()
    val work by WorkManager.getInstance(context)
        .getWorkInfosFlow(WorkQuery.fromUniqueWorkNames(Sync.ALL))
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val running = work.any { it.state == WorkInfo.State.RUNNING }
    val periodic = work.firstOrNull { it.periodicityInfo != null && !it.state.isFinished }
    // Re-read persisted status whenever work state or the log changes.
    val status = remember(work, entries) { settings.lastStatus }
    val lastSyncAt = remember(work, entries) { settings.lastSyncAt }
    val sentCount = entries.count { it.state == SyncState.SENT }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DWM Sync") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Status", style = MaterialTheme.typography.titleMedium)
                        if (running) {
                            Text("Syncing…")
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            Text(status)
                        }
                        Text(
                            if (lastSyncAt > 0) "Last run: ${formatDateTime(lastSyncAt)}" else "Last run: never",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            when {
                                !settings.autoSync || periodic == null -> "Auto-sync: off"
                                periodic.nextScheduleTimeMillis in 1 until Long.MAX_VALUE ->
                                    "Auto-sync every ${settings.intervalHours} h · next around ${formatDateTime(periodic.nextScheduleTimeMillis)}"
                                else -> "Auto-sync every ${settings.intervalHours} h"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !running, onClick = { Sync.runNow(context) }) { Text("Sync now") }
                    OutlinedButton(enabled = !running, onClick = onImport) { Text("Import older data") }
                }
            }
            item {
                Text(
                    "Workouts · $sentCount synced",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "Nothing synced yet. New workouts appear here after Samsung Health saves them to Health Connect.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(entries, key = { it.hcId }) { entry ->
                EntryRow(entry)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EntryRow(e: SyncEntry) {
    val context = LocalContext.current
    val details = buildList {
        add(e.typeLabel)
        add(formatDuration(e.durationMin))
        e.distanceKm?.let { add(formatDistance(it)) }
    }.joinToString(" · ")
    val origin = remember(e.source, e.device) {
        listOfNotNull(e.source.takeIf { it.isNotBlank() }?.let { AppLabels.label(context, it) }, e.device)
            .joinToString(" · ")
    }
    val (label, color) = when (e.state) {
        SyncState.SENT -> "Synced" to MaterialTheme.colorScheme.primary
        SyncState.DUPLICATE -> "Duplicate" to Color.Gray
        SyncState.SKIPPED -> "Skipped" to Color.Gray
        SyncState.FAILED -> "Failed" to MaterialTheme.colorScheme.error
    }
    ListItem(
        headlineContent = { Text(e.title) },
        overlineContent = { Text(formatDateTime(e.startMillis)) },
        leadingContent = e.source.takeIf { it.isNotBlank() }?.let { { AppIcon(it) } },
        supportingContent = {
            Column {
                Text(details)
                if (origin.isNotEmpty()) Text(origin, style = MaterialTheme.typography.bodySmall)
                Text(
                    e.message ?: "Uploaded ${formatDateTime(e.syncedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        trailingContent = { Text(label, color = color, style = MaterialTheme.typography.labelMedium) },
    )
}
