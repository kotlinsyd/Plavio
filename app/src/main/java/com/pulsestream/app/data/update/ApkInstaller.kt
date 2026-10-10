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
 */
object ApkInstaller {

    fun download(context: Context, url: String, version: String, onDone: () -> Unit) {
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
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                try {
                    context.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                val file = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                if (file.exists()) {
                    val apkUri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    val install = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(install)
                }
                onDone()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }
}
