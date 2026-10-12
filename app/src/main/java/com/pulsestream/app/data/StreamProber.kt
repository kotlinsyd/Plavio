package com.pulsestream.app.data

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Plavio Pick": background-probes stream URLs to find the best working one.
 *
 * Each http(s) candidate gets a HEAD request (falling back to a 1KB ranged GET
 * when HEAD is rejected); a 2xx/206 response counts as working and the
 * time-to-first-byte is recorded. Non-HTTP URLs (magnet:, etc.) are skipped.
 * Candidates are scored by resolution first, speed second.
 */
object StreamProber {

    private const val CONNECT_TIMEOUT_MS = 4000
    private const val READ_TIMEOUT_MS = 4000
    private const val MAX_PROBES = 12
    private const val MAX_PARALLEL = 4
    private const val OVERALL_TIMEOUT_MS = 25_000L

    private data class Probe(val stream: StreamItem, val ttfbMs: Long, val resolution: Int)

    /**
     * Returns the best working stream, or null when nothing verifiable answered.
     * Runs fully on Dispatchers.IO; safe to call from a ViewModel scope.
     *
     * Uses [PlaybackHeaders.buildPlaybackHeaders] so probing sends exactly
     * what the player will send — no more "healthy in probe, fails in player".
     */
    suspend fun findBest(
        streams: List<StreamItem>,
        isTv: Boolean = false
    ): StreamItem? = withContext(Dispatchers.IO) {
        val candidates = streams
            .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
            .take(MAX_PROBES)
        if (candidates.isEmpty()) return@withContext null

        val results = withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
            coroutineScope {
                val gate = Semaphore(MAX_PARALLEL)
                candidates.map { stream ->
                    async {
                        gate.acquire()
                        try {
                            probeOne(stream, isTv)
                        } finally {
                            gate.release()
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        } ?: emptyList()

        results
            .sortedWith(compareByDescending<Probe> { it.resolution }.thenBy { it.ttfbMs })
            .firstOrNull()
            ?.stream
    }

    private fun probeOne(stream: StreamItem, isTv: Boolean): Probe? {
        val headers = PlaybackHeaders.buildPlaybackHeaders(stream.headers, isTv)
        val ttfb = headTtfb(stream, headers) ?: rangedGetTtfb(stream, headers) ?: return null
        return Probe(stream, ttfb, resolutionScore(stream))
    }

    /** Returns time-to-first-byte in ms when HEAD succeeds, else null. */
    private fun headTtfb(stream: StreamItem, headers: Map<String, String>): Long? {
        var conn: HttpURLConnection? = null
        return try {
            val start = SystemClock.elapsedRealtime()
            conn = (URL(stream.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                // Same headers the player will send (Nuvio parity).
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            conn.connect()
            val code = conn.responseCode
            if (code in 200..299 || code == 206) SystemClock.elapsedRealtime() - start else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Returns time-to-first-byte in ms when a 1KB ranged GET succeeds, else null. */
    private fun rangedGetTtfb(stream: StreamItem, headers: Map<String, String>): Long? {
        var conn: HttpURLConnection? = null
        return try {
            val start = SystemClock.elapsedRealtime()
            conn = (URL(stream.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                setRequestProperty("Range", "bytes=0-1023")
            }
            conn.connect()
            val code = conn.responseCode
            // Touch the stream so TTFB is real, then bail.
            if (code in 200..299 || code == 206) {
                conn.inputStream.read()
                SystemClock.elapsedRealtime() - start
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Rough resolution rank parsed from the stream's title/name labels. */
    private fun resolutionScore(stream: StreamItem): Int {
        val label = "${stream.title.orEmpty()} ${stream.name.orEmpty()}".lowercase()
        return when {
            "2160" in label || "4k" in label -> 4
            "1080" in label -> 3
            "720" in label -> 2
            "480" in label -> 1
            else -> 0
        }
    }
}
