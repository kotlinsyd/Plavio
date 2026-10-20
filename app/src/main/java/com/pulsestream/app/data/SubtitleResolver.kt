package com.pulsestream.app.data

import androidx.media3.common.MimeTypes
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A subtitle downloaded to the local cache and positively identified by content. */
data class ResolvedSubtitle(
    val sub: Subtitle,
    val file: File,
    val mimeType: String
)

private const val SUB_CACHE_DIR = "plavio_subs"
/** Bump when the download logic changes so stale cached files are ignored. */
private const val SUB_CACHE_VERSION = "v3"
private const val SNIFF_BYTES = 4096
private const val MAX_SUB_BYTES = 4 * 1024 * 1024

/**
 * Downloads side-loaded subtitles concurrently. Request headers are accepted only
 * after normal playback-header validation; PlaybackHttp strips credentials on
 * cross-origin requests/redirects, matching the player’s security boundary.
 * Invalid HTML/JSON/error bodies are rejected rather than mislabelled as SRT.
 */
suspend fun resolveSubtitleFiles(
    subs: List<Subtitle>,
    cacheDir: File,
    requestHeaders: Map<String, String> = emptyMap(),
    streamOriginUrl: String? = null,
    isTv: Boolean = false
): List<ResolvedSubtitle> = withContext(Dispatchers.IO) {
    val dir = File(cacheDir, SUB_CACHE_DIR).apply { mkdirs() }
    dir.listFiles()?.forEach { f ->
        if (f.isFile && !f.name.startsWith("${SUB_CACHE_VERSION}_")) f.delete()
    }
    val safeHeaders = PlaybackHeaders.buildPlaybackHeaders(requestHeaders, isTv)
    val client = if (!streamOriginUrl.isNullOrBlank()) {
        PlaybackHttp.client(streamOriginUrl)
    } else {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .build()
    }
    subs.map { sub ->
        async {
            try {
                // Include a digest of the header set so a cached subtitle from a
                // previous account/session cannot be reused for the same URL.
                val identity = sub.url + "\n" + safeHeaders.toSortedMap().entries.joinToString("\n") { "${it.key}:${it.value}" }
                val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                    .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val file = File(dir, "${SUB_CACHE_VERSION}_sub_$digest")
                if (!file.exists() || file.length() == 0L) downloadTo(sub.url, file, client, safeHeaders)
                if (!file.exists() || file.length() == 0L) return@async null
                val head = file.inputStream().use { input ->
                    val buf = ByteArray(SNIFF_BYTES)
                    val n = input.read(buf)
                    if (n <= 0) return@async null
                    String(buf, 0, n, Charsets.UTF_8).removePrefix("\uFEFF")
                }
                val mime = detectSubtitleMimeType(head) ?: run { file.delete(); return@async null }
                ResolvedSubtitle(sub, file, mime)
            } catch (_: Exception) {
                null
            }
        }
    }.awaitAll().filterNotNull()
}

/** Recognize actual subtitle syntax, not merely “anything that isn't WEBVTT”. */
internal fun detectSubtitleMimeType(content: String): String? {
    val text = content.removePrefix("\uFEFF").trimStart()
    if (text.startsWith("WEBVTT", ignoreCase = true) &&
        (text.length == 6 || text[6].isWhitespace() || text[6] == '\n' || text[6] == '\r')) {
        return MimeTypes.TEXT_VTT
    }
    val srtTimestamp = Regex("(?m)^\\s*\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}\\s+-->\\s*\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}")
    val numberedSrt = Regex("(?m)^\\s*\\d+\\s*\\r?\\n\\s*\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}\\s+-->")
    return if (srtTimestamp.containsMatchIn(text) || numberedSrt.containsMatchIn(text)) MimeTypes.APPLICATION_SUBRIP else null
}

private fun downloadTo(
    url: String,
    dest: File,
    client: OkHttpClient,
    headers: Map<String, String>
) {
    val request = Request.Builder().url(url).apply {
        headers.forEach { (name, value) -> header(name, value) }
    }.get().build()
    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Subtitle HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty subtitle response")
            if (body.contentLength() > MAX_SUB_BYTES) throw IOException("Subtitle exceeds size limit")
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_SUB_BYTES) throw IOException("Subtitle exceeds size limit")
                        output.write(buf, 0, n)
                    }
                }
            }
        }
    } catch (e: Exception) {
        dest.delete()
        throw e
    }
}
