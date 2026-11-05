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
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** Runs incremental auto-sync, or a one-off import when given a date range. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val from = inputData.getLong(KEY_FROM, -1L)
        val to = inputData.getLong(KEY_TO, -1L)
        val range = if (from > 0 && to > from) Instant.ofEpochMilli(from) to Instant.ofEpochMilli(to) else null
        when (SyncEngine(applicationContext).run(range)) {
            SyncEngine.Outcome.DONE -> Result.success()
            SyncEngine.Outcome.RETRY -> Result.retry()
            SyncEngine.Outcome.FATAL -> Result.failure()
        }
    }

    companion object {
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
    }
}

class SyncEngine(private val context: Context) {

    enum class Outcome { DONE, RETRY, FATAL }

    private val settings = Settings(context)
    private val log = SyncLog.get(context)

    /** @param range null = incremental auto-sync; otherwise import exactly that window. */
    suspend fun run(range: Pair<Instant, Instant>?): Outcome {
        val prefix = if (range == null) "" else "Import of ${formatDate(range.first)} to ${formatDate(range.second.minusMillis(1))}: "
        fun finish(msg: String, outcome: Outcome): Outcome {
            settings.lastStatus = prefix + msg
            settings.lastOk = outcome == Outcome.DONE
            settings.lastSyncAt = System.currentTimeMillis()
            return outcome
        }

        if (settings.token.isBlank()) return finish("No agent token is saved. Add one in Settings.", Outcome.FATAL)
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return finish("Health Connect isn't available on this phone. Install or update it from the Play Store.", Outcome.FATAL)
        }
        val client = HealthConnectClient.getOrCreate(context)
        if (!client.permissionController.getGrantedPermissions().containsAll(HealthPermissions.required)) {
            return finish("Health Connect access was removed. Allow it again in Settings.", Outcome.FATAL)
        }

        if (settings.sources.isEmpty()) return finish("No workout apps are selected. Choose at least one in Settings.", Outcome.FATAL)

        val now = Instant.now()
        val (from, to) = range ?: run {
            // 1-day overlap catches late-arriving Samsung Health syncs; repeats are skipped / idempotent.
            val wm = settings.watermark
            val start = if (wm == 0L) now.minus(1, ChronoUnit.DAYS) else Instant.ofEpochMilli(wm).minus(1, ChronoUnit.DAYS)
            maxOf(start, Instant.ofEpochMilli(settings.syncFloor)) to now
        }

        val sessions = try {
            readSessions(client, from, minOf(to, now), settings.sources)
        } catch (e: SecurityException) {
            return finish("Health Connect blocked a background read. Allow background access in Settings, or tap Sync now with the app open.", Outcome.FATAL)
        } catch (e: Exception) {
            return finish("Couldn't read Health Connect (${e.message}). Trying again later.", Outcome.RETRY)
        }

        val handled = log.handledIds()
        val sentEntries = log.sent().toMutableList()
        val priority = settings.sources.withIndex().associate { (i, pkg) -> pkg to i }
        fun rank(pkg: String) = priority[pkg] ?: Int.MAX_VALUE
        fun sameWorkout(sport: String, start: Long, end: Long, s: Session) =
            sport == s.sport && Dedupe.overlaps(start, end, s.start.toEpochMilli(), s.end.toEpochMilli())

        var sent = 0
        var skipped = 0
        var failed = 0
        var dupes = 0
        fun advance(s: Session) {
            if (range == null && s.end.toEpochMilli() > settings.watermark) settings.watermark = s.end.toEpochMilli()
        }

        for (s in sessions.sortedBy { it.end }) {
            if (s.id in handled) { advance(s); continue }
            if (s.durationMinutes < 1) {
                log.record(SyncEntry.from(s, SyncState.SKIPPED, "Shorter than a minute"))
                skipped++; advance(s); continue
            }

            // Same workout from another app: already uploaded, or a higher-priority copy is in this batch.
            val dupOf = sentEntries.firstOrNull { e ->
                e.source != s.source && sameWorkout(e.sport, e.startMillis, e.endMillis, s)
            }?.let { it.title to it.source }
                ?: sessions.firstOrNull { t ->
                    t.id != s.id && t.source != s.source && rank(t.source) < rank(s.source) &&
                        sameWorkout(t.sport, t.start.toEpochMilli(), t.end.toEpochMilli(), s)
                }?.let { it.title to it.source }
            if (dupOf != null) {
                val msg = "Duplicate of \"${dupOf.first}\" from ${AppLabels.label(context, dupOf.second)}"
                log.record(SyncEntry.from(s, SyncState.DUPLICATE, msg))
                dupes++; advance(s); continue
            }

            when (val r = DwmApi.postActivity(settings.baseUrl, settings.token, s)) {
                PostResult.Ok -> {
                    val entry = SyncEntry.from(s, SyncState.SENT)
                    log.record(entry); sentEntries += entry; sent++
                }
                PostResult.Conflict -> {
                    log.record(SyncEntry.from(s, SyncState.SKIPPED, "Changed after it was uploaded (409)"))
                    skipped++
                }
                is PostResult.Rejected -> {
                    log.record(SyncEntry.from(s, SyncState.FAILED, "HTTP ${r.code}: ${r.body}".trim()))
                    failed++
                }
                PostResult.Unauthorized -> return finish(
                    "Devs Who Move rejected the token. It may have expired or been revoked. Add a new one in Settings.",
                    Outcome.FATAL,
                )
                is PostResult.Retry -> return finish("Stopped after ${plural(sent, "upload", "uploads")} (${r.reason}). Trying again later.", Outcome.RETRY)
            }
            advance(s)
        }

        val newOnes = sent + skipped + failed + dupes
        return finish(
            if (newOnes == 0) "Nothing new"
            else listOfNotNull(
                plural(sent, "workout uploaded", "workouts uploaded"),
                dupes.takeIf { it > 0 }?.let { plural(it, "duplicate", "duplicates") },
                skipped.takeIf { it > 0 }?.let { "$it skipped" },
                failed.takeIf { it > 0 }?.let { "$it failed" },
            ).joinToString(", "),
            Outcome.DONE,
        )
    }
}

object Sync {
    const val PERIODIC = "dwm-periodic-sync"
    const val NOW = "dwm-sync-now"
    const val IMPORT = "dwm-import"
    val ALL = listOf(PERIODIC, NOW, IMPORT)

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context, hours: Int) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun import(context: Context, from: Instant, to: Instant) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(workDataOf(SyncWorker.KEY_FROM to from.toEpochMilli(), SyncWorker.KEY_TO to to.toEpochMilli()))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMPORT, ExistingWorkPolicy.REPLACE, request)
    }
}
