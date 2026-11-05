package com.yogeshpaliyal.dwrsync.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.dwrsync.R
import com.yogeshpaliyal.dwrsync.SAMSUNG_HEALTH_PACKAGE
import com.yogeshpaliyal.dwrsync.Settings
import com.yogeshpaliyal.dwrsync.Sync

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
    var urlError by remember { mutableStateOf<String?>(null) }
    var tokenError by remember { mutableStateOf<String?>(null) }
    var window by remember { mutableStateOf<DateWindow?>(null) }

    fun back() { step--; urlError = null; tokenError = null }
    BackHandler(enabled = step > 0) { back() }

    fun finish() {
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

    fun next() {
        when (step) {
            3 -> {
                urlError = validateUrl(url)
                tokenError = validateToken(token)
                if (urlError == null && tokenError == null) step++
            }
            STEPS - 1 -> finish()
            else -> step++
        }
    }

    val canContinue = when (step) {
        1 -> hc.hasRequired
        2 -> sources.isNotEmpty()
        else -> true
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        StepIndicator(step)

        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (s) {
                    0 -> {
                        AppLogo(Modifier.padding(start = 24.dp, top = 32.dp))
                        Heading(
                            "Your workouts, on Devs Who Run",
                            "DWR Sync picks up the workouts your fitness apps already record and uploads them for you. " +
                                "Uploads stay private until you publish them on the site.",
                        )
                        DataPath(Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                    }
                    1 -> {
                        Heading(
                            "Allow access to workouts",
                            "Health Connect asks which data DWR Sync may read. Allow background and past data too, " +
                                "so syncing works with the app closed and older workouts can be imported.",
                        )
                        HealthConnectStatus(hc, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    }
                    2 -> {
                        Heading(
                            "Choose which apps to sync",
                            "If two apps record the same workout, the one higher in the list is uploaded. " +
                                "Use the arrows to change the order.",
                        )
                        SourcesList(hc, sources) { sources = it }
                    }
                    3 -> {
                        Heading(
                            "Connect your account",
                            "On devswhorun.dev, open Your agents and create a token that can write activities. " +
                                "It's stored encrypted on this phone.",
                        )
                        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = url, onValueChange = { url = it; urlError = null },
                                label = { Text("Site address") }, singleLine = true,
                                isError = urlError != null, supportingText = urlError?.let { e -> @Composable { Text(e) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = token, onValueChange = { token = it; tokenError = null },
                                label = { Text("Agent token") }, singleLine = true,
                                isError = tokenError != null, supportingText = tokenError?.let { e -> @Composable { Text(e) } },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    4 -> {
                        Heading(
                            "Bring in past workouts?",
                            "New workouts sync automatically from now on. You can also import older ones later from the home screen.",
                        )
                        RangeChooser(includeNone = true, hasHistory = hc.hasHistory) { window = it }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) TextButton(onClick = ::back) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = ::next,
                enabled = canContinue,
                modifier = Modifier.heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    when (step) {
                        0 -> "Get started"
                        STEPS - 1 -> if (window == null) "Finish setup" else "Finish and import"
                        else -> "Continue"
                    },
                    Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(start = 24.dp, end = 24.dp, top = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Step ${step + 1} of $STEPS" },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(STEPS) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) colors.primary else colors.surfaceContainerHighest),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Step ${step + 1} of $STEPS", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The actual route the data takes, using the real app icons where the phone has them. */
@Composable
private fun DataPath(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        PathStop(
            icon = { AppIcon(SAMSUNG_HEALTH_PACKAGE, 40.dp) },
            title = "Your fitness apps",
            body = "Samsung Health, or any app that saves workouts to Health Connect",
            last = false,
        )
        PathStop(
            icon = { AppIcon(HEALTH_CONNECT_PACKAGE, 40.dp) },
            title = "Health Connect",
            body = "Keeps the workouts on your phone. DWR Sync only reads them.",
            last = false,
        )
        PathStop(
            icon = {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Filled.DirectionsRun, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
                }
            },
            title = "Devs Who Run",
            body = "Each workout arrives as a private session with its sport, time, duration and distance",
            last = true,
        )
    }
}

@Composable
private fun PathStop(icon: @Composable () -> Unit, title: String, body: String, last: Boolean) {
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            if (!last) {
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(2.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Column(Modifier.padding(start = 16.dp, top = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The launcher mark, drawn from the same vector as the app icon. */
@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF234D3C)),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "DWR Sync",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
