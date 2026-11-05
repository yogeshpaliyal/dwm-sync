package com.yogeshpaliyal.dwmsync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted on-device settings. The token never leaves the device except in the Authorization header. */
class Settings(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "dwm_sync_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** First-run setup finished. */
    var onboarded: Boolean
        get() = prefs.getBoolean(K_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(K_ONBOARDED, v).apply()

    var baseUrl: String
        get() = prefs.getString(K_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(v) = prefs.edit().putString(K_URL, v.trim().trimEnd('/')).apply()

    var token: String
        get() = prefs.getString(K_TOKEN, "") ?: ""
        set(v) = prefs.edit()
            .putString(K_TOKEN, v.trim())
            .putLong(K_TOKEN_SAVED, System.currentTimeMillis())
            .apply()

    /** When the current token was saved (epoch ms). Tokens last 90 days on the site. */
    val tokenSavedAt: Long get() = prefs.getLong(K_TOKEN_SAVED, 0L)

    /** Whether the last run finished cleanly. */
    var lastOk: Boolean
        get() = prefs.getBoolean(K_LAST_OK, true)
        set(v) = prefs.edit().putBoolean(K_LAST_OK, v).apply()

    /**
     * Health Connect source apps to sync, in priority order. When two sources record the
     * same workout, the one earlier in this list wins.
     */
    var sources: List<String>
        get() = prefs.getString(K_SOURCES, null)?.split(',')?.filter { it.isNotBlank() }
            ?: listOf(SAMSUNG_HEALTH_PACKAGE)
        set(v) = prefs.edit().putString(K_SOURCES, v.distinct().joinToString(",")).apply()

    var autoSync: Boolean
        get() = prefs.getBoolean(K_AUTO, true)
        set(v) = prefs.edit().putBoolean(K_AUTO, v).apply()

    var intervalHours: Int
        get() = prefs.getInt(K_INTERVAL, 3)
        set(v) = prefs.edit().putInt(K_INTERVAL, v).apply()

    /** Auto-sync never looks before this time (epoch ms). Older data comes in via "Import older data". */
    var syncFloor: Long
        get() = prefs.getLong(K_FLOOR, 0L)
        set(v) = prefs.edit().putLong(K_FLOOR, v).apply()

    /** End time (epoch ms) of the latest session handled by auto-sync. 0 = never. */
    var watermark: Long
        get() = prefs.getLong(K_WATERMARK, 0L)
        set(v) = prefs.edit().putLong(K_WATERMARK, v).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(K_LAST_SYNC, 0L)
        set(v) = prefs.edit().putLong(K_LAST_SYNC, v).apply()

    var lastStatus: String
        get() = prefs.getString(K_STATUS, "Not synced yet") ?: ""
        set(v) = prefs.edit().putString(K_STATUS, v).apply()

    companion object {
        const val DEFAULT_URL = "https://devswhorun.dev"
        val INTERVAL_OPTIONS = listOf(1, 3, 6, 12)

        private const val K_ONBOARDED = "onboarded"
        private const val K_URL = "base_url"
        private const val K_TOKEN = "token"
        private const val K_TOKEN_SAVED = "token_saved_at"
        private const val K_LAST_OK = "last_ok"
        const val TOKEN_LIFETIME_DAYS = 90L
        private const val K_SOURCES = "sources"
        private const val K_AUTO = "auto_sync"
        private const val K_INTERVAL = "interval_hours"
        private const val K_FLOOR = "sync_floor"
        private const val K_WATERMARK = "watermark"
        private const val K_LAST_SYNC = "last_sync_at"
        private const val K_STATUS = "last_status"
    }
}
