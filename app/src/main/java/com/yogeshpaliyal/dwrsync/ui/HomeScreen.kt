package com.yogeshpaliyal.dwrsync.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.yogeshpaliyal.dwrsync.AppLabels
import com.yogeshpaliyal.dwrsync.Settings
import com.yogeshpaliyal.dwrsync.Sync
import com.yogeshpaliyal.dwrsync.SyncEntry
import com.yogeshpaliyal.dwrsync.SyncLog
import com.yogeshpaliyal.dwrsync.SyncState
import com.yogeshpaliyal.dwrsync.formatDateTime
import com.yogeshpaliyal.dwrsync.formatDayHeader
import com.yogeshpaliyal.dwrsync.formatDistance
import com.yogeshpaliyal.dwrsync.formatDuration
import com.yogeshpaliyal.dwrsync.formatRelative
import com.yogeshpaliyal.dwrsync.formatTime
import com.yogeshpaliyal.dwrsync.localDate
import com.yogeshpaliyal.dwrsync.plural
import com.yogeshpaliyal.dwrsync.zone
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle
import java.util.Locale

private data class Snapshot(
    val message: String,
    val ok: Boolean,
    val lastRunAt: Long,
    val autoSync: Boolean,
    val intervalHours: Int,
)

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
    val snapshot = remember(work, entries) {
        Snapshot(settings.lastStatus, settings.lastOk, settings.lastSyncAt, settings.autoSync, settings.intervalHours)
    }
    val days = remember(entries) { entries.groupBy { localDate(it.startMillis) }.toList() }

    var menuOpen by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<SyncEntry?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("DWR Sync") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Import older workouts") },
                                onClick = { menuOpen = false; onImport() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!running) Sync.runNow(context) },
                icon = {
                    if (running) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    } else {
                        Icon(Icons.Filled.Sync, contentDescription = null)
                    }
                },
                text = { Text(if (running) "Syncing" else "Sync now") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp, // room for the FAB
            ),
        ) {
            item(key = "status") { StatusArea(snapshot, periodic, running, onOpenSettings) }
            item(key = "week") { WeekStrip(entries) }

            if (entries.isEmpty()) {
                item(key = "empty") { EmptyLog(onImport) }
            } else {
                days.forEach { (day, list) ->
                    item(key = "day-$day") { DayHeader(day) }
                    items(list, key = { it.hcId }) { e -> WorkoutRow(e) { details = e } }
                }
            }
        }
    }

    details?.let { WorkoutDetails(it) { details = null } }
}

// ---------- Status ----------

@Composable
private fun StatusArea(s: Snapshot, periodic: WorkInfo?, running: Boolean, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (!s.ok && !running) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(colors.errorContainer)
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.onErrorContainer)
            Column(Modifier.padding(start = 12.dp)) {
                Text("Sync didn't finish", style = MaterialTheme.typography.titleSmall, color = colors.onErrorContainer)
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = colors.onErrorContainer)
                TextButton(onClick = onOpenSettings) { Text("Open settings", color = colors.onErrorContainer) }
            }
        }
        return
    }

    val first = when {
        running -> "Checking Health Connect for new workouts"
        s.lastRunAt == 0L -> "Not synced yet"
        else -> "Last synced ${formatRelative(s.lastRunAt)}. ${s.message}."
    }
    val next = periodic?.nextScheduleTimeMillis?.takeIf { it in 1 until Long.MAX_VALUE }
    val second = when {
        !s.autoSync || periodic == null -> "Automatic sync is off"
        next != null && next > System.currentTimeMillis() -> "Next automatic check around ${formatNext(next)}"
        else -> "Checks automatically every ${plural(s.intervalHours, "hour", "hours")}"
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(first, style = MaterialTheme.typography.bodyMedium)
        Text(second, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

private fun formatNext(millis: Long): String =
    if (DateUtils.isToday(millis)) formatTime(millis) else "tomorrow ${formatTime(millis)}"

// ---------- Last 7 days ----------

@Composable
private fun WeekStrip(entries: List<SyncEntry>) {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now(zone)
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = entries.filter { it.state == SyncState.SENT }.groupBy { localDate(it.startMillis) }
    val minutes = week.map { d -> byDay[d].orEmpty().sumOf { it.durationMin } }
    val workouts = week.flatMap { byDay[it].orEmpty() }
    val total = minutes.sum()
    val km = workouts.sumOf { it.distanceKm ?: 0.0 }
    val max = (minutes.maxOrNull() ?: 0).coerceAtLeast(1)

    val summary = if (total == 0) {
        "Nothing synced in the last 7 days"
    } else buildString {
        append(formatDuration(total)).append(" over ").append(plural(workouts.size, "workout", "workouts"))
        if (km > 0) append(", ").append(formatDistance(km))
    }

    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .padding(20.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Last 7 days: $summary" },
    ) {
        Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
        Text(summary, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = colors.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().height(104.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            week.forEachIndexed { i, day ->
                val m = minutes[i]
                val isToday = day == today
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(if (m == 0) 0.04f else (m.toFloat() / max).coerceAtLeast(0.08f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(
                                    when {
                                        m == 0 -> colors.outlineVariant
                                        isToday -> colors.primary
                                        else -> colors.secondary.copy(alpha = 0.55f)
                                    }
                                ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        day.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) colors.onSurface else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------- Log ----------

@Composable
private fun DayHeader(day: LocalDate) {
    Text(
        formatDayHeader(day),
        Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WorkoutRow(e: SyncEntry, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val counted = e.state == SyncState.SENT || e.state == SyncState.FAILED
    val stats = buildString {
        append(formatDuration(e.durationMin))
        e.distanceKm?.let { append("   ").append(formatDistance(it)) }
    }
    val note = when (e.state) {
        SyncState.SENT -> null
        SyncState.DUPLICATE -> e.message ?: "Duplicate, not uploaded"
        SyncState.SKIPPED -> "Not uploaded: ${e.message ?: "skipped"}"
        SyncState.FAILED -> "Upload failed: ${e.message ?: "unknown error"}"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .alpha(if (counted) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(sportIcon(e.sport, e.typeLabel), contentDescription = e.typeLabel,
                tint = colors.onSecondaryContainer, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stats, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = colors.onSurfaceVariant)
            note?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (e.state == SyncState.FAILED) colors.error else colors.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
            Text(formatTime(e.startMillis), style = MaterialTheme.typography.labelMedium.merge(Numbers), color = colors.onSurfaceVariant)
            if (e.source.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                AppIcon(e.source, size = 18.dp)
            }
        }
    }
}

@Composable
private fun EmptyLog(onImport: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 32.dp)) {
        Text("No workouts yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Workouts show up here after your apps save them to Health Connect and a sync runs. " +
                "To bring in workouts from before setup, import them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onImport) { Text("Import older workouts") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDetails(e: SyncEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(sportIcon(e.sport, e.typeLabel), contentDescription = null, tint = colors.onSecondaryContainer)
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(e.title, style = MaterialTheme.typography.headlineSmall)
                    Text("${e.typeLabel}, uploaded as ${e.sport}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
            Detail("Started", formatDateTime(e.startMillis))
            Detail("Duration", formatDuration(e.durationMin))
            e.distanceKm?.let { Detail("Distance", formatDistance(it)) }
            if (e.source.isNotBlank()) {
                val name = AppLabels.label(context, e.source)
                Detail("Recorded by", listOfNotNull(name, e.device?.let { "on $it" }).joinToString(" "))
            }
            Detail(
                "Status",
                when (e.state) {
                    SyncState.SENT -> "Uploaded ${formatDateTime(e.syncedAt)}, private on Devs Who Run"
                    SyncState.DUPLICATE -> e.message ?: "Duplicate, not uploaded"
                    SyncState.SKIPPED -> "Not uploaded: ${e.message ?: "skipped"}"
                    SyncState.FAILED -> "Upload failed: ${e.message ?: "unknown error"}. It will be tried again on the next sync."
                },
            )
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, Modifier.width(108.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.merge(Numbers))
    }
}
