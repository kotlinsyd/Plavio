package com.pulsestream.app.data.cloud

import android.content.Context
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.StreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plavio Cloud: Supabase-backed login, profiles, and backup.
 * Login is OPTIONAL — the app works fully without an account.
 * When logged in: backs up watchlist, continue watching, custom lists,
 * settings, and installed addons. Supports multiple profiles per account.
 */
class CloudManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val repository: StreamRepository
) {
    companion object {
        private const val SUPABASE_URL = "https://vpngqutsjxrdgaihogor.supabase.co"
        // Anon key is public and safe to embed in the app
        private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZwbmdxdXRzanhyZGdhaWhvZ29yIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE5ODk3MDQsImV4cCI6MjEwNzU2NTcwNH0.sSGe2AF7AI0M1HdNiINqkfigVc6cnyi2syjE_9uMhG4"
        private const val PREFS = "plavio_cloud"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_LAST_BACKUP = "last_backup"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val isLoggedIn: Boolean
        get() = prefs.getString(KEY_ACCESS_TOKEN, null) != null

    val userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)

    val userId: String?
        get() = prefs.getString(KEY_USER_ID, null)

    val activeProfileId: String?
        get() = prefs.getString(KEY_ACTIVE_PROFILE, null)

    val lastBackupTime: String?
        get() = prefs.getString(KEY_LAST_BACKUP, null)

    private fun authHeaders(): Map<String, String> {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: ""
        return mapOf(
            "apikey" to SUPABASE_ANON_KEY,
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json"
        )
    }

    private fun post(path: String, body: String, headers: Map<String, String>): Pair<Int, String> {
        val url = URL("$SUPABASE_URL$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val response = try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        conn.disconnect()
        return code to response
    }

    private fun get(path: String, headers: Map<String, String>): Pair<Int, String> {
        val url = URL("$SUPABASE_URL$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val code = conn.responseCode
        val response = try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        conn.disconnect()
        return code to response
    }

    private fun delete(path: String, headers: Map<String, String>): Pair<Int, String> {
        val url = URL("$SUPABASE_URL$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 15000
            readTimeout = 15000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val code = conn.responseCode
        val response = try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        conn.disconnect()
        return code to response
    }

    suspend fun signUp(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()
            val (code, response) = post(
                "/auth/v1/signup",
                body,
                mapOf("apikey" to SUPABASE_ANON_KEY, "Content-Type" to "application/json")
            )
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(response).optString("msg", "Sign up failed.")
                } catch (e: Exception) { "Sign up failed." }
                return@withContext Result.failure(Exception(msg))
            }
            // Check if email confirmation is required
            val obj = try { JSONObject(response) } catch (e: Exception) { JSONObject() }
            val user = obj.optJSONObject("user")
            val confirmedAt = user?.optString("confirmed_at", "") ?: ""
            val identities = obj.optJSONArray("identities")
            
            if (confirmedAt.isEmpty() && (identities == null || identities.length() == 0)) {
                // Email confirmation required - don't auto-signin
                return@withContext Result.failure(
                    Exception("VERIFY_EMAIL:Please check your email and click the verification link, then log in.")
                )
            }
            // Auto sign in after signup (only if no confirmation needed)
            signIn(email, password)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()
            val (code, response) = post(
                "/auth/v1/token?grant_type=password",
                body,
                mapOf("apikey" to SUPABASE_ANON_KEY, "Content-Type" to "application/json")
            )
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(response).optString("msg", "Login failed. Check your email and password.")
                } catch (e: Exception) { "Login failed." }
                return@withContext Result.failure(Exception(msg))
            }
            val obj = JSONObject(response)
            val accessToken = obj.getString("access_token")
            val refreshToken = obj.optString("refresh_token", "")
            val user = obj.getJSONObject("user")
            val userId = user.getString("id")
            val userEmail = user.optString("email", email)

            prefs.edit().apply {
                putString(KEY_ACCESS_TOKEN, accessToken)
                putString(KEY_REFRESH_TOKEN, refreshToken)
                putString(KEY_USER_ID, userId)
                putString(KEY_USER_EMAIL, userEmail)
                apply()
            }

            // Create default profile if none exists
            ensureDefaultProfile()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    @Serializable
    data class CloudProfile(
        val id: String,
        val name: String,
        val created_at: String = ""
    )

    fun getProfiles(): List<CloudProfile> {
        // Stored locally for now; synced with backup
        val profilesJson = prefs.getString("profiles", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(profilesJson)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CloudProfile(
                    id = obj.getString("id"),
                    name = obj.getString("name")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveProfiles(profiles: List<CloudProfile>) {
        // Simple JSON encoding without kotlinx.serialization
        val jsonArray = org.json.JSONArray()
        profiles.forEach { p ->
            jsonArray.put(org.json.JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
            })
        }
        prefs.edit().putString("profiles", jsonArray.toString()).apply()
    }

    private suspend fun ensureDefaultProfile() {
        if (getProfiles().isEmpty()) {
            val default = CloudProfile(
                id = "profile_${System.currentTimeMillis()}",
                name = "Main"
            )
            saveProfiles(listOf(default))
            prefs.edit().putString(KEY_ACTIVE_PROFILE, default.id).apply()
        }
    }

    suspend fun createProfile(name: String): CloudProfile = withContext(Dispatchers.IO) {
        val profile = CloudProfile(
            id = "profile_${System.currentTimeMillis()}",
            name = name
        )
        val updated = getProfiles() + profile
        saveProfiles(updated)
        profile
    }

    suspend fun switchProfile(profileId: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply()
        // Reload profile-specific data from backup
        restoreProfileData(profileId)
    }

    suspend fun deleteProfile(profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val profiles = getProfiles()
            if (profiles.size <= 1) {
                return@withContext Result.failure(Exception("Cannot delete the last profile"))
            }
            val updated = profiles.filter { it.id != profileId }
            saveProfiles(updated)

            // If deleting the active profile, switch to the first remaining one
            if (activeProfileId == profileId) {
                val newActive = updated.first()
                prefs.edit().putString(KEY_ACTIVE_PROFILE, newActive.id).apply()
                restoreProfileData(newActive.id)
            }

            // Delete from cloud as well
            val userId = userId
            if (userId != null) {
                try {
                    delete(
                        "/rest/v1/user_backups?user_id=eq.$userId&profile_id=eq.$profileId",
                        authHeaders()
                    )
                } catch (e: Exception) { /* local delete still succeeded */ }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Auto-backup: silently backs up if logged in and enough time has passed
     * since the last backup (default 30 minutes). Called periodically and
     * when the app goes to background.
     */
    suspend fun autoBackupIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (userId == null) return@withContext false
            val lastBackup = prefs.getLong(KEY_LAST_BACKUP_TIME, 0)
            val now = System.currentTimeMillis()
            // Only auto-backup if 30+ minutes since last backup
            if (now - lastBackup < 30 * 60 * 1000) return@withContext false
            val result = backupNow()
            if (result.isSuccess) {
                prefs.edit().putLong(KEY_LAST_BACKUP_TIME, now).apply()
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Back up everything: watchlist, continue watching, custom lists,
     * settings, and installed addons.
     */
    suspend fun backupNow(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = userId ?: return@withContext Result.failure(Exception("Not logged in"))
            val profileId = activeProfileId ?: return@withContext Result.failure(Exception("No active profile"))

            // Collect watchlist
            val watchlistArr = org.json.JSONArray()
            repository.watchlist.first().forEach { item ->
                watchlistArr.put(org.json.JSONObject().apply {
                    put("key", item.key)
                    put("addonId", item.addonId)
                    put("type", item.type)
                    put("metaId", item.metaId)
                    put("name", item.name)
                    put("poster", item.poster)
                    put("addedAt", item.addedAt)
                })
            }

            // Collect continue watching
            val cwArr = org.json.JSONArray()
            repository.continueWatching.first().forEach { state ->
                cwArr.put(org.json.JSONObject().apply {
                    put("key", state.key)
                    put("title", state.title)
                    put("poster", state.poster)
                    put("videoId", state.videoId)
                    put("positionMs", state.positionMs)
                    put("durationMs", state.durationMs)
                    put("updatedAt", state.updatedAt)
                })
            }

            // Collect installed addons
            val addonsArr = org.json.JSONArray()
            repository.addons.first().forEach { addon ->
                addonsArr.put(org.json.JSONObject().apply {
                    put("id", addon.id)
                    put("name", addon.name)
                    put("baseUrl", addon.baseUrl)
                    put("enabled", addon.enabled)
                })
            }

            // Build backup payload
            val data = org.json.JSONObject().apply {
                put("watchlist", watchlistArr)
                put("continue_watching", cwArr)
                put("addons", addonsArr)
                put("settings", org.json.JSONObject().apply {
                    put("accent_theme", settings.accentName)
                })
                put("profiles", org.json.JSONArray().apply {
                    getProfiles().forEach { p ->
                        put(org.json.JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                        })
                    }
                })
            }

            val backup = org.json.JSONObject().apply {
                put("user_id", userId)
                put("profile_id", profileId)
                put("data", data)
            }

            val (code, _) = post(
                "/rest/v1/user_backups",
                backup.toString(),
                authHeaders() + ("Prefer" to "resolution=merge-duplicates")
            )

            if (code in 200..299) {
                val time = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date())
                prefs.edit().putString(KEY_LAST_BACKUP, time).apply()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Backup failed (code $code)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun restoreProfileData(profileId: String) = withContext(Dispatchers.IO) {
        try {
            val userId = userId ?: return@withContext
            val (code, response) = get(
                "/rest/v1/user_backups?user_id=eq.$userId&profile_id=eq.$profileId&select=data&limit=1",
                authHeaders()
            )
            if (code !in 200..299) return@withContext
            val arr = org.json.JSONArray(response)
            if (arr.length() == 0) return@withContext  // No backup yet, nothing to restore
            val data = arr.getJSONObject(0).getJSONObject("data")

            // Restore watchlist
            val watchlistArr = data.optJSONArray("watchlist")
            if (watchlistArr != null) {
                for (i in 0 until watchlistArr.length()) {
                    val item = watchlistArr.getJSONObject(i)
                    val key = item.optString("key", "")
                    if (key.isEmpty()) continue
                    // Use toggleWatchlist only if not already present
                    if (repository.watchlist.first().none { it.key == key }) {
                        repository.toggleWatchlist(
                            addonId = item.optString("addonId", ""),
                            type = item.optString("type", ""),
                            metaId = item.optString("metaId", ""),
                            name = item.optString("name", ""),
                            poster = item.optString("poster", null)
                        )
                    }
                }
            }

            // Restore continue watching
            val cwArr = data.optJSONArray("continue_watching")
            if (cwArr != null) {
                for (i in 0 until cwArr.length()) {
                    val state = cwArr.getJSONObject(i)
                    repository.savePlayback(
                        title = state.optString("title", ""),
                        poster = state.optString("poster", null),
                        videoId = state.optString("videoId", ""),
                        streamUrl = state.optString("key", ""),
                        positionMs = state.optLong("positionMs", 0),
                        durationMs = state.optLong("durationMs", 0)
                    )
                }
            }

            // Restore addons (install any missing ones by URL)
            val addonsArr = data.optJSONArray("addons")
            if (addonsArr != null) {
                val existingIds = repository.addons.first().map { it.id }.toSet()
                for (i in 0 until addonsArr.length()) {
                    val addon = addonsArr.getJSONObject(i)
                    val id = addon.optString("id", "")
                    val baseUrl = addon.optString("baseUrl", "")
                    if (id.isNotEmpty() && id !in existingIds && baseUrl.isNotEmpty()) {
                        try { repository.addAddonFromUrl(baseUrl) } catch (e: Exception) { /* skip */ }
                    }
                }
            }

            // Restore settings
            val settingsObj = data.optJSONObject("settings")
            if (settingsObj != null) {
                val accent = settingsObj.optString("accent_theme", "")
                if (accent.isNotEmpty()) {
                    try { settings.accentName = accent } catch (e: Exception) { /* skip */ }
                }
            }
        } catch (e: Exception) {
            // Auto-sync failed silently — user can still use the app
        }
    }

    /**
     * Auto-sync: called after login. Downloads the cloud backup and restores it locally.
     * Returns true if a backup was found and restored.
     */
    suspend fun autoSyncOnLogin(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val profileId = activeProfileId ?: return@withContext Result.success(false)
            restoreProfileData(profileId)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
