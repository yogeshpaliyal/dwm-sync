package com.yogeshpaliyal.dwrsync

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

/** An app that has written exercise sessions to Health Connect. */
data class SourceInfo(
    val packageName: String,
    val workoutCount: Int,
    val devices: List<String>,
)

/**
 * Health Connect has no "list sources" API, so scan recent exercise sessions and
 * group them by the app that wrote them.
 */
suspend fun discoverSources(
    client: HealthConnectClient,
    days: Long = 90,
    maxRecords: Int = 3000,
): List<SourceInfo> {
    val now = Instant.now()
    val counts = mutableMapOf<String, Int>()
    val devices = mutableMapOf<String, MutableSet<String>>()
    var pageToken: String? = null
    var seen = 0
    do {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(days, ChronoUnit.DAYS), now),
                ascendingOrder = false,
                pageSize = 500,
                pageToken = pageToken,
            )
        )
        for (r in response.records) {
            val pkg = r.metadata.dataOrigin.packageName
            counts[pkg] = (counts[pkg] ?: 0) + 1
            deviceLabel(r.metadata.device)?.let { devices.getOrPut(pkg) { linkedSetOf() }.add(it) }
        }
        seen += response.records.size
        pageToken = response.pageToken
    } while (!pageToken.isNullOrEmpty() && seen < maxRecords)

    return counts.map { (pkg, n) -> SourceInfo(pkg, n, devices[pkg].orEmpty().take(3)) }
        .sortedByDescending { it.workoutCount }
}

/** Best-effort device name. Many apps leave this empty or only set the type. */
fun deviceLabel(device: Device?): String? {
    if (device == null) return null
    val name = listOfNotNull(device.manufacturer, device.model)
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" ")
    if (name.isNotEmpty()) return name
    return when (device.type) {
        Device.TYPE_WATCH -> "Watch"
        Device.TYPE_PHONE -> "Phone"
        Device.TYPE_RING -> "Ring"
        Device.TYPE_FITNESS_BAND -> "Fitness band"
        Device.TYPE_CHEST_STRAP -> "Chest strap"
        else -> null
    }
}

object AppLabels {
    private val known = mapOf(
        SAMSUNG_HEALTH_PACKAGE to "Samsung Health",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.google.android.apps.healthdata" to "Health Connect",
        "com.fitbit.FitbitMobile" to "Fitbit",
        "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.strava" to "Strava",
        "com.xiaomi.wearable" to "Mi Fitness",
        "com.huami.watch.hmwatchmanager" to "Zepp",
        "com.ouraring.oura" to "Oura",
        "com.nike.plusgps" to "Nike Run Club",
        "com.adidas.app" to "adidas Running",
    )

    fun label(context: Context, pkg: String): String {
        if (pkg.isBlank()) return "Unknown app"
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull() ?: known[pkg] ?: pkg
    }

    fun icon(context: Context, pkg: String): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
}

object Dedupe {
    /** True when two time ranges overlap by at least half of the shorter one. */
    fun overlaps(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean {
        val overlap = minOf(aEnd, bEnd) - maxOf(aStart, bStart)
        if (overlap <= 0) return false
        val shorter = minOf(aEnd - aStart, bEnd - bStart).coerceAtLeast(1)
        return overlap * 2 >= shorter
    }
}
