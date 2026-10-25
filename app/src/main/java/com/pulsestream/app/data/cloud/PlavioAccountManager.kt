package com.pulsestream.app.data.cloud

import android.content.Context
import com.pulsestream.app.BuildConfig
import com.pulsestream.app.data.StreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Plavio account (owner's order 2026-10-25): email + password sign-in on
 * Supabase Auth, with the full app backup (the same payload the Google
 * Drive sync carries — library, progress, addons, settings and API keys)
 * stored in a per-user row guarded by row-level security.
 *
 * Email codes are used ONLY for password reset (recover -> verify OTP ->
 * set new password). Google Drive stays available as a second door.
 *
 * The feature hides itself until the backend is configured at build
 * time (PLAVIO_SUPABASE_URL / PLAVIO_SUPABASE_ANON_KEY).
 */
class PlavioAccountManager(
    context: Context,
    private val repository: StreamRepository
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val PREFS = "plavio_account"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_LAST_SYNC = "last_sync"

        fun isConfigured(): Boolean =
            BuildConfig.PLAVIO_SUPABASE_URL.isNotBlank() &&
                BuildConfig.PLAVIO_SUPABASE_ANON_KEY.isNotBlank()
    }

    fun isSignedIn(): Boolean = prefs.getString(KEY_ACCESS, null) != null &&
        prefs.getString(KEY_USER_ID, null) != null

    fun accountEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun lastSyncTime(): String? = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }?.let {
        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(it))
    }

    // ------------------------------------------------------------------
    // Auth
    // ------------------------------------------------------------------

    /** Returns true when a session was issued immediately (no email confirmation pending). */
    suspend fun signUp(email: String, password: String): Result<Boolean> = authCall {
        val body = JSONObject().put("email", email).put("password", password)
        val resp = post("/auth/v1/signup", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
        val json = resp.json ?: throw AuthException("Unexpected response from the account server.")
        if (json.has("access_token")) {
            saveSession(json, email)
            true
        } else {
            false
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = authCall {
        val body = JSONObject().put("email", email).put("password", password)
        val resp = post("/auth/v1/token?grant_type=password", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
        val json = resp.json ?: throw AuthException("Unexpected response from the account server.")
        saveSession(json, email)
    }

    suspend fun signOut() {
        val token = prefs.getString(KEY_ACCESS, null)
        if (token != null) {
            runCatching { post("/auth/v1/logout", JSONObject(), accessToken = token) }
        }
        prefs.edit().clear().apply()
    }

    /** Sends the 6-digit reset code email. */
    suspend fun requestPasswordReset(email: String): Result<Unit> = authCall {
        val body = JSONObject().put("email", email)
        val resp = post("/auth/v1/recover", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
    }

    /** Verifies the reset code and sets the new password. Ends signed out. */
    suspend fun verifyResetAndSetPassword(email: String, code: String, newPassword: String): Result<Unit> = authCall {
        val body = JSONObject().put("email", email).put("token", code).put("type", "recovery")
        val resp = post("/auth/v1/verify", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
        val json = resp.json ?: throw AuthException("Unexpected response from the account server.")
        val recoveryToken = json.optString("access_token")
            .ifBlank { throw AuthException("The reset code was not accepted.") }
        val update = JSONObject().put("password", newPassword)
        val resp2 = put("/auth/v1/user", update, accessToken = recoveryToken)
        if (resp2.code !in 200..299) throw AuthException(errorMessage(resp2.json, resp2.code))
        runCatching { post("/auth/v1/logout", JSONObject(), accessToken = recoveryToken) }
    }

    suspend fun changePassword(newPassword: String): Result<Unit> = authCall {
        withFreshToken { token ->
            val body = JSONObject().put("password", newPassword)
            val resp = put("/auth/v1/user", body, accessToken = token)
            if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
        }
    }

    // ------------------------------------------------------------------
    // Sync (same payload as the Google Drive backup)
    // ------------------------------------------------------------------

    /** Uploads the current app backup into the account row. */
    suspend fun syncNow(): Result<Unit> = authCall {
        val payload = repository.exportBackupJson().getOrThrow()
        withFreshToken { token ->
            val uid = prefs.getString(KEY_USER_ID, null)
                ?: throw AuthException("Not signed in.")
            val body = JSONObject()
                .put("user_id", uid)
                .put("payload", payload)
                .put("updated_at", isoNow())
            val resp = post(
                "/rest/v1/account_backups", body, accessToken = token,
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            )
            if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
        }
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    /** True when a cloud backup existed (and was imported). */
    suspend fun restoreFromAccount(): Result<Boolean> = authCall {
        var restored = false
        withFreshToken { token ->
            val uid = prefs.getString(KEY_USER_ID, null)
                ?: throw AuthException("Not signed in.")
            val resp = get(
                "/rest/v1/account_backups?select=payload,updated_at&user_id=eq.$uid",
                accessToken = token
            )
            if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
            val rows = resp.jsonArray
            val payload = rows?.optJSONObject(0)?.optString("payload")?.takeIf { it.isNotBlank() }
            if (payload != null) {
                repository.importBackupJson(payload).getOrThrow()
                restored = true
            }
        }
        restored
    }

    /**
     * Post-sign-in flow: restore the account backup when one exists;
     * otherwise upload the current device state as the first backup.
     */
    suspend fun syncAfterSignIn(): Result<Boolean> = authCall {
        val restored = restoreFromAccount().getOrThrow()
        if (!restored) syncNow().getOrThrow()
        restored
    }

    // ------------------------------------------------------------------
    // HTTP plumbing
    // ------------------------------------------------------------------

    private class AuthException(message: String) : Exception(message)

    private data class Resp(val code: Int, val json: JSONObject?, val jsonArray: org.json.JSONArray?)

    private fun baseRequest(path: String, accessToken: String?): Request.Builder =
        Request.Builder()
            .url(BuildConfig.PLAVIO_SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BuildConfig.PLAVIO_SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${accessToken ?: BuildConfig.PLAVIO_SUPABASE_ANON_KEY}")
            .header("Content-Type", "application/json")

    private fun execute(req: Request): Resp {
        val text = client.newCall(req).execute().use { r ->
            r.code to (r.body?.string().orEmpty())
        }
        val (code, body) = text
        val trimmed = body.trim()
        return Resp(
            code,
            runCatching { JSONObject(trimmed) }.getOrNull(),
            runCatching { org.json.JSONArray(trimmed) }.getOrNull()
        )
    }

    private fun post(path: String, body: JSONObject, accessToken: String?, extraHeaders: Map<String, String> = emptyMap()): Resp {
        val builder = baseRequest(path, accessToken)
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        return execute(builder.post(body.toString().toRequestBody("application/json".toMediaType())).build())
    }

    private fun put(path: String, body: JSONObject, accessToken: String?): Resp =
        execute(baseRequest(path, accessToken).put(body.toString().toRequestBody("application/json".toMediaType())).build())

    private fun get(path: String, accessToken: String?): Resp =
        execute(baseRequest(path, accessToken).get().build())

    private fun errorMessage(json: JSONObject?, code: Int): String {
        val raw = json?.optString("msg")?.ifBlank { null }
            ?: json?.optString("message")?.ifBlank { null }
            ?: json?.optString("error_description")?.ifBlank { null }
            ?: json?.optString("error")?.ifBlank { null }
        return when {
            raw == null -> "Account server error ($code). Please try again."
            raw.contains("Invalid login credentials", ignoreCase = true) ->
                "Wrong email or password."
            raw.contains("Email not confirmed", ignoreCase = true) ->
                "Please confirm your email first — check your inbox."
            raw.contains("already registered", ignoreCase = true) ||
                raw.contains("already been registered", ignoreCase = true) ->
                "An account with this email already exists. Try signing in."
            raw.contains("Password should be at least", ignoreCase = true) -> raw
            else -> raw
        }
    }

    private fun saveSession(json: JSONObject, email: String) {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val expiresIn = json.optLong("expires_in", 3600)
        val userId = json.optJSONObject("user")?.optString("id").orEmpty()
        if (access.isBlank() || userId.isBlank()) throw AuthException("Unexpected sign-in response.")
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putLong(KEY_EXPIRES, System.currentTimeMillis() + expiresIn * 1000)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, json.optJSONObject("user")?.optString("email")?.ifBlank { null } ?: email)
            .apply()
    }

    /** Runs [block] with a valid access token, refreshing (and retrying once on 401). */
    private suspend fun withFreshToken(block: suspend (String) -> Unit) {
        var token = prefs.getString(KEY_ACCESS, null)
            ?: throw AuthException("Not signed in.")
        val expires = prefs.getLong(KEY_EXPIRES, 0L)
        if (System.currentTimeMillis() > expires - 60_000) {
            refreshSession()
            token = prefs.getString(KEY_ACCESS, null) ?: throw AuthException("Session expired.")
        }
        try {
            block(token)
        } catch (e: AuthException) {
            if (e.message?.contains("401") == true) {
                refreshSession()
                val fresh = prefs.getString(KEY_ACCESS, null) ?: throw e
                block(fresh)
            } else throw e
        }
    }

    private suspend fun refreshSession() {
        val refresh = prefs.getString(KEY_REFRESH, null)
            ?: throw AuthException("Session expired. Please sign in again.")
        val body = JSONObject().put("refresh_token", refresh)
        val resp = post("/auth/v1/token?grant_type=refresh_token", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException("Session expired. Please sign in again.")
        val json = resp.json ?: throw AuthException("Session expired. Please sign in again.")
        saveSession(json, prefs.getString(KEY_EMAIL, "").orEmpty())
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())

    private suspend fun <T> authCall(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: AuthException) {
                Result.failure(Exception(e.message))
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Something went wrong. Please try again."))
            }
        }
}
