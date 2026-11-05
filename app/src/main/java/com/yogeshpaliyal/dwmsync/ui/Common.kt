package com.yogeshpaliyal.dwmsync.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
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
import com.yogeshpaliyal.dwmsync.plural
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
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun openHealthConnectSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Icon + explanation + the one action that moves the user forward. */
@Composable
fun HealthConnectStatus(hc: HealthConnectState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        when {
            !hc.available -> {
                StatusLine(Icons.Outlined.ErrorOutline, colors.error, "Health Connect isn't available",
                    "Install or update Health Connect from the Play Store, then come back here.")
                ActionRow { Button(onClick = { openHealthConnectInstall(context) }) { Text("Open Play Store") } }
            }
            hc.hasAll -> {
                StatusLine(Icons.Filled.CheckCircle, colors.primary, "Access allowed",
                    "DWR Sync can read workouts and distance, including in the background and from the past.")
                ActionRow { TextButton(onClick = { openHealthConnectSettings(context) }) { Text("Manage in Health Connect") } }
            }
            hc.hasRequired -> {
                StatusLine(Icons.Outlined.Info, colors.tertiary, "Partly allowed",
                    "Background or past-data access is off, so automatic sync or older imports may miss workouts.")
                ActionRow { FilledTonalButton(onClick = hc.request) { Text("Allow the rest") } }
            }
            else -> {
                StatusLine(Icons.Outlined.Lock, colors.onSurfaceVariant, "Access needed",
                    "DWR Sync only reads workouts and distance. It never writes to Health Connect.")
                ActionRow { Button(onClick = hc.request) { Text("Allow access") } }
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(22.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Row(Modifier.padding(start = 38.dp, top = 8.dp)) { content() }
}

// ---------- Source apps ----------

@Composable
fun AppIcon(pkg: String, size: Dp = 36.dp) {
    val context = LocalContext.current
    val bitmap = remember(pkg) { AppLabels.icon(context, pkg)?.toBitmap(128, 128)?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Icon(
            Icons.Outlined.Apps, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size),
        )
    }
}

/**
 * Apps that write workouts to Health Connect. Checked ones are synced, and their order is the
 * priority used when two apps record the same workout.
 */
@Composable
fun SourcesList(hc: HealthConnectState, selected: List<String>, onChange: (List<String>) -> Unit) {
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

    Column {
        when {
            !hc.hasRequired -> Hint("Allow Health Connect access to see which apps have workouts.")
            found == null -> Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Looking for apps with workouts", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            found!!.isEmpty() -> Hint("No app has saved a workout to Health Connect in the last 90 days.")
        }

        ordered.forEach { pkg ->
            val idx = selected.indexOf(pkg)
            val isSelected = idx >= 0
            val src = info[pkg]
            val name = AppLabels.label(context, pkg)

            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = isSelected, role = Role.Checkbox,
                        onValueChange = { checked -> onChange(if (checked) selected + pkg else selected - pkg) },
                    )
                    .heightIn(min = 72.dp)
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
                AppIcon(pkg)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (name != pkg) {
                        Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        src?.let { "${plural(it.workoutCount, "workout", "workouts")} in the last 90 days" }
                            ?: "No recent workouts",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    src?.devices?.takeIf { it.isNotEmpty() }?.let {
                        Text("Recorded on ${it.joinToString(", ")}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (isSelected && selected.size > 1) {
                    IconButton(
                        enabled = idx > 0,
                        onClick = { onChange(selected.toMutableList().apply { add(idx - 1, removeAt(idx)) }) },
                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move $name up") }
                    IconButton(
                        enabled = idx < selected.size - 1,
                        onClick = { onChange(selected.toMutableList().apply { add(idx + 1, removeAt(idx)) }) },
                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move $name down") }
                }
            }
        }
        if (selected.isEmpty()) {
            Text(
                "Choose at least one app.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ---------- List building blocks ----------

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun Hint(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun PrefRow(
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.let { Spacer(Modifier.size(16.dp)); it() }
    }
}

@Composable
fun SwitchPref(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, supporting: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Single-field edit dialog. [onSave] returns an error message, or null to close. */
@Composable
fun TextFieldDialog(
    title: String,
    initial: String,
    label: String,
    help: String? = null,
    secret: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> String?,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                help?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                }
                val currentError = error
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text(label) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = currentError?.let { e -> @Composable { Text(e) } },
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = onSave(value)
                if (e == null) onDismiss() else error = e
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun validateUrl(url: String): String? =
    if (!url.trim().startsWith("https://")) "Use the full address, starting with https://" else null

fun validateToken(token: String): String? =
    if (token.isBlank()) "Paste the token from Your agents on the site" else null

// ---------- Date window selection ----------

data class DateWindow(val from: Instant, val to: Instant) {
    val label get() = "${formatDate(from)} to ${formatDate(to.minusMillis(1))}"

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

private data class RangeOption(val label: String, val days: Long?) // null = custom, 0 = none

/** Preset windows plus a date picker. [onChange] gets null for "only new workouts". */
@Composable
fun RangeChooser(includeNone: Boolean, hasHistory: Boolean, onChange: (DateWindow?) -> Unit) {
    val options = remember(includeNone) {
        buildList {
            if (includeNone) add(RangeOption("No, only new workouts", 0))
            add(RangeOption("Last 7 days", 7))
            add(RangeOption("Last 30 days", 30))
            add(RangeOption("Last 90 days", 90))
            add(RangeOption("Last 12 months", 365))
            add(RangeOption("Choose dates", null))
        }
    }
    val defaultIndex = if (includeNone) 2 else 1 // last 30 days
    var selected by remember { mutableIntStateOf(defaultIndex) }
    var custom by remember { mutableStateOf<DateWindow?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    val option = options[selected]
    val window: DateWindow? = when (option.days) {
        null -> custom
        0L -> null
        else -> DateWindow.lastDays(option.days)
    }
    LaunchedEffect(selected, custom) { onChange(window) }

    Column {
        options.forEachIndexed { i, o ->
            RadioRow(
                label = o.label,
                selected = selected == i,
                supporting = if (o.days == null) custom?.label ?: "Pick a start and end date" else null,
            ) {
                selected = i
                if (o.days == null) showPicker = true
            }
        }
        val oldest = window?.from
        if (oldest != null && oldest.isBefore(Instant.now().minus(30, ChronoUnit.DAYS)) && !hasHistory) {
            Text(
                "Past-data access is off, so Health Connect will only share about the last 30 days.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showPicker) {
        DateRangeDialog(
            onDismiss = {
                showPicker = false
                if (custom == null) selected = defaultIndex
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
            ) { Text("Use these dates") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = { Text("Workouts from", Modifier.padding(PaddingValues(start = 24.dp, end = 12.dp, top = 16.dp))) },
        )
    }
}
