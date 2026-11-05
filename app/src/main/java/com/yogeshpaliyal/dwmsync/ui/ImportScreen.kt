package com.yogeshpaliyal.dwmsync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.dwmsync.Sync

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onBack: () -> Unit, onStarted: () -> Unit) {
    val context = LocalContext.current
    val hc = rememberHealthConnect()
    var window by remember { mutableStateOf<DateWindow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import older data") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Pick which workouts to upload. Ones already synced are skipped, so overlapping ranges are safe.",
                style = MaterialTheme.typography.bodyMedium,
            )
            RangeChooser(includeNone = false, hasHistory = hc.hasHistory) { window = it }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = window != null && hc.hasRequired,
                onClick = {
                    window?.let { Sync.import(context, it.from, it.to) }
                    onStarted()
                },
            ) { Text(window?.let { "Import ${it.label}" } ?: "Import") }
        }
    }
}
