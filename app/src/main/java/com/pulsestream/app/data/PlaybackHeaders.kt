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
     * Nuvio-style header sanitization: forward the addon's proxyHeaders
     * verbatim, dropping only blank keys/values and Range. Never strip
     * User-Agent — upstreams that ship a UA via proxyHeaders expect it,
     * and a missing UA is itself a bot-filter trigger.
     */
    fun buildPlaybackHeaders(
        addonHeaders: Map<String, String>,
        isTv: Boolean
    ): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in addonHeaders) {
            if (k.isBlank() || v.isBlank()) continue
            if (k.equals("Range", ignoreCase = true)) continue
            out[k] = v
        }
        if (out.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            out["User-Agent"] = if (isTv) DEFAULT_TV_UA else DEFAULT_MOBILE_UA
        }
        return out
    }

    private val M3U8_PATTERN = Regex("(^|[=/_.?&%-])m3u8($|[=/_.?&%-])", RegexOption.IGNORE_CASE)
    private val MPD_PATTERN = Regex("(^|[=/_.?&%-])mpd($|[=/_.?&%-])", RegexOption.IGNORE_CASE)

    /**
     * Infer the stream container from the FULL URL (path + query), since
     * proxied HLS URLs often hide the extension in a query parameter
     * (e.g. /api/manifest?url=<encoded ...m3u8>).
     * Returns null when nothing recognizable is found.
     */
    fun inferMimeType(url: String): String? = when {
        M3U8_PATTERN.containsMatchIn(url) -> MimeTypes.APPLICATION_M3U8
        MPD_PATTERN.containsMatchIn(url) -> MimeTypes.APPLICATION_MPD
        else -> null
    }
}
