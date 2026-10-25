package com.pulsestream.app.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.core.content.FileProvider
import com.pulsestream.app.BuildConfig
import java.io.File

/**
 * "Share debug log" (Settings > About): builds a small text report the
 * user can send to support — app/device facts, the privacy-safe source
 * diagnostics, and the app's own recent logcat with URLs and key/token
 * values redacted — then shares it as a .txt file. Nothing is sent
 * anywhere automatically; the user picks the destination.
 */
object DebugLog {

    private val urlPattern = Regex("""https?://\S+""")
    private val secretPattern = Regex("""(?i)(key|token|secret|password|auth)=([^\s&]+)""")

    fun build(): String = buildString {
        appendLine("Plavio debug log")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine(SourceDiagnostics.export())
        appendLine()
        appendLine("--- Logcat (this app, latest 300 lines, redacted) ---")
        appendLine(readOwnLogcat())
    }

    private fun readOwnLogcat(): String = runCatching {
        val proc = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-t", "300", "--pid", Process.myPid().toString())
        )
        val text = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        redact(text).ifBlank { "(no log lines captured)" }
    }.getOrElse { "(logcat unavailable: ${it.javaClass.simpleName})" }

    private fun redact(text: String): String =
        secretPattern.replace(urlPattern.replace(text, "<url>")) { "${it.groupValues[1]}=<hidden>" }

    /** Writes the log to the cache dir and opens the system share sheet. */
    fun share(context: Context) {
        runCatching {
            val dir = File(context.cacheDir, "debug").apply { mkdirs() }
            val file = File(dir, "plavio-debug-log.txt")
            file.writeText(build())
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Plavio debug log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share debug log"))
        }
    }
}
