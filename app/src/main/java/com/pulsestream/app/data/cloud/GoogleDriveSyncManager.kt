package com.pulsestream.app.data.cloud

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.StreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Drive sync (user's order 2026-10-19): replaces Supabase entirely.
 * - Login via device Google account (AccountManager, no Play Services needed)
 * - Backup stored in Drive's appDataFolder (private to Plavio, invisible to user)
 * - Auto-backup on login + manual; auto-restore on login
 * - Includes API keys (user's order: simple sync, no passphrase)
 *
 * Uses Drive REST API directly via OkHttp.
 */
class GoogleDriveSyncManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val repository: StreamRepository
) {
    companion object {
        private const val PREFS = "plavio_google_sync"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_LAST_BACKUP = "last_backup"
        private const val KEY_LAST_RESTORE = "last_restore"
        private const val BACKUP_FILENAME = "plavio-backup.json"
        private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"
        private const val ACCOUNT_TYPE_GOOGLE = "com.google"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val accountManager = AccountManager.get(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Get list of Google accounts on device */
    fun getGoogleAccounts(): List<Account> {
        return accountManager.getAccountsByType(ACCOUNT_TYPE_GOOGLE).toList()
    }

    /** Get the currently selected account, or null */
    fun getSelectedAccount(): Account? {
        val name = prefs.getString(KEY_ACCOUNT_NAME, null) ?: return null
        return getGoogleAccounts().find { it.name == name }
    }

    fun isSignedIn(): Boolean = getSelectedAccount() != null

    fun getAccountEmail(): String? {
        return getSelectedAccount()?.name ?: prefs.getString(KEY_ACCOUNT_NAME, null)
    }

    /** Select a Google account (user picks from list) */
    fun selectAccount(account: Account) {
        prefs.edit().putString(KEY_ACCOUNT_NAME, account.name).apply()
    }

    /** Select account by name (from system picker) */
    fun selectAccountByName(accountName: String) {
        prefs.edit().putString(KEY_ACCOUNT_NAME, accountName).apply()
    }

    /** Sign out - just clear the selected account */
    fun signOut() {
        prefs.edit().remove(KEY_ACCOUNT_NAME).apply()
    }

    /** Get OAuth2 access token for the selected account (suspending) */
    private suspend fun getAccessToken(account: Account): String =
        suspendCancellableCoroutine { cont ->
            accountManager.getAuthToken(
                account,
                DRIVE_SCOPE,
                Bundle(),
                true, // notifyAuthFailure - shows system dialog if needed
                { future ->
                    try {
                        val bundle = future.result
                        val token = bundle.getString(AccountManager.KEY_AUTHTOKEN)
                        if (token != null) {
                            cont.resume(token)
                        } else {
                            // User needs to grant permission - launch the intent
                            val intent = bundle.getParcelable<android.content.Intent>(
                                AccountManager.KEY_INTENT
                            )
                            if (intent != null) {
                                cont.resumeWithException(
                                    AuthRequiredException(intent)
                                )
                            } else {
                                cont.resumeWithException(
                                    Exception("Failed to get auth token")
                                )
                            }
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                null
            )
        }

    /** Exception indicating user interaction is needed */
    class AuthRequiredException(val intent: android.content.Intent) : Exception("Authorization required")

    /** Invalidate a token (when it expires) */
    private fun invalidateToken(token: String) {
        accountManager.invalidateAuthToken(ACCOUNT_TYPE_GOOGLE, token)
    }

    /** Find backup file ID in appDataFolder, or null if not found */
    private fun findBackupFileId(token: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=appDataFolder" +
            "&q=name%3D%27${BACKUP_FILENAME}%27" +
            "&fields=files(id)"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401) throw TokenExpiredException()
                return null
            }
            val body = response.body?.string() ?: return null
            val files = JSONObject(body).optJSONArray("files")
            if (files != null && files.length() > 0) {
                return files.getJSONObject(0).optString("id", null)
            }
            return null
        }
    }

    class TokenExpiredException : Exception("Token expired")

    /** Upload backup to Drive appDataFolder */
    suspend fun backupNow(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val account = getSelectedAccount()
                ?: return@withContext Result.failure(Exception("Not signed in"))
            val token = try {
                getAccessToken(account)
            } catch (e: AuthRequiredException) {
                return@withContext Result.failure(e)
            }

            val allCategories = setOf(
                "addons", "search_history", "watch_history",
                "watchlist", "custom_lists", "settings", "api_keys"
            )
            val json = repository.exportBackupJson(allCategories).getOrThrow()

            try {
                val fileId = findBackupFileId(token)

                if (fileId != null) {
                    // Update existing file
                    val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId" +
                        "?uploadType=media"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .patch(json.toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext Result.failure(
                                Exception("Drive update failed: ${response.code}")
                            )
                        }
                    }
                } else {
                    // Create new file with multipart upload
                    val metadata = JSONObject().apply {
                        put("name", BACKUP_FILENAME)
                        put("parents", org.json.JSONArray().put("appDataFolder"))
                    }
                    val boundary = "plavio-boundary-${System.currentTimeMillis()}"
                    val body = StringBuilder()
                        .append("--$boundary\r\n")
                        .append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                        .append(metadata.toString())
                        .append("\r\n--$boundary\r\n")
                        .append("Content-Type: application/json\r\n\r\n")
                        .append(json)
                        .append("\r\n--$boundary--\r\n")
                        .toString()

                    val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext Result.failure(
                                Exception("Drive upload failed: ${response.code}")
                            )
                        }
                    }
                }

                val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                prefs.edit().putString(KEY_LAST_BACKUP, now).apply()

                Result.success(Unit)
            } catch (e: TokenExpiredException) {
                invalidateToken(token)
                Result.failure(Exception("Session expired, please try again"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Download and restore backup from Drive appDataFolder */
    suspend fun restoreFromCloud(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val account = getSelectedAccount()
                ?: return@withContext Result.failure(Exception("Not signed in"))
            val token = try {
                getAccessToken(account)
            } catch (e: AuthRequiredException) {
                return@withContext Result.failure(e)
            }

            try {
                val fileId = findBackupFileId(token)
                    ?: return@withContext Result.success(false)

                val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                val json: String
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("Drive download failed: ${response.code}")
                        )
                    }
                    json = response.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty backup"))
                }

                // Save local snapshot for rollback
                val localSnapshot = repository.exportBackupJson(null).getOrNull()
                val result = repository.importBackupJson(json)
                if (result.isFailure) {
                    localSnapshot?.let { repository.importBackupJson(it) }
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Restore failed")
                    )
                }

                val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                prefs.edit().putString(KEY_LAST_RESTORE, now).apply()

                Result.success(true)
            } catch (e: TokenExpiredException) {
                invalidateToken(token)
                Result.failure(Exception("Session expired, please try again"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun autoRestoreOnLogin(): Result<Boolean> = restoreFromCloud()

    fun getLastBackupTime(): String? = prefs.getString(KEY_LAST_BACKUP, null)
    fun getLastRestoreTime(): String? = prefs.getString(KEY_LAST_RESTORE, null)
}
