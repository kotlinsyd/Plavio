package com.pulsestream.app.data.debrid

/**
 * Conservative selection of a TorBox torrent file for a Stremio stream.
 *
 * `fileIdx` is the zero-based position in the provider's original files array,
 * not TorBox's file id. Keep that distinction so skipped/malformed fixture or
 * API entries cannot silently shift a requested episode to another file.
 */
internal object TorBoxFileSelection {
    private val videoExtensions = setOf("mp4", "mkv", "avi", "webm", "mov", "m4v", "ts")

    fun select(
        files: List<TorBoxApi.TorrentFile>,
        fileIdx: Int?,
        filePath: String?
    ): TorBoxApi.TorrentFile? {
        val hasRequestedPath = !filePath.isNullOrBlank()
        if (fileIdx == null && !hasRequestedPath) {
            // No episode/file hint is safe only if there is exactly one video.
            // Choosing the largest video in a season pack can play the wrong episode.
            return files.filter { isVideo(it.name) }.singleOrNull()
        }

        val byIndex = fileIdx?.let { requested ->
            files.singleOrNull { it.sourceIndex == requested }
        }
        val normalizedPath = filePath?.takeIf { it.isNotBlank() }?.let(::normalizePath)
        if (fileIdx != null) {
            val indexed = byIndex ?: return null
            return indexed.takeIf { normalizedPath == null || normalizePath(it.name) == normalizedPath }
        }
        val byPath = normalizedPath?.let { requested ->
            val matches = files.filter { normalizePath(it.name) == requested }
            matches.singleOrNull()
        }

        if (hasRequestedPath && byPath == null) return null
        return byPath
    }

    private fun isVideo(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in videoExtensions

    private fun normalizePath(path: String): String =
        path.replace('\\', '/').trim('/').lowercase()
}
