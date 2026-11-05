package com.yogeshpaliyal.dwmsync

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion as Ex
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant

const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"

object HealthPermissions {
    val required: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
    )

    /** Required + optional extras that this device's Health Connect actually supports. */
    @Suppress("RestrictedApi")
    fun toRequest(client: HealthConnectClient): Set<String> {
        val perms = required.toMutableSet()
        val features = client.features
        if (features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        ) perms += HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        if (features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        ) perms += HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        return perms
    }
}

data class Session(
    val id: String,
    val title: String,
    val sport: String,
    val typeLabel: String,
    val start: Instant,
    val end: Instant,
    val distanceKm: Double?,
    val source: String,
    val device: String?,
) {
    val durationMinutes: Int get() = Duration.between(start, end).toMinutes().toInt()
}

suspend fun readSessions(
    client: HealthConnectClient,
    from: Instant,
    to: Instant,
    sources: List<String>,
): List<Session> {
    // An empty dataOriginFilter means "all apps", so no sources selected must mean nothing.
    if (sources.isEmpty()) return emptyList()
    val origins = sources.map { DataOrigin(it) }.toSet()
    val out = mutableListOf<Session>()
    var pageToken: String? = null
    do {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to),
                dataOriginFilter = origins,
                ascendingOrder = true,
                pageSize = 200,
                pageToken = pageToken,
            )
        )
        for (r in response.records) {
            val distanceKm = client.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(r.startTime, r.endTime),
                    dataOriginFilter = setOf(r.metadata.dataOrigin),
                )
            )[DistanceRecord.DISTANCE_TOTAL]?.inKilometers

            val (sport, label) = mapSport(r.exerciseType)
            out += Session(
                id = r.metadata.id,
                title = r.title?.takeIf { it.isNotBlank() } ?: "$label session",
                sport = sport,
                typeLabel = label,
                start = r.startTime,
                end = r.endTime,
                distanceKm = distanceKm?.takeIf { it > 0.0 },
                source = r.metadata.dataOrigin.packageName,
                device = deviceLabel(r.metadata.device),
            )
        }
        pageToken = response.pageToken
    } while (!pageToken.isNullOrEmpty())
    return out
}

/** Health Connect exercise type -> (Devs Who Move sport, human label). */
fun mapSport(type: Int): Pair<String, String> = when (type) {
    Ex.EXERCISE_TYPE_RUNNING -> "Running" to "Run"
    Ex.EXERCISE_TYPE_RUNNING_TREADMILL -> "Running" to "Treadmill run"
    Ex.EXERCISE_TYPE_BIKING -> "Cycling" to "Ride"
    Ex.EXERCISE_TYPE_BIKING_STATIONARY -> "Cycling" to "Indoor ride"
    Ex.EXERCISE_TYPE_TENNIS -> "Tennis" to "Tennis"
    Ex.EXERCISE_TYPE_WALKING -> "Walking" to "Walk"
    Ex.EXERCISE_TYPE_SWIMMING_POOL -> "Swimming" to "Pool swim"
    Ex.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming" to "Open water swim"
    Ex.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength" to "Strength training"
    Ex.EXERCISE_TYPE_WEIGHTLIFTING -> "Strength" to "Weightlifting"
    Ex.EXERCISE_TYPE_HIKING -> "Other" to "Hike"
    Ex.EXERCISE_TYPE_YOGA -> "Other" to "Yoga"
    Ex.EXERCISE_TYPE_BADMINTON -> "Other" to "Badminton"
    Ex.EXERCISE_TYPE_CRICKET -> "Other" to "Cricket"
    Ex.EXERCISE_TYPE_SOCCER -> "Other" to "Football"
    Ex.EXERCISE_TYPE_ELLIPTICAL -> "Other" to "Elliptical"
    Ex.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "Other" to "HIIT"
    else -> "Other" to "Workout"
}
