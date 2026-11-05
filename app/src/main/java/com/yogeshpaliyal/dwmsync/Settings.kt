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

    var baseUrl: String
        get() = prefs.getString(K_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(v) = prefs.edit().putString(K_URL, v.trim().trimEnd('/')).apply()

    var token: String
        get() = prefs.getString(K_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(K_TOKEN, v.trim()).apply()

    /** How many days back to import on the very first sync. */
    var backfillDays: Int
        get() = prefs.getInt(K_DAYS, 30)
        set(v) = prefs.edit().putInt(K_DAYS, v.coerceIn(1, 3650)).apply()

    /** Only sync sessions recorded by Samsung Health (avoids duplicates from other apps). */
    var samsungOnly: Boolean
        get() = prefs.getBoolean(K_SAMSUNG, true)
        set(v) = prefs.edit().putBoolean(K_SAMSUNG, v).apply()

    /** End time (epoch ms) of the latest session handled. 0 = never synced. */
    var watermark: Long
        get() = prefs.getLong(K_WATERMARK, 0L)
        set(v) = prefs.edit().putLong(K_WATERMARK, v).apply()

    var lastStatus: String
        get() = prefs.getString(K_STATUS, "Not synced yet") ?: ""
        set(v) = prefs.edit().putString(K_STATUS, v).apply()

    companion object {
        const val DEFAULT_URL = "https://devswhorun.dev"
        private const val K_URL = "base_url"
        private const val K_TOKEN = "token"
        private const val K_DAYS = "backfill_days"
        private const val K_SAMSUNG = "samsung_only"
        private const val K_WATERMARK = "watermark"
        private const val K_STATUS = "last_status"
    }
}
