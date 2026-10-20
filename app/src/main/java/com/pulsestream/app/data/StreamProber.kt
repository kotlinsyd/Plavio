package com.pulsestream.app.data

import android.os.SystemClock
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

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
    private const val MAX_PROBES = 24
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
        isTv: Boolean = false,
        qualityCap: String? = null
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

        // Adaptive pick (user's order 2026-10-20, Netflix/YouTube style): when a
        // quality cap is given, prefer the highest resolution AT or BELOW the cap
        // (fastest first); only if every working stream exceeds the cap, take the
        // lowest one above it. Without a cap, legacy behavior: highest resolution.
        val capScore = when (qualityCap) {
            "2160p" -> 4; "1080p" -> 3; "720p" -> 2; "480p" -> 1; else -> null
        }
        val sorted = if (capScore != null) {
            results.sortedWith(
                compareBy<Probe> { if (it.resolution <= capScore) 0 else 1 }
                    .thenByDescending { if (it.resolution <= capScore) it.resolution else -it.resolution }
                    .thenBy { it.ttfbMs }
            )
        } else {
            results.sortedWith(compareByDescending<Probe> { it.resolution }.thenBy { it.ttfbMs })
        }
        sorted.firstOrNull()?.stream
    }

    private fun probeOne(stream: StreamItem, isTv: Boolean): Probe? {
        val headers = PlaybackHeaders.buildPlaybackHeaders(stream.headers, isTv)
        // Match playback's redirect and credential-scoping behavior. A sensitive
        // header is retained on same-origin redirects and stripped cross-origin.
        val client = PlaybackHttp.client(stream.url).newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout((CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS).toLong(), TimeUnit.MILLISECONDS)
            .build()
        // HEAD reachability is not enough: require a media MIME, or verify a
        // bounded ranged body before considering an opaque/incorrect response playable.
        val head = headResult(client, stream, headers)
        val evidence = head?.takeIf { it.second }?.first ?: rangedGetTtfb(client, stream, headers) ?: return null
        return Probe(stream, evidence, resolutionScore(stream))
    }

    /** Pair of HEAD latency and whether its response explicitly identifies media. */
    private fun headResult(client: OkHttpClient, stream: StreamItem, headers: Map<String, String>): Pair<Long, Boolean>? {
        return try {
            val start = SystemClock.elapsedRealtime()
            val request = Request.Builder().url(stream.url).apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.head().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) return null
                val type = response.header("Content-Type").orEmpty().substringBefore(';').lowercase()
                val media = type.startsWith("video/") || type.startsWith("audio/") ||
                    type in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "application/dash+xml")
                (SystemClock.elapsedRealtime() - start) to media
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Returns time-to-first-byte in ms when a 1KB ranged GET succeeds, else null. */
    private fun rangedGetTtfb(client: OkHttpClient, stream: StreamItem, headers: Map<String, String>): Long? {
        return try {
            val start = SystemClock.elapsedRealtime()
            val request = Request.Builder().url(stream.url).apply {
                headers.forEach { (key, value) -> header(key, value) }
                header("Range", "bytes=0-1023")
            }.get().build()
            client.newCall(request).execute().use { response ->
                // Touch only a short prefix so TTFB is real and probing remains bounded.
                if (!response.isSuccessful && response.code != 206) return null
                val bytes = ByteArray(256)
                val count = response.body?.byteStream()?.read(bytes) ?: -1
                val type = response.header("Content-Type").orEmpty().substringBefore(';').lowercase()
                val prefix = if (count > 0) String(bytes, 0, count, Charsets.ISO_8859_1).trimStart().lowercase() else ""
                val looksHtml = prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.startsWith("{")
                val looksMedia = type.startsWith("video/") || type.startsWith("audio/") ||
                    type in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "application/dash+xml")
                if (count > 0 && !looksHtml && (looksMedia || type == "application/octet-stream" || prefix.startsWith("#extm3u")))
                    SystemClock.elapsedRealtime() - start else null
            }
        } catch (_: Exception) {
            null
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

    /** Quality rank of a stream: 4=2160p, 3=1080p, 2=720p, 1=480p, 0=unknown. */
    fun qualityRank(stream: StreamItem): Int = resolutionScore(stream)

    /** Quality rank of a cap label like "1080p"; unknown labels rank 0. */
    fun qualityRank(label: String): Int = when (label.trim().lowercase()) {
        "2160p", "4k", "uhd" -> 4
        "1080p" -> 3
        "720p" -> 2
        "480p" -> 1
        else -> 0
    }

    /**
     * Orders streams for Live TV: verified-reachable first (fastest TTFB),
     * then everything not yet verified, in original order.
     * Budget-bounded ([deadlineMs]) — whatever is verified in time goes first
     * and playback start never stalls on probing. Unprobed streams are kept
     * after as failover candidates.
     */
    suspend fun rankByProbe(
        streams: List<StreamItem>,
        isTv: Boolean = false,
        deadlineMs: Long = 6_000L
    ): List<StreamItem> = withContext(Dispatchers.IO) {
        val candidates = streams
            .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
            .take(12)
        if (candidates.isEmpty()) return@withContext streams
        val alive = mutableListOf<Probe>()
        try {
            withTimeout(deadlineMs) {
                coroutineScope {
                    val gate = Semaphore(4)
                    val deferreds = candidates.map { stream ->
                        async { gate.withPermit { probeOne(stream, isTv) } }
                    }
                    deferreds.forEach { d -> d.await()?.let { alive.add(it) } }
                }
            }
        } catch (_: TimeoutCancellationException) {
            // Partial results stand — the verified ones still go first.
        }
        val ordered = alive.sortedBy { it.ttfbMs }.map { it.stream }
        val aliveUrls = ordered.map { it.url }.toSet()
        ordered + streams.filter { it.url !in aliveUrls }
    }
}
