package com.pulsestream.app.data.download

/** HTTP range and media-type rules kept pure so they can be unit tested. */
internal object DownloadProtocol {
    data class ContentRange(val start: Long, val endInclusive: Long, val total: Long?)

    private val RANGE = Regex("^bytes (\\d+)-(\\d+)/(\\d+|\\*)$", RegexOption.IGNORE_CASE)

    /** Checks payload + reserve without allowing signed-Long addition to wrap. */
    fun fitsWithReserve(availableBytes: Long, payloadBytes: Long, reserveBytes: Long): Boolean =
        availableBytes >= 0 && payloadBytes >= 0 && reserveBytes >= 0 &&
            payloadBytes <= availableBytes && reserveBytes <= availableBytes - payloadBytes

    fun parseContentRange(value: String?, requestedStart: Long): ContentRange? {
        if (requestedStart < 0) return null
        val match = RANGE.matchEntire(value?.trim() ?: return null) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
        if (start != requestedStart || end == Long.MAX_VALUE || end < start || (total != null && total <= end)) return null
        return ContentRange(start, end, total)
    }

    fun isAdaptive(url: String, contentType: String? = null): Boolean {
        val lowerUrl = url.lowercase()
        val type = contentType.orEmpty().substringBefore(';').trim().lowercase()
        return lowerUrl.contains(".m3u8") || lowerUrl.contains(".mpd") ||
            type in setOf(
                "application/vnd.apple.mpegurl",
                "application/x-mpegurl",
                "audio/mpegurl",
                "audio/x-mpegurl",
                "application/dash+xml"
            )
    }

    fun isUnsupportedPayload(prefix: ByteArray): Boolean {
        val text = prefix.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n').lowercase()
        return text.startsWith("#extm3u") || text.startsWith("<mpd") ||
            (text.startsWith("<?xml") && "<mpd" in text) || text.startsWith("<!doctype html") ||
            text.startsWith("<html") || text.startsWith("{\"") || text.startsWith("[{")
    }
    fun changedValidator(
        oldEtag: String?, oldLastModified: String?, newEtag: String?, newLastModified: String?
    ): Boolean = (oldEtag != null && newEtag != null && oldEtag != newEtag) ||
        (oldLastModified != null && newLastModified != null && oldLastModified != newLastModified)

    fun ifRange(etag: String?, lastModified: String?): String? =
        etag?.takeUnless { it.startsWith("W/", ignoreCase = true) } ?: lastModified
}
