package com.yogeshpaliyal.dwmsync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.yogeshpaliyal.dwmsync.Settings
import com.yogeshpaliyal.dwmsync.Sync

private const val STEPS = 5

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val hc = rememberHealthConnect()

    var step by rememberSaveable { mutableStateOf(0) }
    var url by rememberSaveable { mutableStateOf(settings.baseUrl) }
    var token by rememberSaveable { mutableStateOf(settings.token) }
    var sources by remember { mutableStateOf(settings.sources) }
    var error by remember { mutableStateOf<String?>(null) }
    var window by remember { mutableStateOf<DateWindow?>(null) }

    BackHandler(enabled = step > 0) { step--; error = null }

    Column(
        Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LinearProgressIndicator(progress = { (step + 1) / STEPS.toFloat() }, modifier = Modifier.fillMaxWidth())

        when (step) {
            0 -> {
                Text("Sync workouts to Devs Who Move", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "This app reads workouts that Samsung Health saves to Health Connect and uploads them " +
                        "to your Devs Who Move account in the background. Uploads are always private; " +
                        "you choose what to publish on the site."
                )
                Text("Setup takes about a minute.", style = MaterialTheme.typography.bodySmall)
            }
            1 -> {
                Text("Allow Health Connect access", style = MaterialTheme.typography.headlineSmall)
                Text("Only exercise sessions and distance are read. Nothing is written back.")
                HealthConnectCard(hc)
            }
            2 -> {
                Text("Choose workout sources", style = MaterialTheme.typography.headlineSmall)
                Text("Pick the apps whose workouts should go to Devs Who Move.")
                SourcesCard(hc, sources) { sources = it }
            }
            3 -> {
                Text("Connect your account", style = MaterialTheme.typography.headlineSmall)
                Text("On the site, open Your agents and create a token with activities:write.")
                OutlinedTextField(
                    value = url, onValueChange = { url = it; error = null },
                    label = { Text("Site URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it; error = null },
                    label = { Text("Agent token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            4 -> {
                Text("Import older workouts?", style = MaterialTheme.typography.headlineSmall)
                Text("After this, new workouts sync automatically. You can import more later from the home screen.")
                RangeChooser(includeNone = true, hasHistory = hc.hasHistory) { window = it }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(Modifier.fillMaxWidth()) {
            if (step > 0) TextButton(onClick = { step--; error = null }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = (step != 1 || hc.hasRequired) && (step != 2 || sources.isNotEmpty()),
                onClick = {
                    when (step) {
                        3 -> when {
                            !url.trim().startsWith("https://") -> error = "Site URL must start with https://"
                            token.isBlank() -> error = "Paste your agent token"
                            else -> step++
                        }
                        4 -> {
                            settings.baseUrl = url
                            settings.token = token
                            settings.sources = sources
                            settings.autoSync = true
                            val now = System.currentTimeMillis()
                            settings.syncFloor = now
                            settings.watermark = now
                            Sync.schedule(context, settings.intervalHours)
                            window?.let { Sync.import(context, it.from, it.to) }
                            settings.onboarded = true
                            onDone()
                        }
                        else -> step++
                    }
                },
            ) { Text(if (step == STEPS - 1) "Finish" else "Next") }
        }
    }
}
