package com.pulsestream.app.data.debrid

import android.util.Log
import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
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
 *  3. waitForDownload(torrentId) → polls until TorBox's completion flags are ready
 *  4. requestDownloadLink(torrentId, fileId) → direct HTTP URL
 */
class TorBoxApi(private val apiKey: String) {

    companion object {
        private const val TAG = "TorBoxApi"
        private const val BASE = "https://api.torbox.app/v1/api"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** How many times to poll torrent status before giving up. */
        private const val MAX_POLLS = 40
        /** Delay between status polls. */
        private const val POLL_DELAY_MS = 3000L

        /**
         * Parse the documented mylist payload. Supplying `id` normally returns an
         * object, but tolerate a list only when it contains exactly one matching id.
         */
        internal fun parseTorrentStatus(json: JSONObject, requestedId: String): TorrentStatus {
            if (!json.optBoolean("success", false)) {
                throw TorBoxException("mylist failed: ${json.optString("detail", "unknown error")}")
            }
            val data = json.opt("data")
            val torrent = when (data) {
                is JSONObject -> {
                    val responseId = jsonId(data.opt("id"))
                    if (responseId != null && responseId != requestedId) {
                        throw TorBoxException("mylist returned a different torrent id")
                    }
                    data
                }
                is JSONArray -> {
                    val matches = (0 until data.length()).mapNotNull { i ->
                        val entry = data.optJSONObject(i) ?: return@mapNotNull null
                        if (jsonId(entry.opt("id")) == requestedId) entry else null
                    }
                    when (matches.size) {
                        1 -> matches.single()
                        0 -> throw TorBoxException("mylist array did not contain torrent $requestedId")
                        else -> throw TorBoxException("mylist array contained ambiguous matches for torrent $requestedId")
                    }
                }
                else -> throw TorBoxException("mylist: unexpected data shape")
            }

            val state = torrent.optString("download_state", "")
            val files = torrent.optJSONArray("files")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val file = arr.optJSONObject(i) ?: return@mapNotNull null
                    val id = file.optLong("id", -1L)
                    // `name` is the full relative path when supplied; short_name is
                    // only a fallback so path-based selection is not truncated.
                    val name = listOf("name", "short_name", "shortName", "path")
                        .firstNotNullOfOrNull { key ->
                            file.opt(key)?.takeIf { it is String }?.let { it as String }
                                ?.takeIf { it.isNotBlank() }
                        }.orEmpty()
                    if (id < 0L || name.isBlank()) return@mapNotNull null
                    TorrentFile(id = id, name = name, size = file.optLong("size", 0L), sourceIndex = i)
                }
            } ?: emptyList()

            return TorrentStatus(
                id = requestedId,
                downloadState = state,
                files = files,
                downloadFinished = torrent.booleanOrNull("download_finished"),
                downloadPresent = torrent.booleanOrNull("download_present")
            )
        }

        private fun jsonId(value: Any?): String? = when (value) {
            is String -> value
            is Number -> value.toLong().toString()
            else -> null
        }

        private fun JSONObject.booleanOrNull(key: String): Boolean? =
            opt(key).let { if (it is Boolean) it else null }
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

    private suspend fun get(path: String): JSONObject = request(
        auth(Request.Builder().url("$BASE$path")).get().build()
    )

    private suspend fun postMultipart(path: String, magnet: String): JSONObject {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("magnet", magnet)
            .build()
        return request(
            auth(Request.Builder().url("$BASE$path")).post(body).build()
        )
    }

    private suspend fun postForm(path: String, form: FormBody): JSONObject = request(
        auth(Request.Builder().url("$BASE$path")).post(form).build()
    )

    private suspend fun request(request: Request): JSONObject = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        val bodyStr = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            // Detect auth errors (401/403) specifically for useful diagnostics
                            val isAuth = it.code == 401 || it.code == 403
                            val safeDetail = bodyStr.take(200).replace(apiKey, "[REDACTED]")
                            Log.w(TAG, "TorBox HTTP ${it.code} (auth=$isAuth): $safeDetail")
                            throw TorBoxException(
                                if (isAuth) "TorBox authentication failed (HTTP ${it.code}). Check your API key in Settings → Integrations."
                                else "TorBox request failed (HTTP ${it.code}): $safeDetail"
                            )
                        }
                        JSONObject(bodyStr)
                    }
                } catch (e: Exception) {
                    continuation.resumeWith(Result.failure(e))
                    return
                }
                continuation.resumeWith(Result.success(result))
            }
        })
    }

    /**
     * Check whether a torrent (by info hash) is already cached on TorBox's
     * servers. Cached = instant download link, no waiting.
     *
     * Note: The checkcached endpoint returns { data: ... } without a
     * "success" field (unlike other endpoints), so we check data directly.
     */
    suspend fun checkCached(infoHash: String): Boolean = withContext(Dispatchers.IO) {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.torbox.app")
            .addPathSegments("v1/api/torrents/checkcached")
            .addQueryParameter("hash", infoHash.lowercase())
            .addQueryParameter("format", "list")
            .build()
        val json = request(auth(Request.Builder().url(url)).get().build())
        // checkcached doesn't return "success" field, just check data.
        val data = json.opt("data")
        val cached = when (data) {
            is JSONArray -> data.length() > 0
            is JSONObject -> data.length() > 0
            else -> throw TorBoxException("checkcached returned an unexpected data shape")
        }
        Log.d(TAG, "checkCached $infoHash -> $cached")
        cached
    }

    /**
     * Create a torrent from a magnet link. Returns the TorBox torrent id.
     * The torrent starts downloading (or is instantly ready if cached).
     * Uses multipart form as per TorBox API docs.
     */
    suspend fun createTorrent(magnet: String): String = withContext(Dispatchers.IO) {
        val json = postMultipart("/torrents/createtorrent", magnet)
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
        val files: List<TorrentFile>,
        /** TorBox's documented completion/presence flags; null means not supplied. */
        val downloadFinished: Boolean? = null,
        val downloadPresent: Boolean? = null
    ) {
        /** Prefer authoritative flags; use cached + usable files only for legacy payloads. */
        val isReady: Boolean
            get() = when {
                downloadFinished == true && downloadPresent == true -> true
                downloadFinished != null || downloadPresent != null -> false
                downloadState.equals("cached", ignoreCase = true) && files.isNotEmpty() -> true
                else -> false
            }
    }

    data class TorrentFile(
        val id: Long,
        val name: String,
        val size: Long,
        /** Zero-based source `files` array position; distinct from TorBox's file id. */
        val sourceIndex: Int = -1
    )

    /**
     * Fetch the current status of a torrent.
     */
    suspend fun getTorrentInfo(torrentId: String): TorrentStatus = withContext(Dispatchers.IO) {
        val json = get("/torrents/mylist?id=$torrentId")
        parseTorrentStatus(json, torrentId)
    }

    /**
     * Poll until TorBox's documented completion/presence flags indicate the
     * files are ready (or fail after [MAX_POLLS] attempts). Cached torrents
     * commonly report both flags immediately. State alone is not completion.
     */
    suspend fun waitForDownload(torrentId: String): TorrentStatus = withContext(Dispatchers.IO) {
        var last: TorrentStatus? = null
        repeat(MAX_POLLS) {
            val status = getTorrentInfo(torrentId)
            last = status
            if (status.isReady) return@withContext status
            when (status.downloadState.lowercase()) {
                "download_failed", "download_error", "failed", "error" ->
                    throw TorBoxException("Torrent download failed (state=${status.downloadState})")
            }
            delay(POLL_DELAY_MS)
        }
        throw TorBoxException(
            "Timed out waiting for torrent $torrentId " +
                "(last state=${last?.downloadState}, finished=${last?.downloadFinished}, " +
                "present=${last?.downloadPresent})"
        )
    }

    /**
     * Request a direct HTTP download link for a file inside a finished
     * torrent. Returns the URL string.
     *
     * Per TorBox API docs, requestdl requires the API key as a `token`
     * query parameter (in addition to the Bearer header).
     */
    suspend fun requestDownloadLink(torrentId: String, fileId: Long): String =
        withContext(Dispatchers.IO) {
            val url = requestDownloadUrl(torrentId, fileId)
            // Note: token is in URL, do NOT log the full URL
            Log.d(TAG, "requestDownloadLink: requesting dl for torrent=$torrentId file=$fileId")
            val json = request(
                auth(Request.Builder().url(url).get()).build()
            )
            if (!json.optBoolean("success", false)) {
                val detail = json.optString("detail", "unknown error")
                Log.w(TAG, "requestdl failed for torrent=$torrentId: $detail")
                throw TorBoxException("requestdl failed: $detail")
            }
            json.optString("data").takeIf { it.isNotBlank() }
                ?: throw TorBoxException("requestdl: missing download URL")
        }

    /** Production requestdl URL builder, kept internal for fixture-only tests. */
    internal fun requestDownloadUrl(torrentId: String, fileId: Long): okhttp3.HttpUrl =
        okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.torbox.app")
            .addPathSegments("v1/api/torrents/requestdl")
            .addQueryParameter("token", apiKey)
            .addQueryParameter("torrent_id", torrentId)
            .addQueryParameter("file_id", fileId.toString())
            .addQueryParameter("redirect", "false")
            .build()

    /**
     * Full resolve with detailed result: infoHash → DebridResolveResult.
     *
     * Distinguishes "not cached" (normal) from errors (auth/network).
     * Only attempts torrents that are already cached (fast path).
     * Picks the largest video file in the torrent.
     */
    suspend fun resolveCachedDetailed(
        infoHash: String,
        fileIdx: Int? = null,
        filePath: String? = null
    ): DebridResolveResult = withContext(Dispatchers.IO) {
            try {
                if (!checkCached(infoHash)) {
                    Log.d(TAG, "resolveCached: $infoHash not cached")
                    return@withContext DebridResolveResult.NotCached
                }
                val magnet = "magnet:?xt=urn:btih:$infoHash"
                val torrentId = createTorrent(magnet)
                Log.d(TAG, "resolveCached: created torrent $torrentId for $infoHash")
                val status = waitForDownload(torrentId)
                val video = TorBoxFileSelection.select(status.files, fileIdx, filePath)
                    ?: return@withContext DebridResolveResult.Error(
                        if (fileIdx != null || !filePath.isNullOrBlank()) "Requested torrent file was not found"
                        else "No playable files found in torrent"
                    )
                val url = requestDownloadLink(torrentId, video.id)
                Log.d(TAG, "resolveCached: resolved $infoHash -> [URL received, ${url.length} chars]")
                DebridResolveResult.Resolved(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TorBoxException) {
                val msg = e.message ?: "Unknown TorBox error"
                val isAuth = msg.contains("authentication failed", ignoreCase = true) ||
                    msg.contains("401") || msg.contains("403") ||
                    msg.contains("invalid", ignoreCase = true)
                Log.e(TAG, "resolveCached failed for $infoHash: $msg")
                DebridResolveResult.Error(msg, isAuth)
            } catch (e: Exception) {
                Log.e(TAG, "resolveCached failed for $infoHash", e)
                DebridResolveResult.Error("Network error: ${e.message}")
            }
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
        when (val result = resolveCachedDetailed(infoHash, fileIdx, filePath)) {
            is DebridResolveResult.Resolved -> result.url
            else -> null
        }
    }
}

/** Thrown for TorBox API failures (so callers can distinguish from IO errors). */
class TorBoxException(message: String) : Exception(message)

/**
 * Result of attempting to resolve an infoHash to a direct HTTP URL.
 * Distinguishes "not cached" (normal, skip quietly) from actual errors
 * (auth failure, network issue) so callers can surface diagnostics.
 */
sealed class DebridResolveResult {
    /** Successfully resolved to a direct HTTP URL. */
    data class Resolved(val url: String) : DebridResolveResult()

    /** Torrent is not cached on the debrid service. Normal, skip quietly. */
    data object NotCached : DebridResolveResult()

    /**
     * Resolution failed due to API/auth/network error.
     * message is safe to show (no keys/tokens included).
     */
    data class Error(val message: String, val isAuthError: Boolean = false) : DebridResolveResult()
}
