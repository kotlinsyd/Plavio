package com.pulsestream.app.data.download

import android.content.Context
import android.util.Log
import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Offline download engine with pause/resume support.
 *
 * Uses HTTP Range requests: on resume, sends `Range: bytes=<downloaded>-`
 * and appends to the partial file. If the server doesn't support ranges
 * (no 206 response), restarts from scratch.
 *
 * Downloads go to app-private external files dir ("downloads") — no
 * storage permission needed on Android 10+, and files survive app updates.
 */
class DownloadManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "DownloadManager"
        const val LARGE_FILE_WARNING_BYTES = 500L * 1024 * 1024 // 500MB

        @Volatile
        private var instance: DownloadManager? = null

        fun get(context: Context): DownloadManager =
            instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db: AppDatabase by lazy {
        // Reuse the app's database instance via PulseApp
        (appContext as? com.pulsestream.app.PulseApp)?.database
            ?: throw IllegalStateException("PulseApp.database not available")
    }
    private val dao get() = db.downloadDao()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()

    /** Active download jobs keyed by download id. */
    private val jobs = mutableMapOf<String, Job>()

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    /** Live progress for active downloads: id -> progress. */
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()

    data class DownloadProgress(
        val id: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val status: String
    ) {
        val fraction: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    fun downloadsDir(): File =
        File(appContext.getExternalFilesDir(null), "downloads").apply { mkdirs() }

    fun observeAll() = dao.observeAll()

    suspend fun getByUrl(url: String): DownloadItem? = dao.getByUrl(url)

    /**
     * Starts a new download. Returns the download id.
     * If a download for this URL already exists and isn't cancelled, returns its id.
     */
    suspend fun startDownload(
        url: String,
        title: String,
        headers: Map<String, String> = emptyMap()
    ): String {
        val existing = withContext(Dispatchers.IO) { dao.getByUrl(url) }
        if (existing != null && existing.status != "CANCELLED") {
            // Resume if paused/failed, or just return if already active/done
            when (existing.status) {
                "PAUSED", "FAILED" -> resumeDownload(existing.id)
                "QUEUED", "DOWNLOADING" -> { /* already going */ }
                "COMPLETED" -> { /* already done */ }
            }
            return existing.id
        }

        val id = UUID.randomUUID().toString()
        val fileName = sanitizeFileName(title) + guessExtension(url)
        val file = File(downloadsDir(), "${id}_$fileName")
        val item = DownloadItem(
            id = id,
            url = url,
            title = title,
            fileName = fileName,
            filePath = file.absolutePath,
            headersJson = JSONObject(headers as Map<*, *>).toString(),
            createdAt = System.currentTimeMillis()
        )
        withContext(Dispatchers.IO) { dao.upsert(item) }
        launchDownload(item)
        return id
    }

    fun pauseDownload(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        scope.launch {
            withContext(Dispatchers.IO) {
                val item = dao.getById(id)
                if (item != null && (item.status == "DOWNLOADING" || item.status == "QUEUED")) {
                    dao.updateStatus(id, "PAUSED")
                }
            }
            updateProgressFlow(id)
        }
    }

    fun resumeDownload(id: String) {
        scope.launch {
            val item = withContext(Dispatchers.IO) { dao.getById(id) } ?: return@launch
            if (item.status == "PAUSED" || item.status == "FAILED" || item.status == "QUEUED") {
                launchDownload(item)
            }
        }
    }

    fun cancelDownload(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        scope.launch {
            withContext(Dispatchers.IO) {
                val item = dao.getById(id)
                if (item != null) {
                    runCatching { File(item.filePath).delete() }
                    dao.updateStatus(id, "CANCELLED")
                }
            }
            _activeDownloads.value = _activeDownloads.value - id
        }
    }

    suspend fun deleteDownload(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        withContext(Dispatchers.IO) {
            val item = dao.getById(id)
            if (item != null) {
                runCatching { File(item.filePath).delete() }
                dao.deleteById(id)
            }
        }
        _activeDownloads.value = _activeDownloads.value - id
    }

    /** Total bytes used by completed + partial downloads. */
    suspend fun storageUsedBytes(): Long = withContext(Dispatchers.IO) {
        dao.getAll().sumOf { item ->
            runCatching { File(item.filePath).takeIf { it.exists() }?.length() ?: 0L }.getOrDefault(0L)
        }
    }

    /**
     * HEAD request to get the file size without downloading.
     * Returns -1 if unknown.
     */
    suspend fun probeSize(url: String, headers: Map<String, String> = emptyMap()): Long =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(url).head()
                headers.forEach { (k, v) -> builder.header(k, v) }
                client.newCall(builder.build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    } else -1L
                }
            } catch (e: Exception) {
                Log.w(TAG, "Size probe failed: ${e.message}")
                -1L
            }
        }

    private fun launchDownload(item: DownloadItem) {
        jobs[item.id]?.cancel()
        val job = scope.launch {
            withContext(Dispatchers.IO) { dao.updateStatus(item.id, "DOWNLOADING") }
            // Emit immediately so UI shows active state right away
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                put(item.id, DownloadProgress(item.id, 0L, -1L, "DOWNLOADING"))
            }
            try {
                downloadFile(item)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Download failed: ${item.id}", e)
                withContext(Dispatchers.IO) {
                    dao.updateStatus(item.id, "FAILED", e.message?.take(200))
                }
                updateProgressFlow(item.id)
            } finally {
                jobs.remove(item.id)
            }
        }
        jobs[item.id] = job
    }

    private suspend fun downloadFile(item: DownloadItem) {
        val file = File(item.filePath)
        val existingBytes = if (file.exists()) file.length() else 0L
        val headers = runCatching {
            JSONObject(item.headersJson).let { json ->
                buildMap {
                    json.keys().forEach { k -> put(k, json.getString(k)) }
                }
            }
        }.getOrDefault(emptyMap())

        val requestBuilder = Request.Builder().url(item.url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        // Resume from where we left off
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val supportsResume = response.code == 206
            val startByte: Long
            val totalBytes: Long

            if (response.code == 206) {
                // Partial content — server supports resume
                startByte = existingBytes
                val contentRange = response.header("Content-Range") // bytes 100-999/1234
                totalBytes = contentRange?.substringAfterLast("/")?.toLongOrNull()
                    ?: (existingBytes + (response.body?.contentLength() ?: -1L))
            } else if (response.isSuccessful) {
                // Full content — either fresh start or server ignores Range
                if (existingBytes > 0) {
                    // Server doesn't support resume; restart from scratch
                    Log.i(TAG, "Server doesn't support Range, restarting download ${item.id}")
                    file.delete()
                }
                startByte = 0L
                totalBytes = response.body?.contentLength() ?: -1L
            } else {
                throw java.io.IOException("HTTP ${response.code}: ${response.message}")
            }

            withContext(Dispatchers.IO) {
                dao.upsert(item.copy(totalBytes = totalBytes, downloadedBytes = startByte))
            }

            val body = response.body ?: throw java.io.IOException("Empty response body")
            var downloaded = startByte
            var lastDbUpdate = System.currentTimeMillis()

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(startByte)
                // 256KB buffer for faster throughput on modern networks
                val buffer = ByteArray(256 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        raf.write(buffer, 0, read)
                        downloaded += read

                        // Throttle DB + flow updates to ~2/sec
                        val now = System.currentTimeMillis()
                        if (now - lastDbUpdate > 500) {
                            lastDbUpdate = now
                            val d = downloaded
                            withContext(Dispatchers.IO) {
                                dao.updateProgress(item.id, d, "DOWNLOADING")
                            }
                            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                                put(item.id, DownloadProgress(item.id, d, totalBytes, "DOWNLOADING"))
                            }
                        }

                        // Note: job cancellation interrupts the blocking read,
                        // which throws and is handled by the caller.
                    }
                }
            }

            // Verify completion
            if (totalBytes > 0 && downloaded < totalBytes) {
                throw java.io.IOException("Incomplete download: $downloaded / $totalBytes bytes")
            }

            withContext(Dispatchers.IO) {
                dao.markCompleted(item.id, System.currentTimeMillis())
            }
            _activeDownloads.value = _activeDownloads.value - item.id
            Log.i(TAG, "Download completed: ${item.id} ($downloaded bytes)")
        }
    }

    private suspend fun updateProgressFlow(id: String) {
        val item = withContext(Dispatchers.IO) { dao.getById(id) } ?: run {
            _activeDownloads.value = _activeDownloads.value - id
            return
        }
        if (item.status == "COMPLETED" || item.status == "CANCELLED") {
            _activeDownloads.value = _activeDownloads.value - id
        } else {
            _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                put(id, DownloadProgress(id, item.downloadedBytes, item.totalBytes, item.status))
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80)
        return clean.ifBlank { "download" }
    }

    private fun guessExtension(url: String): String {
        val path = url.substringBefore("?").substringAfterLast("/")
        val ext = path.substringAfterLast(".", "")
        return when {
            ext.length in 2..4 && ext.all { it.isLetterOrDigit() } -> ".$ext"
            url.contains(".m3u8", ignoreCase = true) -> ".m3u8"
            else -> ".mp4"
        }
    }

    fun formatBytes(bytes: Long): String = when {
        bytes < 0 -> "Unknown"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
        else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
    }

    /** Call on app shutdown (tests). */
    fun shutdown() {
        scope.cancel()
        instance = null
    }
}
