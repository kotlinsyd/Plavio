package com.pulsestream.app.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Downloads the update APK with DownloadManager and opens the system
 * installer once it finishes. The APK is stored in the app's private
 * external downloads dir and re-shared through FileProvider.
 *
 * [onDone] fires only on a verified-good download (installer launched).
 * [onError] fires with a human-readable message on any failure — the
 * caller keeps its dialog open and shows it instead of dismissing
 * silently (a failed ACTION_DOWNLOAD_COMPLETE used to launch the
 * installer on a garbage file → "problem parsing the package").
 */
object ApkInstaller {

    fun download(
        context: Context,
        url: String,
        version: String,
        onDone: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val scheme = runCatching { Uri.parse(url).scheme }.getOrNull()?.lowercase()
        if (scheme != "http" && scheme != "https") {
            onError("That download link isn't a valid update URL ($url).")
            return
        }
        val fileName = "PulseStream-v$version.apk"
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
            File(dir, fileName).takeIf { it.exists() }?.delete()
        }
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Plavio v$version")
            setDescription("Downloading update…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // enqueue() throws IllegalArgumentException on malformed URLs —
        // surface it as a readable error instead of force-closing.
        val downloadId = try {
            dm.enqueue(request)
        } catch (e: Exception) {
            onError("Couldn't start the download: ${e.message ?: "bad download link"}.")
            return
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                try {
                    context.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                try {
                    verifyAndInstall(context, dm, downloadId, fileName, onDone, onError)
                } catch (e: Exception) {
                    deletePartial(context, fileName)
                    onError("The update download failed: ${e.message ?: "unknown error"}.")
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    /**
     * ACTION_DOWNLOAD_COMPLETE fires for failures too — check the real
     * status (and a non-trivial file) before touching the installer.
     */
    private fun verifyAndInstall(
        context: Context,
        dm: DownloadManager,
        downloadId: Long,
        fileName: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        var failure: String? = null
        dm.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                )
                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    val reason = runCatching {
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    }.getOrNull()
                    failure = "Download failed (${reasonMessage(reason)})."
                }
            } else {
                failure = "Download failed (no download found)."
            }
        } ?: run { failure = "Download failed (couldn't check its status)." }
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        // A "successful" status with an empty/missing file is still a failure
        // (partial write, storage hiccup) — never launch the installer on it.
        if (failure == null && (!file.exists() || file.length() <= 0)) {
            failure = "Download failed (the file came back empty)."
        }
        if (failure != null) {
            deletePartial(context, fileName)
            onError(failure!!)
            return
        }
        val apkUri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(install)
        onDone()
    }

    private fun reasonMessage(reason: Int?): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough storage space"
        DownloadManager.ERROR_CANNOT_RESUME -> "couldn't resume the download"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage not available"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "file already exists"
        DownloadManager.ERROR_FILE_ERROR -> "storage error"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "network error"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "too many redirects"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "server error"
        DownloadManager.ERROR_UNKNOWN -> "unknown error"
        else -> "unknown error"
    }

    private fun deletePartial(context: Context, fileName: String) {
        runCatching {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
                File(dir, fileName).takeIf { it.exists() }?.delete()
            }
        }
    }
}
