package com.pulsestream.app.data

import androidx.media3.common.MimeTypes

/**
 * Shared playback helpers, ported from Nuvio's playback pipeline.
 *
 * Both the player (PlayerScreen.buildExo) and the stream prober
 * (StreamProber) MUST use [buildPlaybackHeaders] so probing matches
 * playback exactly — a mismatch means "Plavio Pick" can bless a stream
 * that then fails in the player.
 */
object PlaybackHeaders {

    /** Default UA when the addon supplies none (Nuvio Mobile's default). */
    const val DEFAULT_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** Default UA when the addon supplies none on TV (NuvioTV's default). */
    const val DEFAULT_TV_UA =
        "Mozilla/5.0 (Linux; Android 13; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Validated addon headers shared by the built-in player and prober.
     * Header names/values are bounded and control characters rejected; hop-by-hop
     * and caller-supplied Range headers are not forwarded.
     */
    fun buildPlaybackHeaders(
        addonHeaders: Map<String, String>,
        isTv: Boolean
    ): Map<String, String> {
        val blocked = setOf("range", "connection", "host", "content-length", "transfer-encoding", "upgrade")
        val out = linkedMapOf<String, Pair<String, String>>()
        for ((rawName, rawValue) in addonHeaders) {
            val name = rawName.trim()
            val value = rawValue.trim()
            if (name.isEmpty() || name.length > 128 || value.isEmpty() || value.length > 8192) continue
            if (!name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) continue
            if (value.any { it == '\r' || it == '\n' || it == '\u0000' }) continue
            val lower = name.lowercase()
            if (lower in blocked) continue
            out[lower] = canonicalHeaderName(name) to value
        }
        if ("user-agent" !in out) {
            val ua = if (isTv) DEFAULT_TV_UA else DEFAULT_MOBILE_UA
            out["user-agent"] = "User-Agent" to ua
        }
        return out.values.associateTo(linkedMapOf()) { it }
    }

    /** Headers safe to expose to arbitrary external-player apps via intent extras. */
    fun isSensitiveHeader(name: String): Boolean {
        val normalized = name.lowercase().replace("_", "-")
        return normalized in setOf("authorization", "proxy-authorization", "cookie", "set-cookie", "referer", "origin") ||
            listOf("token", "secret", "api-key", "apikey", "auth").any { it in normalized }
    }

    fun externalPlayerHeaders(addonHeaders: Map<String, String>, isTv: Boolean = false): Map<String, String> =
        buildPlaybackHeaders(addonHeaders, isTv).filterKeys { it.lowercase() in setOf("user-agent", "accept", "accept-language") }

    private fun canonicalHeaderName(name: String): String = when (name.lowercase()) {
        "user-agent" -> "User-Agent"
        "accept" -> "Accept"
        "referer" -> "Referer"
        "origin" -> "Origin"
        "authorization" -> "Authorization"
        "cookie" -> "Cookie"
        else -> name
    }

    private val M3U8_PATTERN = Regex("(^|[=/_.?&%-])m3u8($|[=/_.?&%-])", RegexOption.IGNORE_CASE)
    private val MPD_PATTERN = Regex("(^|[=/_.?&%-])mpd($|[=/_.?&%-])", RegexOption.IGNORE_CASE)

    /**
     * Infer the stream container from the FULL URL (path + query), since
     * proxied HLS URLs often hide the extension in a query parameter
     * (e.g. /api/manifest?url=<encoded ...m3u8>).
     * Returns null when nothing recognizable is found.
     */
    fun inferMimeType(url: String): String? {
        // Encoded punctuation (%2E, %2F) is not a literal extension boundary.
        // Decode only for detection; never rewrite the signed playback URL.
        val decoded = try { java.net.URLDecoder.decode(url, "UTF-8") } catch (_: IllegalArgumentException) { url }
        return when {
            M3U8_PATTERN.containsMatchIn(url) || M3U8_PATTERN.containsMatchIn(decoded) -> MimeTypes.APPLICATION_M3U8
            MPD_PATTERN.containsMatchIn(url) || MPD_PATTERN.containsMatchIn(decoded) -> MimeTypes.APPLICATION_MPD
            else -> null
        }
    }
}
