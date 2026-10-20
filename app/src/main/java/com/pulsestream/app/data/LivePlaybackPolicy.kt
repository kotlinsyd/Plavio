package com.pulsestream.app.data

import androidx.media3.common.PlaybackException
import java.security.MessageDigest

/** Narrow policy for mobile Live source recovery; TV and non-Live playback keep their existing rules. */
internal object LivePlaybackPolicy {
    /** One initial stream plus at most eight automatic source advances. */
    const val MAX_MOBILE_LIVE_FAILOVERS = 8

    fun isRecoverableSourceError(errorCode: Int): Boolean =
        // Media3 reserves 2xxx for IO failures (including network timeout/connectivity and HTTP errors).
        errorCode in 2_000..2_999 || errorCode in setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
        )

    fun shouldRetry(
        isLive: Boolean,
        isTv: Boolean,
        errorCode: Int,
        attemptsSoFar: Int
    ): Boolean = isLive && !isTv && isRecoverableSourceError(errorCode) &&
        attemptsSoFar < MAX_MOBILE_LIVE_FAILOVERS

    /** Distinguishes identical URLs that require different request headers without retaining raw headers. */
    internal fun candidateIdentity(stream: StreamItem): String = candidateIdentity(stream.url, stream.headers)

    internal fun candidateIdentity(url: String, headers: Map<String, String>): String {
        val canonicalHeaders = headers.entries
            .map { it.key.lowercase() to it.value }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString("\n") { (name, value) -> "$name:$value" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$url\n$canonicalHeaders".toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** Same URL with different request headers is a distinct mobile Live candidate. */
    internal fun isSameMobileLiveCandidate(
        currentUrl: String,
        currentHeaders: Map<String, String>,
        selectedUrl: String,
        selectedHeaders: Map<String, String>
    ): Boolean = candidateIdentity(currentUrl, currentHeaders) ==
        candidateIdentity(selectedUrl, selectedHeaders)

    /** Selects only a later, untried candidate; it never wraps to earlier rows. */
    internal fun nextUntriedCandidate(
        streams: List<StreamItem>,
        current: StreamItem,
        attemptedIdentities: Set<String>
    ): StreamItem? {
        val currentIdentity = candidateIdentity(current)
        val currentIndex = streams.indexOfFirst { candidateIdentity(it) == currentIdentity }
        val later = if (currentIndex >= 0) streams.drop(currentIndex + 1) else streams
        return later.firstOrNull { stream ->
            isPlayableLiveStreamUrl(stream.url) &&
                candidateIdentity(stream) != currentIdentity &&
                candidateIdentity(stream) !in attemptedIdentities
        }
    }
}
