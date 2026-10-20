package com.pulsestream.app.data

import com.pulsestream.app.BuildConfig
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Volatile diagnostics only. No request URL, payload, exception text or viewing history is stored. */
object SourceDiagnostics {
    data class Entry(val provider: String, val outcome: String, val durationMs: Long, val recordedAt: Long)
    private val allowed = setOf(
        "loading", "succeeded", "timed_out", "authentication_needed", "no_results", "failed",
        "fallback_attempt", "fallback_exhausted", "startup_timeout", "rebuffer_timeout"
    )
    private val mutableEntries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = mutableEntries

    @Synchronized
    fun record(provider: String, outcome: String, durationMs: Long) {
        // Hash before storage: even a mistakenly supplied configured URL never enters the log.
        val safeId = MessageDigest.getInstance("SHA-256").digest(provider.toByteArray())
            .take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
        val entry = Entry("source-$safeId", outcome.takeIf { it in allowed } ?: "failed",
            durationMs.coerceIn(0, 300_000), System.currentTimeMillis())
        mutableEntries.value = (mutableEntries.value + entry).takeLast(80)
    }

    @Synchronized fun clear() { mutableEntries.value = emptyList() }

    fun export(): String = buildString {
        appendLine("Plavio ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) mobile source diagnostics")
        appendLine("Android API ${android.os.Build.VERSION.SDK_INT}; no URLs, tokens, titles or account IDs included.")
        entries.value.forEach { appendLine("${it.provider}: ${it.outcome}, ${it.durationMs}ms") }
    }
}
