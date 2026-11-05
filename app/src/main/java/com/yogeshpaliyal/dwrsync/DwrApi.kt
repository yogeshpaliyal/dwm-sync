package com.yogeshpaliyal.dwrsync

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

sealed interface PostResult {
    /** 2xx — created, or an idempotent replay of an earlier upload. */
    data object Ok : PostResult
    /** 409 — same Idempotency-Key, different payload (e.g. session edited later). Skip it. */
    data object Conflict : PostResult
    /** 401/403 — token missing, expired (90 days) or revoked. Stop syncing. */
    data object Unauthorized : PostResult
    /** 4xx validation error — this one session can't be uploaded. Skip it. */
    data class Rejected(val code: Int, val body: String) : PostResult
    /** Network error, 429 or 5xx — try again later. */
    data class Retry(val reason: String) : PostResult
}

object DwrApi {

    fun postActivity(baseUrl: String, token: String, s: Session): PostResult {
        val body = JSONObject().apply {
            put("title", s.title.take(120))
            put("sport", s.sport)
            put("occurredAt", DateTimeFormatter.ISO_INSTANT.format(s.start))
            put("duration", s.durationMinutes)
            s.distanceKm?.let { put("distance", (it * 100).roundToInt() / 100.0) }
            put("notes", "${s.typeLabel} · synced from Health Connect")
            put("visibility", "private")
        }.toString().toByteArray(Charsets.UTF_8)

        val conn = try {
            (URL("$baseUrl/api/activities").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Idempotency-Key", "hc-${s.id}")
                setRequestProperty("User-Agent", "DwrSync-Android/1.0")
                setFixedLengthStreamingMode(body.size)
            }
        } catch (e: IOException) {
            return PostResult.Retry(e.message ?: "connection failed")
        }

        return try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            when {
                code in 200..299 -> PostResult.Ok
                code == 409 -> PostResult.Conflict
                code == 401 || code == 403 -> PostResult.Unauthorized
                code == 429 || code >= 500 -> PostResult.Retry("HTTP $code")
                else -> PostResult.Rejected(code, conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(300))
            }
        } catch (e: IOException) {
            PostResult.Retry(e.message ?: "network error")
        } finally {
            conn.disconnect()
        }
    }
}
