package com.pulsestream.app.data.debrid

import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for the Real-Debrid API (https://api.real-debrid.com/rest/1.0).
 *
 * Debrid-only usage: resolve a cached torrent's infoHash to a direct HTTP
 * download link. No torrent downloading happens in the app — Real-Debrid's
 * servers do the fetching, we just get the HTTP URL.
 *
 * Flow:
 *  1. checkCached(hash) → is the torrent already on Real-Debrid's servers?
 *  2. addMagnet(magnet) → returns a Real-Debrid torrent id
 *  3. selectFiles(torrentId, fileId) → pick the video file
 *  4. waitForDownload(torrentId) → polls until "downloaded" (instant if cached)
 *  5. unrestrictLink(link) → direct HTTP URL for the file
 */
class RealDebridApi(private val apiKey: String) {

    companion object {
        private const val BASE = "https://api.real-debrid.com/rest/1.0"

        /** How many times to poll torrent status before giving up. */
        private const val MAX_POLLS = 40
        /** Delay between status polls. */
        private const val POLL_DELAY_MS = 3000L

        private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "webm", "mov", "m4v", "ts")
    }

    private val userAgent = "Plavio/${BuildConfig.VERSION_NAME}"

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun auth(request: Request.Builder): Request.Builder =
        request
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", userAgent)

    private fun get(path: String): String {
        val request = auth(Request.Builder().url("$BASE$path")).get().build()
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RealDebridException("Real-Debrid request failed (HTTP ${response.code}): ${bodyStr.take(200)}")
            }
            return bodyStr
        }
    }

    private fun postForm(path: String, form: FormBody): String {
        val request = auth(Request.Builder().url("$BASE$path")).post(form).build()
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RealDebridException("Real-Debrid request failed (HTTP ${response.code}): ${bodyStr.take(200)}")
            }
            return bodyStr
        }
    }

    /**
     * Check which of the given info hashes are already cached on
     * Real-Debrid's servers. Returns a map of hash → cached.
     *
     * Response shape: {"<hash>": [{"host": "real-debrid.com", "split": N,
     * "files": [{"id": 1, "filename": "..."}, ...]}]} — an empty list means
     * not cached.
     */
    suspend fun checkCached(hashes: List<String>): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            if (hashes.isEmpty()) return@withContext emptyMap()
            val result = mutableMapOf<String, Boolean>()
            // RD accepts up to 1000 hashes per call; keep batches small.
            for (batch in hashes.chunked(50)) {
                runCatching {
                    val path = batch.joinToString("/", prefix = "/torrents/instantAvailability/") { it.lowercase() }
                    val json = JSONObject(get(path))
                    for (h in batch) {
                        val entry = json.optJSONArray(h.lowercase())
                        result[h] = entry != null && entry.length() > 0
                    }
                }
            }
            result
        }

    /** Single-hash convenience. */
    suspend fun isCached(infoHash: String): Boolean =
        checkCached(listOf(infoHash))[infoHash] == true

    /**
     * Add a magnet link. Returns the Real-Debrid torrent id.
     */
    suspend fun addMagnet(magnet: String): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("magnet", magnet).build()
        val json = JSONObject(postForm("/torrents/addMagnet", form))
        json.optString("id").takeIf { it.isNotBlank() }
            ?: throw RealDebridException("addMagnet: missing id (${json.optString("error", "unknown")})")
    }

    /** File entry inside a Real-Debrid torrent. */
    data class RdFile(
        val id: Long,
        val path: String,
        val bytes: Long,
        val selected: Boolean = false
    )

    /** Status info for one torrent. */
    data class TorrentStatus(
        val id: String,
        val status: String,
        val files: List<RdFile>,
        /** Unrestricted download links, in selected-file order (present when downloaded). */
        val links: List<String>
    )

    /**
     * Fetch the current status of a torrent.
     */
    suspend fun getTorrentInfo(torrentId: String): TorrentStatus = withContext(Dispatchers.IO) {
        val json = JSONObject(get("/torrents/info/$torrentId"))
        val files = json.optJSONArray("files")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val f = arr.optJSONObject(i) ?: return@mapNotNull null
                RdFile(
                    id = f.optLong("id", -1L),
                    path = f.optString("path", ""),
                    bytes = f.optLong("bytes", 0L),
                    selected = f.optInt("selected", 0) == 1
                )
            }
        } ?: emptyList()
        val links = json.optJSONArray("links")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        } ?: emptyList()
        TorrentStatus(
            id = torrentId,
            status = json.optString("status", ""),
            files = files,
            links = links
        )
    }

    /**
     * Select which files to download (comma-separated file ids, or "all").
     * Must be called while the torrent status is "waiting_files_selection".
     */
    suspend fun selectFiles(torrentId: String, fileIds: String): Unit = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("files", fileIds).build()
        postForm("/torrents/selectFiles/$torrentId", form)
    }

    /**
     * Poll until the torrent reaches "downloaded" (or fail after
     * [MAX_POLLS] attempts). Cached torrents finish on the first poll.
     */
    suspend fun waitForDownload(torrentId: String): TorrentStatus = withContext(Dispatchers.IO) {
        var last: TorrentStatus? = null
        repeat(MAX_POLLS) {
            val status = getTorrentInfo(torrentId)
            last = status
            when (status.status) {
                "downloaded" -> return@withContext status
                "error", "magnet_error", "virus" ->
                    throw RealDebridException("Torrent failed (status=${status.status})")
            }
            delay(POLL_DELAY_MS)
        }
        throw RealDebridException("Timed out waiting for torrent $torrentId (last status=${last?.status})")
    }

    /**
     * Unrestrict a Real-Debrid download link into a direct HTTP URL.
     */
    suspend fun unrestrictLink(link: String): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("link", link).build()
        val json = JSONObject(postForm("/unrestrict/link", form))
        json.optString("download").takeIf { it.isNotBlank() }
            ?: throw RealDebridException("unrestrict/link: missing download URL (${json.optString("error", "unknown")})")
    }

    private fun isVideo(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTS
    }

    internal fun selectRequestedFile(files: List<RdFile>, fileIdx: Int?, filePath: String?): RdFile? {
        if (fileIdx == null && filePath.isNullOrBlank()) {
            return files.filter { isVideo(it.path) }.maxByOrNull { it.bytes }
                ?: files.maxByOrNull { it.bytes }
        }
        val byIndex = fileIdx?.let { files.getOrNull(it) }
        val normalizedPath = filePath?.replace('\\', '/')?.trim('/')
        val byPath = normalizedPath?.let { requested ->
            files.firstOrNull { it.path.replace('\\', '/').trim('/').equals(requested, true) }
        }
        if (fileIdx != null && byIndex == null) return null
        if (filePath != null && byPath == null) return null
        if (byIndex != null && byPath != null && byIndex != byPath) return null
        return byIndex ?: byPath
    }

    /**
     * Full resolve: infoHash → direct HTTP URL.
     *
     * Only attempts torrents that are already cached (fast path). Returns
     * null when the hash isn't cached or anything fails — the caller should
     * just skip the stream.
     *
     * Picks the largest video file in the torrent.
     */
    suspend fun resolveCachedToHttp(
        infoHash: String,
        fileIdx: Int? = null,
        filePath: String? = null
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!isCached(infoHash)) return@runCatching null
            val magnet = "magnet:?xt=urn:btih:$infoHash"
            val torrentId = addMagnet(magnet)
            var info = getTorrentInfo(torrentId)
            if (info.status == "waiting_files_selection") {
                val selected = selectRequestedFile(info.files, fileIdx, filePath)
                    ?: return@runCatching null
                selectFiles(torrentId, selected.id.toString())
                info = waitForDownload(torrentId)
            } else if (info.status != "downloaded") {
                info = waitForDownload(torrentId)
            }
            val selected = selectRequestedFile(info.files, fileIdx, filePath)
                ?: return@runCatching null
            // Existing cached torrents may already have several selected files.
            // Match the selected file's position, never blindly take the first URL.
            val link = linkForRequestedFile(info, selected) ?: return@runCatching null
            unrestrictLink(link)
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            null
        }
    }

    internal fun linkForRequestedFile(status: TorrentStatus, requested: RdFile): String? {
        val selected = status.files.filter { it.selected }
        if (selected.size != status.links.size) return null // Split/ambiguous mappings fail closed.
        val index = selected.indexOfFirst { it.id == requested.id }
        return status.links.getOrNull(index)
    }
}

/** Thrown for Real-Debrid API failures (so callers can distinguish from IO errors). */
class RealDebridException(message: String) : Exception(message)
