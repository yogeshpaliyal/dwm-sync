package com.yogeshpaliyal.dwmsync.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon for a workout, from its Devs Who Run sport and the more specific Health Connect label. */
@Suppress("DEPRECATION")
fun sportIcon(sport: String, typeLabel: String): ImageVector = when (sport) {
    "Running" -> Icons.Filled.DirectionsRun
    "Cycling" -> Icons.Filled.DirectionsBike
    "Walking" -> Icons.Filled.DirectionsWalk
    "Tennis" -> Icons.Filled.SportsTennis
    "Swimming" -> Icons.Filled.Pool
    "Strength" -> Icons.Filled.FitnessCenter
    else -> when (typeLabel) {
        "Hike" -> Icons.Filled.Hiking
        "Yoga" -> Icons.Filled.SelfImprovement
        "Cricket" -> Icons.Filled.SportsCricket
        "Football" -> Icons.Filled.SportsSoccer
        "Badminton" -> Icons.Filled.SportsTennis
        else -> Icons.Filled.Timer
    }
}
