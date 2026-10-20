package com.pulsestream.app.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import com.pulsestream.app.data.PlaybackHeaders
import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.DownloadItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Progressive-file downloader. Adaptive HLS/DASH manifests are explicitly unsupported. */
class DownloadManager private constructor(private val appContext: Context) {
    companion object {
        private const val TAG = "DownloadManager"
        const val LARGE_FILE_WARNING_BYTES = 500L * 1024 * 1024
        private const val STORAGE_RESERVE_BYTES = 32L * 1024 * 1024
        private const val MAX_CONCURRENT_DOWNLOADS = 2
        private const val PREFS = "download_policy"
        private const val KEY_WIFI_ONLY = "wifi_only"

        @Volatile private var instance: DownloadManager? = null
        fun get(context: Context): DownloadManager = instance ?: synchronized(this) {
            instance ?: DownloadManager(context.applicationContext).also { instance = it }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db: AppDatabase by lazy {
        (appContext as? com.pulsestream.app.PulseApp)?.database
            ?: throw IllegalStateException("PulseApp.database not available")
    }
    private val dao get() = db.downloadDao()
    private val prefs by lazy { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            chain.proceed(com.pulsestream.app.data.PlaybackHttp.scopedRequest(chain.request(), chain.call().request().url))
        }
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()
    private val jobs = mutableMapOf<String, Job>()
    private val downloadPermits = Semaphore(MAX_CONCURRENT_DOWNLOADS)
    private val recoveryLock = Any()
    @Volatile private var recoveryRunning = false

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()

    data class DownloadProgress(val id: String, val downloadedBytes: Long, val totalBytes: Long, val status: String) {
        val fraction: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    fun downloadsDir(): File = File(appContext.getExternalFilesDir(null), "downloads").apply { mkdirs() }
    fun observeAll() = dao.observeAll()
    suspend fun getByUrl(url: String): DownloadItem? = dao.getByUrl(url)
    fun isWifiOnlyDownloads(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, false)
    fun setWifiOnlyDownloads(enabled: Boolean) { prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply() }
    fun storagePreflight(sizeBytes: Long?): Boolean =
        sizeBytes == null || sizeBytes <= 0 || DownloadProtocol.fitsWithReserve(
            availableBytes(), sizeBytes, STORAGE_RESERVE_BYTES
        )

    /** Used by confirmation UI; response Content-Type is also checked before saving. */
    fun isAdaptiveDownloadUrl(url: String): Boolean = DownloadProtocol.isAdaptive(decodeUrlForDetection(url))

    suspend fun startDownload(url: String, title: String, headers: Map<String, String> = emptyMap()): String {
        require(!isAdaptiveDownloadUrl(url)) {
            "Offline downloads currently support progressive media files only. HLS/DASH adaptive streams cannot be saved as complete offline downloads."
        }
        val existing = withContext(Dispatchers.IO) { dao.getByUrl(url) }
        if (existing != null && existing.status != "CANCELLED") {
            when (existing.status) {
                "PAUSED", "FAILED" -> resumeDownload(existing.id)
                "QUEUED", "DOWNLOADING", "COMPLETED" -> Unit
            }
            return existing.id
        }
        val id = UUID.randomUUID().toString()
        val fileName = sanitizeFileName(title) + guessExtension(url)
        val file = File(downloadsDir(), "${id}_$fileName")
        val normalizedHeaders = normalizeRequestHeaders(headers)
        val item = DownloadItem(
            id = id, url = url, title = title, fileName = fileName,
            filePath = file.absolutePath,
            headersJson = JSONObject(normalizedHeaders as Map<*, *>).toString(),
            createdAt = System.currentTimeMillis()
        )
        withContext(Dispatchers.IO) { dao.upsert(item) }
        launchDownload(item)
        return id
    }

    fun pauseDownload(id: String) {
        val activeJob = synchronized(jobs) {
            jobs.remove(id).also { it?.cancel() }
        }
        scope.launch {
            activeJob?.join()
            withContext(Dispatchers.IO) {
                val item = dao.getById(id)
                if (item != null && item.status in listOf("DOWNLOADING", "QUEUED")) dao.updateStatus(id, "PAUSED")
            }
            updateProgressFlow(id)
        }
    }

    fun pauseAll() {
        scope.launch {
            val items = withContext(Dispatchers.IO) { dao.getAll().filter { it.status == "DOWNLOADING" || it.status == "QUEUED" } }
            items.forEach { pauseDownload(it.id) }
        }
    }

    fun resumeQueuedDownloads() {
        scope.launch {
            val items = withContext(Dispatchers.IO) { dao.getAll().filter { it.status == "PAUSED" || it.status == "FAILED" || it.status == "QUEUED" } }
            items.forEach { resumeDownload(it.id) }
        }
    }

    fun resumeDownload(id: String) {
        scope.launch {
            val item = withContext(Dispatchers.IO) { dao.getById(id) } ?: return@launch
            if (item.status in listOf("PAUSED", "FAILED", "QUEUED")) {
                withContext(Dispatchers.IO) { dao.updateStatus(id, "QUEUED") }
                launchDownload(item.copy(status = "QUEUED"))
            }
        }
    }

    /** Requeue interrupted jobs after process/service death; paused jobs stay paused. */
    suspend fun recoverInterruptedDownloads() {
        synchronized(recoveryLock) {
            if (recoveryRunning) return
            recoveryRunning = true
        }
        try {
            val activeIds = synchronized(jobs) { jobs.filterValues { it.isActive }.keys.toSet() }
            val interrupted = withContext(Dispatchers.IO) {
                dao.getAll().filter { (it.status == "DOWNLOADING" || it.status == "QUEUED") && it.id !in activeIds }
            }
            for (item in interrupted) {
                withContext(Dispatchers.IO) { dao.updateStatus(item.id, "QUEUED") }
                launchDownload(item.copy(status = "QUEUED"))
            }
        } finally {
            synchronized(recoveryLock) { recoveryRunning = false }
        }
    }

    fun cancelDownload(id: String) {
        val active = synchronized(jobs) { jobs.remove(id).also { it?.cancel() } }
        scope.launch {
            active?.join()
            withContext(Dispatchers.IO) {
                val item = dao.getById(id)
                if (item != null) {
                    runCatching { File(item.filePath).delete() }
                    runCatching { metadataFile(item).delete() }
                    dao.updateStatus(id, "CANCELLED")
                }
            }
            _activeDownloads.value = _activeDownloads.value - id
        }
    }

    suspend fun deleteDownload(id: String) {
        val active = synchronized(jobs) { jobs.remove(id).also { it?.cancel() } }
        active?.join()
        withContext(Dispatchers.IO) {
            val item = dao.getById(id)
            if (item != null) {
                runCatching { File(item.filePath).delete() }
                runCatching { metadataFile(item).delete() }
                dao.deleteById(id)
            }
        }
        _activeDownloads.value = _activeDownloads.value - id
    }

    suspend fun storageUsedBytes(): Long = withContext(Dispatchers.IO) {
        dao.getAll().sumOf { item -> runCatching { File(item.filePath).takeIf { it.exists() }?.length() ?: 0L }.getOrDefault(0L) }
    }

    suspend fun probeSize(url: String, headers: Map<String, String> = emptyMap()): Long = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(url).head()
            normalizeRequestHeaders(headers).forEach { (k, v) -> builder.header(k, v) }
            cancellableExecute(client.newCall(builder.build())).use { response ->
                if (response.isSuccessful) response.header("Content-Length")?.toLongOrNull() ?: -1L else -1L
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.w(TAG, "Size probe failed: ${e.message}"); -1L }
    }

    private fun launchDownload(item: DownloadItem) {
        synchronized(jobs) {
            if (jobs[item.id]?.isActive == true) return
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                put(item.id, DownloadProgress(item.id, item.downloadedBytes, item.totalBytes, "QUEUED"))
            }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    downloadPermits.withPermit {
                        if (isWifiOnlyDownloads() && !isOnWifi()) {
                            withContext(Dispatchers.IO) { dao.updateStatus(item.id, "QUEUED", "Waiting for Wi-Fi") }
                            updateProgressFlow(item.id)
                            _activeDownloads.value = _activeDownloads.value - item.id
                            return@withPermit
                        }
                        withContext(Dispatchers.IO) { dao.updateStatus(item.id, "DOWNLOADING") }
                        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                            put(item.id, DownloadProgress(item.id, item.downloadedBytes, item.totalBytes, "DOWNLOADING"))
                        }
                        downloadFile(item)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed: ${item.id}", e)
                    withContext(Dispatchers.IO) { dao.updateStatus(item.id, "FAILED", e.message?.take(200)) }
                    updateProgressFlow(item.id)
                } finally {
                    synchronized(jobs) { jobs.remove(item.id) }
                }
            }
            jobs[item.id] = job
            job.start()
        }
    }

    private suspend fun downloadFile(item: DownloadItem) {
        if (isAdaptiveDownloadUrl(item.url)) throw IOException("Unsupported offline format: HLS/DASH adaptive streams are not downloaded as complete offline media.")
        val file = File(item.filePath)
        file.parentFile?.mkdirs()
        val headers = runCatching {
            JSONObject(item.headersJson).let { json -> buildMap { json.keys().forEach { k -> put(k, json.getString(k)) } } }
        }.getOrDefault(emptyMap())
        val normalizedHeaders = normalizeRequestHeaders(headers)
        val old = readMetadata(item)
        // Without a strong validator we cannot prove the resource is unchanged.
        val offset = if (file.exists() && DownloadProtocol.ifRange(old.etag, old.lastModified) != null) file.length() else 0L
        performDownload(item, file, normalizedHeaders, offset, old, allowCleanRestart = true)
    }

    private data class ResumeMetadata(val etag: String? = null, val lastModified: String? = null)

    private suspend fun performDownload(
        item: DownloadItem,
        file: File,
        headers: Map<String, String>,
        offset: Long,
        previous: ResumeMetadata,
        allowCleanRestart: Boolean
    ): Unit = coroutineScope {
        val request = Request.Builder().url(item.url).apply {
            headers.forEach { (key, value) -> header(key, value) }
            if (offset > 0) {
                header("Range", "bytes=$offset-")
                DownloadProtocol.ifRange(previous.etag, previous.lastModified)?.let { header("If-Range", it) }
            }
        }.build()
        val call = client.newCall(request)
        val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            val response = withContext(Dispatchers.IO) { call.execute() }
            response.use { resp ->
            val responseType = resp.header("Content-Type")
            if (DownloadProtocol.isAdaptive(item.url, responseType)) {
                throw IOException("Unsupported offline format: this URL serves an HLS/DASH adaptive manifest, not a complete media file.")
            }
            val etag = resp.header("ETag")
            val modified = resp.header("Last-Modified")
            val isPartial = resp.code == 206
            val range = if (isPartial) DownloadProtocol.parseContentRange(resp.header("Content-Range"), offset) else null
            val rangeBytes = range?.let { it.endInclusive - it.start + 1 }
            val bodyLength = resp.body?.contentLength() ?: -1L
            val validatorMismatch = offset > 0 && DownloadProtocol.changedValidator(previous.etag, previous.lastModified, etag, modified)
            val invalidRange = isPartial && (range == null || (rangeBytes != null && bodyLength >= 0 && bodyLength != rangeBytes))

            if (offset > 0 && (!resp.isSuccessful || (isPartial && (invalidRange || validatorMismatch)))) {
                if (allowCleanRestart) {
                    Log.i(TAG, "Resume rejected; restarting ${item.id} from byte zero")
                    resp.close()
                    file.delete()
                    metadataFile(item).delete()
                    return@coroutineScope performDownload(item, file, headers, 0L, ResumeMetadata(), allowCleanRestart = false)
                }
                throw IOException("Server returned an invalid or inconsistent byte range; restart required")
            }
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.message}")
            if (isPartial && (range == null || range.total == null)) throw IOException("Invalid Content-Range; refusing to append response bytes")

            // A successful 200 after a Range request means the server ignored
            // the range (or If-Range detected a changed object): overwrite safely.
            val startByte = if (isPartial) offset else 0L
            val totalBytes = if (isPartial) range?.total ?: -1L else bodyLength
            val body = resp.body ?: throw IOException("Empty response body")
            if (startByte == 0L) {
                val source = body.source()
                source.request(512L)
                val prefix = source.buffer.clone().readByteArray(minOf(512L, source.buffer.size))
                if (DownloadProtocol.isUnsupportedPayload(prefix)) throw IOException("This response is a playlist or error page, not a complete offline video.")
            }
            checkStorageSpace(totalBytes, startByte)
            val freshMetadata = ResumeMetadata(etag ?: previous.etag, modified ?: previous.lastModified)
            writeMetadata(item, freshMetadata)
            withContext(Dispatchers.IO) { dao.upsert(item.copy(totalBytes = totalBytes, downloadedBytes = startByte, status = "DOWNLOADING", error = null)) }

            var downloaded = startByte
            var lastDbUpdate = System.currentTimeMillis()
            RandomAccessFile(file, "rw").use { raf ->
                if (startByte == 0L) raf.setLength(0L)
                raf.seek(startByte)
                val buffer = ByteArray(256 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (isWifiOnlyDownloads() && !isOnWifi()) {
                            withContext(Dispatchers.IO) { dao.updateStatus(item.id, "PAUSED", "Wi-Fi disconnected; resume when Wi-Fi is available") }
                            _activeDownloads.value = _activeDownloads.value - item.id
                            return@coroutineScope
                        }
                        val count = input.read(buffer)
                        if (count == -1) break
                        if (availableBytes() < count + STORAGE_RESERVE_BYTES) throw IOException("Not enough free storage to continue this download.")
                        raf.write(buffer, 0, count)
                        downloaded += count
                        val now = System.currentTimeMillis()
                        if (now - lastDbUpdate > 500) {
                            lastDbUpdate = now
                            val bytesNow = downloaded
                            withContext(Dispatchers.IO) { dao.updateProgress(item.id, bytesNow, "DOWNLOADING") }
                            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                                put(item.id, DownloadProgress(item.id, bytesNow, totalBytes, "DOWNLOADING"))
                            }
                        }
                    }
                }
                raf.fd.sync()
            }
            if (downloaded == startByte) throw IOException("The server returned no media bytes")
            if (bodyLength >= 0 && downloaded - startByte != bodyLength) throw IOException("Incomplete response body (${downloaded - startByte} of $bodyLength bytes)")
            if (range != null && downloaded != range.endInclusive + 1) throw IOException("Incomplete range response ending at byte ${downloaded - 1}")
            if (totalBytes > 0 && downloaded < totalBytes) throw IOException("Incomplete download: $downloaded / $totalBytes bytes")
            withContext(Dispatchers.IO) {
                // Room's markCompleted copies totalBytes into downloadedBytes;
                // persist the measured size first when the server gave no length.
                dao.upsert(item.copy(totalBytes = downloaded, downloadedBytes = downloaded, status = "DOWNLOADING", error = null))
                dao.markCompleted(item.id, System.currentTimeMillis())
            }
            _activeDownloads.value = _activeDownloads.value - item.id
            Log.i(TAG, "Download completed: ${item.id} ($downloaded bytes)")
            }
        } finally {
            cancellationWatcher.cancel()
        }
    }

    private suspend fun cancellableExecute(call: okhttp3.Call): Response = coroutineScope {
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try { withContext(Dispatchers.IO) { call.execute() } }
        finally { watcher.cancel() }
    }

    private fun isOnWifi(): Boolean = try {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (_: Exception) { false }

    private fun availableBytes(): Long = runCatching { StatFs(downloadsDir().absolutePath).availableBytes }.getOrDefault(0L)
    private fun checkStorageSpace(totalBytes: Long, offset: Long) {
        val remainingPayload = if (totalBytes > offset) totalBytes - offset else 0L
        val available = availableBytes()
        if (!DownloadProtocol.fitsWithReserve(available, remainingPayload, STORAGE_RESERVE_BYTES)) {
            throw IOException("Not enough free storage for this download (${formatBytes(available)} available).")
        }
    }

    private fun metadataFile(item: DownloadItem) = File(item.filePath + ".resume.json")
    private fun readMetadata(item: DownloadItem): ResumeMetadata = runCatching {
        val json = JSONObject(metadataFile(item).readText())
        ResumeMetadata(json.optString("etag").takeIf { it.isNotBlank() }, json.optString("lastModified").takeIf { it.isNotBlank() })
    }.getOrDefault(ResumeMetadata())
    private fun writeMetadata(item: DownloadItem, metadata: ResumeMetadata) {
        runCatching {
            metadataFile(item).writeText(JSONObject().put("etag", metadata.etag).put("lastModified", metadata.lastModified).toString())
        }.onFailure { Log.w(TAG, "Could not store download validators") }
    }

    private suspend fun updateProgressFlow(id: String) {
        val item = withContext(Dispatchers.IO) { dao.getById(id) } ?: run { _activeDownloads.value -= id; return }
        if (item.status in listOf("COMPLETED", "CANCELLED", "PAUSED", "FAILED")) _activeDownloads.value -= id
        else _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            put(id, DownloadProgress(id, File(item.filePath).takeIf { it.exists() }?.length() ?: item.downloadedBytes, item.totalBytes, item.status))
        }
    }

    private fun decodeUrlForDetection(url: String): String = runCatching {
        URLDecoder.decode(url, StandardCharsets.UTF_8.name())
    }.getOrDefault(url)

    private fun normalizeRequestHeaders(headers: Map<String, String>): Map<String, String> {
        val clean = LinkedHashMap<String, String>()
        headers.forEach { (rawKey, rawValue) ->
            val key = rawKey.trim()
            val value = rawValue.trim()
            if (key.isNotEmpty() && value.isNotEmpty() && !key.equals("Range", ignoreCase = true)) {
                clean[key] = value
            }
        }
        return PlaybackHeaders.buildPlaybackHeaders(clean, isTv = false)
            .mapKeys { it.key.trim() }
            .mapValues { it.value.trim() }
            .filter { (key, value) -> key.isNotEmpty() && value.isNotEmpty() }
    }

    private fun sanitizeFileName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "download" }
    private fun guessExtension(url: String): String {
        val path = url.substringBefore("?").substringAfterLast("/")
        val ext = path.substringAfterLast(".", "")
        return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) ".$ext" else ".mp4"
    }
    fun formatBytes(bytes: Long): String = when {
        bytes < 0 -> "Unknown"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
        else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
    }

    fun shutdown() { scope.cancel(); instance = null }
}
