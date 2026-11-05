package com.yogeshpaliyal.dwmsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.yogeshpaliyal.dwmsync.ui.DwmTheme
import com.yogeshpaliyal.dwmsync.ui.HomeScreen
import com.yogeshpaliyal.dwmsync.ui.ImportScreen
import com.yogeshpaliyal.dwmsync.ui.OnboardingScreen
import com.yogeshpaliyal.dwmsync.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DwmTheme {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

private enum class Screen { Home, Settings, Import }

@Composable
private fun App() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    var onboarded by remember { mutableStateOf(settings.onboarded) }
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    // Where "Import" was opened from, so Back returns there.
    var importFrom by rememberSaveable { mutableStateOf(Screen.Home) }

    if (!onboarded) {
        OnboardingScreen(onDone = { onboarded = true; screen = Screen.Home })
        return
    }

    // Make sure the periodic job exists (e.g. after an app update or a force-stop).
    LaunchedEffect(Unit) {
        if (settings.autoSync) Sync.schedule(context, settings.intervalHours)
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen == Screen.Import) importFrom else Screen.Home
    }

    when (screen) {
        Screen.Home -> HomeScreen(
            onOpenSettings = { screen = Screen.Settings },
            onImport = { importFrom = Screen.Home; screen = Screen.Import },
        )
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Home },
            onImport = { importFrom = Screen.Settings; screen = Screen.Import },
            onResetSetup = { onboarded = false },
        )
        Screen.Import -> ImportScreen(
            onBack = { screen = importFrom },
            onStarted = { screen = Screen.Home },
        )
    }
}
