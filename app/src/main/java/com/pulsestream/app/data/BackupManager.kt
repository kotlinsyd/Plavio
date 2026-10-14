package com.pulsestream.app.data

import android.content.Context
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-file backup helper (v0.9.0+): writes/reads the JSON payload produced
 * by [StreamRepository.exportBackupJson] through the Storage Access Framework,
 * so no storage permission is needed. All DB/prefs work stays in the
 * repository; this class only moves bytes between the payload and a
 * user-chosen document.
 */
object BackupManager {

    /** Suggested file name for a new export, e.g. plavio-backup-2026-10-14.json */
    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "plavio-backup-$stamp.json"
    }

    /** Writes [json] to the user-chosen [uri]. Returns null on success, or an error message. */
    fun writeToUri(context: Context, uri: Uri, json: String): String? {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("Couldn't open the file for writing.")
            null
        }.getOrElse { it.message ?: "Write failed." }
    }

    /** Reads text from the user-chosen [uri]. Returns null on failure. */
    fun readFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
