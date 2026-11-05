package com.yogeshpaliyal.dwrsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import com.yogeshpaliyal.dwrsync.ui.DwrTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shown by Health Connect when the user asks why this app wants access. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DwrTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("How DWR Sync uses your data", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "DWR Sync reads only your exercise sessions and distance from Health Connect. " +
                                "For each workout it sends the title, sport, start time, duration and distance " +
                                "to the Devs Who Run site you configure, using your own agent token. " +
                                "Uploads are always private on the site until you publish them yourself."
                        )
                        Text(
                            "It does not read heart rate, sleep, location routes or any other health data, " +
                                "and it never writes to Health Connect. Nothing is sent anywhere else."
                        )
                        Button(onClick = { finish() }) { Text("OK") }
                    }
                }
            }
        }
    }
}
