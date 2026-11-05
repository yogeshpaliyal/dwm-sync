package com.yogeshpaliyal.dwmsync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = Settings(applicationContext)
        fun status(msg: String) {
            settings.lastStatus = "${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())} — $msg"
        }

        if (settings.token.isBlank()) {
            status("No token set"); return@withContext Result.failure()
        }
        if (HealthConnectClient.getSdkStatus(applicationContext) != HealthConnectClient.SDK_AVAILABLE) {
            status("Health Connect isn't available"); return@withContext Result.failure()
        }
        val client = HealthConnectClient.getOrCreate(applicationContext)
        if (!client.permissionController.getGrantedPermissions().containsAll(HealthPermissions.required)) {
            status("Health Connect permissions not granted"); return@withContext Result.failure()
        }

        val now = Instant.now()
        val from = if (settings.watermark == 0L) {
            now.minus(settings.backfillDays.toLong(), ChronoUnit.DAYS)
        } else {
            // 1-day overlap catches late-arriving syncs; Idempotency-Key makes repeats harmless.
            Instant.ofEpochMilli(settings.watermark).minus(1, ChronoUnit.DAYS)
        }

        val sessions = try {
            readSessions(client, from, now, settings.samsungOnly)
        } catch (e: SecurityException) {
            status("Health Connect blocked the read (background access off?). Open the app and tap Sync now.")
            return@withContext Result.failure()
        } catch (e: Exception) {
            status("Couldn't read Health Connect: ${e.message}")
            return@withContext Result.retry()
        }

        var uploaded = 0
        var skipped = 0
        for (s in sessions.sortedBy { it.end }) {
            if (s.durationMinutes < 1) { skipped++; continue }
            when (val r = DwmApi.postActivity(settings.baseUrl, settings.token, s)) {
                PostResult.Ok -> uploaded++
                PostResult.Conflict, is PostResult.Rejected -> skipped++
                PostResult.Unauthorized -> {
                    status("Token rejected — it may have expired (90 days) or been revoked. Paste a new one.")
                    return@withContext Result.failure()
                }
                is PostResult.Retry -> {
                    status("Paused after $uploaded uploads: ${r.reason}. Will retry.")
                    return@withContext Result.retry()
                }
            }
            if (s.end.toEpochMilli() > settings.watermark) settings.watermark = s.end.toEpochMilli()
        }

        status("Checked ${sessions.size} sessions · $uploaded sent · $skipped skipped")
        Result.success()
    }
}

object Sync {
    const val PERIODIC = "dwm-periodic-sync"
    const val NOW = "dwm-sync-now"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
