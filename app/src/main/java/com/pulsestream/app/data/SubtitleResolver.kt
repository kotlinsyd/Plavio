package com.pulsestream.app.data

import androidx.media3.common.MimeTypes
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * A subtitle whose file has been downloaded to local cache with a
 * positively-identified mime type. Side-loaded subtitle URLs rarely carry
 * a file extension (e.g. OpenSubtitles' /download/.../file/12345), so we
 * can't trust the URL — we sniff the actual content instead.
 */
data class ResolvedSubtitle(
    val sub: Subtitle,
    val file: File,
    val mimeType: String
)

private const val SUB_CACHE_DIR = "plavio_subs"
/** Bump when the download logic changes so stale cached files are ignored. */
private const val SUB_CACHE_VERSION = "v2"
private const val SNIFF_BYTES = 256
/**
 * Safety cap for a single subtitle file. Real subtitle files are tiny
 * (a 3-hour movie's SRT is ~100-200KB); 4MB is generous headroom that
 * still protects against a broken server streaming endless data.
 * Files over the cap are rejected, never truncated.
 */
private const val MAX_SUB_BYTES = 4 * 1024 * 1024

/**
 * Downloads every subtitle to the app cache (skipping files already cached)
 * and identifies VTT vs SRT by content. Failures for individual subtitles
 * are skipped, never fatal. Runs fully concurrently.
 */
suspend fun resolveSubtitleFiles(
    subs: List<Subtitle>,
    cacheDir: File
): List<ResolvedSubtitle> = withContext(Dispatchers.IO) {
    val dir = File(cacheDir, SUB_CACHE_DIR).apply { mkdirs() }
    // Drop caches written by older downloader versions (e.g. v1 truncated at 64KB).
    dir.listFiles()?.forEach { f ->
        if (f.isFile && !f.name.startsWith("${SUB_CACHE_VERSION}_")) f.delete()
    }
    subs.map { sub ->
        async {
            try {
                val file = File(dir, "${SUB_CACHE_VERSION}_sub_${sub.url.hashCode().toUInt().toString(16)}")
                if (!file.exists() || file.length() == 0L) {
                    downloadTo(sub.url, file)
                }
                if (!file.exists() || file.length() == 0L) return@async null
                val head = file.inputStream().use { input ->
                    val buf = ByteArray(SNIFF_BYTES)
                    val n = input.read(buf)
                    if (n <= 0) return@async null
                    String(buf, 0, n, Charsets.UTF_8)
                }
                val mime = if (head.trimStart().startsWith("WEBVTT", ignoreCase = true))
                    MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                ResolvedSubtitle(sub, file, mime)
            } catch (e: Exception) {
                null
            }
        }
    }.awaitAll().filterNotNull()
}

private fun downloadTo(url: String, dest: File) {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Plavio/1.0")
        // No Range header: we want the whole file. The byte cap below is
        // only a safety net against runaway servers — over-cap downloads
        // are discarded, never saved truncated.
    }
    try {
        conn.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_SUB_BYTES) {
                        throw java.io.IOException("Subtitle exceeds ${MAX_SUB_BYTES} bytes")
                    }
                    output.write(buf, 0, n)
                }
            }
        }
    } catch (e: Exception) {
        dest.delete()
        throw e
    } finally {
        conn.disconnect()
    }
}
