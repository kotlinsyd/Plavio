package com.pulsestream.app.data.debrid

import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for the TorBox API (https://api.torbox.app/v1/api).
 *
 * Debrid-only usage: resolve a cached torrent's infoHash to a direct HTTP
 * download link. No torrent downloading happens in the app — TorBox's
 * servers do the fetching, we just get the HTTP URL.
 *
 * Flow:
 *  1. checkCached(hash) → is the torrent already on TorBox's servers?
 *  2. createTorrent(magnet) → returns a TorBox torrent id
 *  3. waitForDownload(torrentId) → polls until download_finished
 *  4. requestDownloadLink(torrentId, fileId) → direct HTTP URL
 */
class TorBoxApi(private val apiKey: String) {

    companion object {
        private const val BASE = "https://api.torbox.app/v1/api"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** How many times to poll torrent status before giving up. */
        private const val MAX_POLLS = 40
        /** Delay between status polls. */
        private const val POLL_DELAY_MS = 3000L
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

    private fun get(path: String): JSONObject = request(
        auth(Request.Builder().url("$BASE$path")).get().build()
    )

    private fun postForm(path: String, form: FormBody): JSONObject = request(
        auth(Request.Builder().url("$BASE$path")).post(form).build()
    )

    private fun request(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw TorBoxException("TorBox request failed (HTTP ${response.code}): ${bodyStr.take(200)}")
            }
            return JSONObject(bodyStr)
        }
    }

    /**
     * Check whether a torrent (by info hash) is already cached on TorBox's
     * servers. Cached = instant download link, no waiting.
     */
    suspend fun checkCached(infoHash: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val json = get("/torrents/checkcached?hash=${infoHash.lowercase()}&format=list")
            if (!json.optBoolean("success", false)) return@runCatching false
            val data = json.opt("data")
            when (data) {
                is JSONArray -> data.length() > 0
                is JSONObject -> {
                    // keyed by hash: {"<hash>": {...}}
                    data.length() > 0
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    /**
     * Create a torrent from a magnet link. Returns the TorBox torrent id.
     * The torrent starts downloading (or is instantly ready if cached).
     */
    suspend fun createTorrent(magnet: String): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("magnet", magnet)
            .build()
        val json = postForm("/torrents/createtorrent", form)
        if (!json.optBoolean("success", false)) {
            throw TorBoxException("createTorrent failed: ${json.optString("detail", "unknown error")}")
        }
        val data = json.optJSONObject("data")
            ?: throw TorBoxException("createTorrent: missing data")
        // TorBox returns torrent_id in data
        data.optString("torrent_id").takeIf { it.isNotBlank() }
            ?: data.optInt("torrent_id", -1).takeIf { it >= 0 }?.toString()
            ?: throw TorBoxException("createTorrent: missing torrent_id")
    }

    /** Status info for one torrent. */
    data class TorrentStatus(
        val id: String,
        val downloadState: String,
        /** File entries: id + name + size. */
        val files: List<TorrentFile>
    )

    data class TorrentFile(
        val id: Long,
        val name: String,
        val size: Long
    )

    /**
     * Fetch the current status of a torrent.
     */
    suspend fun getTorrentInfo(torrentId: String): TorrentStatus = withContext(Dispatchers.IO) {
        val json = get("/torrents/mylist?id=$torrentId")
        if (!json.optBoolean("success", false)) {
            throw TorBoxException("mylist failed: ${json.optString("detail", "unknown error")}")
        }
        val data = json.optJSONObject("data")
            ?: throw TorBoxException("mylist: missing data")
        val downloadState = data.optString("download_state", "")
        val files = data.optJSONArray("files")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val f = arr.optJSONObject(i) ?: return@mapNotNull null
                TorrentFile(
                    id = f.optLong("id", -1L),
                    name = f.optString("name", ""),
                    size = f.optLong("size", 0L)
                )
            }
        } ?: emptyList()
        TorrentStatus(torrentId, downloadState, files)
    }

    /**
     * Poll until the torrent reaches "download_finished" (or fail after
     * [MAX_POLLS] attempts). Cached torrents finish on the first poll.
     */
    suspend fun waitForDownload(torrentId: String): TorrentStatus = withContext(Dispatchers.IO) {
        var last: TorrentStatus? = null
        repeat(MAX_POLLS) {
            val status = getTorrentInfo(torrentId)
            last = status
            when (status.downloadState) {
                "download_finished" -> return@withContext status
                "download_failed", "download_error" ->
                    throw TorBoxException("Torrent download failed (state=${status.downloadState})")
            }
            delay(POLL_DELAY_MS)
        }
        throw TorBoxException("Timed out waiting for torrent $torrentId (last state=${last?.downloadState})")
    }

    /**
     * Request a direct HTTP download link for a file inside a finished
     * torrent. Returns the URL string.
     */
    suspend fun requestDownloadLink(torrentId: String, fileId: Long): String =
        withContext(Dispatchers.IO) {
            val json = get("/torrents/requestdl?torrent_id=$torrentId&file_id=$fileId&redirect=false")
            if (!json.optBoolean("success", false)) {
                throw TorBoxException("requestdl failed: ${json.optString("detail", "unknown error")}")
            }
            json.optString("data").takeIf { it.isNotBlank() }
                ?: throw TorBoxException("requestdl: missing download URL")
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
    suspend fun resolveCachedToHttp(infoHash: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!checkCached(infoHash)) return@runCatching null
            val magnet = "magnet:?xt=urn:btih:$infoHash"
            val torrentId = createTorrent(magnet)
            val status = waitForDownload(torrentId)
            val video = status.files
                .filter { it.name.isNotBlank() }
                .filter { f ->
                    val n = f.name.lowercase()
                    n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") ||
                        n.endsWith(".webm") || n.endsWith(".mov") || n.endsWith(".m4v")
                }
                .maxByOrNull { it.size }
                ?: status.files.maxByOrNull { it.size }
                ?: return@runCatching null
            requestDownloadLink(torrentId, video.id)
        }.getOrNull()
    }
}

/** Thrown for TorBox API failures (so callers can distinguish from IO errors). */
class TorBoxException(message: String) : Exception(message)
