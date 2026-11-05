package com.yogeshpaliyal.dwmsync.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.yogeshpaliyal.dwmsync.AppLabels
import com.yogeshpaliyal.dwmsync.HealthPermissions
import com.yogeshpaliyal.dwmsync.SourceInfo
import com.yogeshpaliyal.dwmsync.discoverSources
import com.yogeshpaliyal.dwmsync.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// ---------- Health Connect state ----------

class HealthConnectState(
    val available: Boolean,
    val wanted: Set<String>,
    val granted: Set<String>,
    val request: () -> Unit,
) {
    val hasRequired get() = granted.containsAll(HealthPermissions.required)
    val hasAll get() = granted.containsAll(wanted)
    val hasHistory get() = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
}

@Composable
fun rememberHealthConnect(): HealthConnectState {
    val context = LocalContext.current
    val client = remember {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null
    }
    var wanted by remember { mutableStateOf(HealthPermissions.required) }
    var granted by remember { mutableStateOf(emptySet<String>()) }
    var refresh by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { refresh++ }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(client, refresh) {
        client?.let {
            wanted = HealthPermissions.toRequest(it)
            granted = it.permissionController.getGrantedPermissions()
        }
    }
    return HealthConnectState(client != null, wanted, granted) { launcher.launch(wanted) }
}

fun openHealthConnectInstall(context: Context) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun openHealthConnectSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
fun HealthConnectCard(hc: HealthConnectState) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            when {
                !hc.available -> {
                    Text("Health Connect isn't installed or needs an update.")
                    TextButton(onClick = { openHealthConnectInstall(context) }) { Text("Open Play Store") }
                }
                hc.hasAll -> {
                    Text("✓ All access granted")
                    TextButton(onClick = { openHealthConnectSettings(context) }) { Text("Manage in Health Connect") }
                }
                hc.hasRequired -> {
                    Text("✓ Exercise and distance access granted")
                    Text(
                        "Background or past-data access is off, so auto-sync or older imports may be limited.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = hc.request) { Text("Grant the rest") }
                }
                else -> {
                    Text("Needs read access to exercise sessions and distance.")
                    TextButton(onClick = hc.request) { Text("Grant access") }
                }
            }
        }
    }
}

// ---------- Source apps ----------

@Composable
fun AppIcon(pkg: String) {
    val context = LocalContext.current
    val bitmap = remember(pkg) { AppLabels.icon(context, pkg)?.toBitmap(96, 96)?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(36.dp))
    } else {
        Spacer(Modifier.size(36.dp))
    }
}

/**
 * Lists apps that write workouts to Health Connect. Checked ones are synced, and their order is
 * the priority used when two apps record the same workout.
 */
@Composable
fun SourcesCard(hc: HealthConnectState, selected: List<String>, onChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    var found by remember { mutableStateOf<List<SourceInfo>?>(null) }
    LaunchedEffect(hc.hasRequired) {
        if (hc.hasRequired) {
            found = runCatching { discoverSources(HealthConnectClient.getOrCreate(context)) }
                .getOrDefault(emptyList())
        }
    }
    val info = found.orEmpty().associateBy { it.packageName }
    val ordered = selected + found.orEmpty().map { it.packageName }.filterNot { it in selected }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Workout sources", style = MaterialTheme.typography.titleMedium)
            Text(
                "Apps that save workouts to Health Connect. Checked apps are synced. If two apps record " +
                    "the same workout, the one higher in the list is uploaded and the other is marked as a duplicate.",
                style = MaterialTheme.typography.bodySmall,
            )
            when {
                !hc.hasRequired -> Text("Grant Health Connect access to see your apps.")
                found == null -> CircularProgressIndicator(Modifier.size(24.dp))
                found!!.isEmpty() -> Text(
                    "No workouts found in Health Connect in the last 90 days.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ordered.forEach { pkg ->
                val idx = selected.indexOf(pkg)
                val isSelected = idx >= 0
                val src = info[pkg]
                val subtitle = buildList {
                    if (isSelected && selected.size > 1) add("Priority ${idx + 1}")
                    add(src?.let { "${it.workoutCount} workouts in 90 days" } ?: "No recent workouts")
                    src?.devices?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
                }.joinToString(" · ")

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked -> onChange(if (checked) selected + pkg else selected - pkg) },
                    )
                    AppIcon(pkg)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        val name = AppLabels.label(context, pkg)
                        Text(name)
                        if (name != pkg) {
                            Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                    if (isSelected && selected.size > 1) {
                        IconButton(
                            enabled = idx > 0,
                            onClick = { onChange(selected.toMutableList().apply { add(idx - 1, removeAt(idx)) }) },
                        ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                        IconButton(
                            enabled = idx < selected.size - 1,
                            onClick = { onChange(selected.toMutableList().apply { add(idx + 1, removeAt(idx)) }) },
                        ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                    }
                }
            }
            if (selected.isEmpty()) {
                Text("Pick at least one app.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ---------- Small widgets ----------

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------- Date window selection ----------

data class DateWindow(val from: Instant, val to: Instant) {
    val label get() = "${formatDate(from)} – ${formatDate(to.minusMillis(1))}"

    companion object {
        fun lastDays(days: Long): DateWindow {
            val now = Instant.now()
            return DateWindow(now.minus(days, ChronoUnit.DAYS), now)
        }

        /** Whole local days, inclusive of [end]. */
        fun ofDates(start: LocalDate, end: LocalDate): DateWindow {
            val zone = ZoneId.systemDefault()
            val to = minOf(end.plusDays(1).atStartOfDay(zone).toInstant(), Instant.now())
            return DateWindow(start.atStartOfDay(zone).toInstant(), to)
        }
    }
}

private data class RangeOption(val label: String, val days: Long?) // days == null -> custom

/**
 * Radio list of preset windows plus "Pick dates…".
 * [onChange] gets null for "only new workouts" (when [includeNone]).
 */
@Composable
fun RangeChooser(includeNone: Boolean, hasHistory: Boolean, onChange: (DateWindow?) -> Unit) {
    val options = remember(includeNone) {
        buildList {
            if (includeNone) add(RangeOption("Only new workouts from now on", 0))
            add(RangeOption("Last 7 days", 7))
            add(RangeOption("Last 30 days", 30))
            add(RangeOption("Last 90 days", 90))
            add(RangeOption("Last 12 months", 365))
            add(RangeOption("Pick dates…", null))
        }
    }
    var selected by remember { mutableIntStateOf(if (includeNone) 2 else 1) } // default: last 30 days
    var custom by remember { mutableStateOf<DateWindow?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    val option = options[selected]
    val window: DateWindow? = when {
        option.days == null -> custom
        option.days == 0L -> null
        else -> DateWindow.lastDays(option.days)
    }
    LaunchedEffect(selected, custom) { onChange(window) }

    Column {
        options.forEachIndexed { i, o ->
            val label = if (o.days == null && custom != null && selected == i) "Pick dates… (${custom!!.label})" else o.label
            RadioRow(label, selected == i) {
                selected = i
                if (o.days == null) showPicker = true
            }
        }
        val oldest = window?.from
        if (oldest != null && oldest.isBefore(Instant.now().minus(30, ChronoUnit.DAYS)) && !hasHistory) {
            Text(
                "Without past-data access, Health Connect only shares about the last 30 days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showPicker) {
        DateRangeDialog(
            onDismiss = {
                showPicker = false
                if (custom == null) selected = if (includeNone) 2 else 1
            },
            onConfirm = { custom = it; showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(onDismiss: () -> Unit, onConfirm: (DateWindow) -> Unit) {
    val state = rememberDateRangePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
        }
    )
    val start = state.selectedStartDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null,
                onClick = {
                    val s = Instant.ofEpochMilli(start!!).atZone(ZoneOffset.UTC).toLocalDate()
                    val e = state.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: s
                    onConfirm(DateWindow.ofDates(s, e))
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}
