package com.pulsestream.app.data

import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One channel parsed from an M3U/IPTV playlist. */
data class M3uChannel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String?
)

/**
 * Minimal M3U parser: reads #EXTINF lines (tvg-name/tvg-logo/group-title)
 * followed by the stream URL on the next non-empty, non-comment line.
 * Malformed entries are skipped — one bad line never kills the playlist.
 */
object M3uParser {
    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val ATTR = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")

    /** Hard ceiling on playlist download size — a huge/malicious playlist must not OOM the app. */
    private const val MAX_PLAYLIST_BYTES = 8L * 1024L * 1024L

    suspend fun fetch(url: String): Result<List<M3uChannel>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Plavio/${BuildConfig.VERSION_NAME}")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Playlist request failed (HTTP ${resp.code})")
                    }
                    val text = readBounded(resp)
                    parse(text).also {
                        if (it.isEmpty()) throw IllegalStateException(
                            "No channels found — is this an M3U playlist?"
                        )
                    }
                }
            }
        }

    /**
     * Reads the response body with a hard size cap, checked both against the
     * declared Content-Length (fast path) and incrementally while streaming
     * (covers chunked/lying servers). Strips a UTF-8 BOM when present.
     */
    private fun readBounded(resp: okhttp3.Response): String {
        val body = resp.body ?: throw IllegalStateException("Empty playlist")
        val declared = body.contentLength()
        if (declared > MAX_PLAYLIST_BYTES) {
            throw IllegalStateException(
                "Playlist too large (${declared / 1_048_576} MB — max ${MAX_PLAYLIST_BYTES / 1_048_576} MB)"
            )
        }
        val buffer = okio.Buffer()
        val source = body.source()
        var total = 0L
        while (true) {
            val read = source.read(buffer, 8192)
            if (read == -1L) break
            total += read
            if (total > MAX_PLAYLIST_BYTES) {
                throw IllegalStateException(
                    "Playlist too large (over ${MAX_PLAYLIST_BYTES / 1_048_576} MB)"
                )
            }
        }
        return buffer.readString(Charsets.UTF_8).removePrefix("\uFEFF")
    }

    fun parse(text: String): List<M3uChannel> {
        val out = mutableListOf<M3uChannel>()
        val seen = HashSet<String>()
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val attrs = ATTR.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                // Strip the attribute pairs first: a quoted comma inside e.g.
                // group-title="News, Sports" must not be mistaken for the
                // display-name separator.
                val displayName = ATTR.replace(line, "").substringAfter(",", "").trim()
                pendingName = attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                    ?: displayName.takeIf { it.isNotBlank() }
                pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() }
            } else if (!line.startsWith("#")) {
                val name = pendingName
                if (name != null && (line.startsWith("http://") || line.startsWith("https://"))) {
                    // Keep the supplied stream URL intact; normalize only the
                    // comparison key (case/default-port differences).
                    if (seen.add(comparisonKey(line))) {
                        out.add(M3uChannel(name, line, pendingLogo, pendingGroup))
                    }
                }
                pendingName = null
                pendingLogo = null
                pendingGroup = null
            }
        }
        return out
    }

    /**
     * A comparison key only: OkHttp canonicalizes scheme/host/default ports,
     * preserves credentials, IPv6 brackets, path trailing slash and query.
     * The returned key must never replace the original URL used for fetching.
     */
    fun comparisonKey(url: String): String {
        val supplied = url.trim()
        return supplied.toHttpUrlOrNull()?.newBuilder()?.fragment(null)?.build()?.toString()
            ?: supplied
    }

    /** Backwards-compatible alias for existing cache/dedup callers. */
    fun normalizeUrl(url: String): String = comparisonKey(url)
}
