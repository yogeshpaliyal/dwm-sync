package com.yogeshpaliyal.dwmsync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class SyncState { SENT, SKIPPED, FAILED }

data class SyncEntry(
    val hcId: String,
    val title: String,
    val sport: String,
    val typeLabel: String,
    val startMillis: Long,
    val durationMin: Int,
    val distanceKm: Double?,
    val state: SyncState,
    val message: String?,
    val syncedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("hcId", hcId); put("title", title); put("sport", sport); put("typeLabel", typeLabel)
        put("start", startMillis); put("duration", durationMin)
        distanceKm?.let { put("distance", it) }
        put("state", state.name); message?.let { put("message", it) }; put("syncedAt", syncedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = SyncEntry(
            hcId = o.getString("hcId"),
            title = o.optString("title"),
            sport = o.optString("sport"),
            typeLabel = o.optString("typeLabel"),
            startMillis = o.getLong("start"),
            durationMin = o.optInt("duration"),
            distanceKm = if (o.has("distance")) o.getDouble("distance") else null,
            state = runCatching { SyncState.valueOf(o.getString("state")) }.getOrDefault(SyncState.FAILED),
            message = if (o.has("message")) o.getString("message") else null,
            syncedAt = o.optLong("syncedAt"),
        )

        fun from(s: Session, state: SyncState, message: String? = null) = SyncEntry(
            hcId = s.id, title = s.title, sport = s.sport, typeLabel = s.typeLabel,
            startMillis = s.start.toEpochMilli(), durationMin = s.durationMinutes,
            distanceKm = s.distanceKm, state = state, message = message,
            syncedAt = System.currentTimeMillis(),
        )
    }
}

/** History of every workout the app has handled, newest first. Stored as a small JSON file. */
class SyncLog private constructor(private val file: File) {

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<SyncEntry>> = _entries.asStateFlow()

    fun sentIds(): Set<String> = _entries.value.filter { it.state == SyncState.SENT }.map { it.hcId }.toSet()

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
