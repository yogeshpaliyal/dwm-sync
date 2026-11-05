package com.yogeshpaliyal.dwrsync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class SyncState { SENT, DUPLICATE, SKIPPED, FAILED }

data class SyncEntry(
    val hcId: String,
    val title: String,
    val sport: String,
    val typeLabel: String,
    val startMillis: Long,
    val endMillis: Long,
    val durationMin: Int,
    val distanceKm: Double?,
    val source: String,
    val device: String?,
    val state: SyncState,
    val message: String?,
    val syncedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("hcId", hcId); put("title", title); put("sport", sport); put("typeLabel", typeLabel)
        put("start", startMillis); put("end", endMillis); put("duration", durationMin)
        distanceKm?.let { put("distance", it) }
        put("source", source); device?.let { put("device", it) }
        put("state", state.name); message?.let { put("message", it) }; put("syncedAt", syncedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = SyncEntry(
            hcId = o.getString("hcId"),
            title = o.optString("title"),
            sport = o.optString("sport"),
            typeLabel = o.optString("typeLabel"),
            startMillis = o.getLong("start"),
            endMillis = o.optLong("end", o.getLong("start") + o.optInt("duration") * 60_000L),
            durationMin = o.optInt("duration"),
            distanceKm = if (o.has("distance")) o.getDouble("distance") else null,
            source = o.optString("source"),
            device = if (o.has("device")) o.getString("device") else null,
            state = runCatching { SyncState.valueOf(o.getString("state")) }.getOrDefault(SyncState.FAILED),
            message = if (o.has("message")) o.getString("message") else null,
            syncedAt = o.optLong("syncedAt"),
        )

        fun from(s: Session, state: SyncState, message: String? = null) = SyncEntry(
            hcId = s.id, title = s.title, sport = s.sport, typeLabel = s.typeLabel,
            startMillis = s.start.toEpochMilli(), endMillis = s.end.toEpochMilli(),
            durationMin = s.durationMinutes, distanceKm = s.distanceKm,
            source = s.source, device = s.device, state = state, message = message,
            syncedAt = System.currentTimeMillis(),
        )
    }
}

/** History of every workout the app has handled, newest first. Stored as a small JSON file. */
class SyncLog private constructor(private val file: File) {

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<SyncEntry>> = _entries.asStateFlow()

    /** Workouts that need no further action: uploaded, or recognised as a duplicate. */
    fun handledIds(): Set<String> = _entries.value
        .filter { it.state == SyncState.SENT || it.state == SyncState.DUPLICATE }
        .map { it.hcId }.toSet()

    fun sent(): List<SyncEntry> = _entries.value.filter { it.state == SyncState.SENT }

    @Synchronized
    fun record(entry: SyncEntry) {
        val updated = (_entries.value.filterNot { it.hcId == entry.hcId } + entry)
            .sortedByDescending { it.startMillis }
            .take(MAX_ENTRIES)
        _entries.value = updated
        save(updated)
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        file.delete()
    }

    private fun load(): List<SyncEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        List(arr.length()) { SyncEntry.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun save(list: List<SyncEntry>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
        tmp.renameTo(file)
    }

    companion object {
        private const val MAX_ENTRIES = 2000

        @Volatile private var instance: SyncLog? = null

        fun get(context: Context): SyncLog = instance ?: synchronized(this) {
            instance ?: SyncLog(File(context.applicationContext.filesDir, "sync_log.json")).also { instance = it }
        }
    }
}
