package com.pulsestream.app.ui.screens.player

import com.pulsestream.app.data.StreamItem

/** Add late addon results without dropping the title's already loaded streams or duplicating rows. */
internal fun mergePlayerSourceSet(
    existing: List<StreamItem>,
    incoming: List<StreamItem>,
    includeHeadersInIdentity: Boolean = false
): List<StreamItem> {
    fun identity(stream: StreamItem): String {
        val hash = stream.infoHash ?: stream.resolvedInfoHash
        val source = if (hash != null) "torrent:${hash.lowercase()}|${stream.fileIdx}|${stream.filePath.orEmpty()}"
            else "url:${stream.url}|${stream.fileIdx}|${stream.filePath.orEmpty()}"
        if (!includeHeadersInIdentity) return source
        val headerKey = stream.headers.entries
            .map { it.key.lowercase() to it.value }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString("\n") { (name, value) -> "$name:$value" }
        return "$source|headers:$headerKey"
    }
    val merged = linkedMapOf<String, StreamItem>()
    existing.forEach { merged[identity(it)] = it }
    incoming.forEach { stream ->
        val key = identity(stream)
        val previous = merged[key]
        // An old still-running addon request must not replace an already resolved cached link.
        if (previous == null || !stream.url.startsWith("infohash:", true) || previous.url.startsWith("infohash:", true)) {
            merged[key] = stream
        }
    }
    return merged.values.toList()
}
