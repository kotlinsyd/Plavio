@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsestream.app.data

import androidx.media3.common.MimeTypes
import okhttp3.Request

/** Format recovery only: bounded requests, normal TLS, and the player's origin header policy. */
object PlaybackContentProbe {
    fun probe(url: String, headers: Map<String, String>): String? = try {
        val client = PlaybackHttp.client(url).newBuilder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).apply {
            PlaybackHeaders.buildPlaybackHeaders(headers, isTv = false).forEach { (key, value) -> header(key, value) }
        }
        // HEAD is cheap, but many IPTV gateways reject it or return generic types.
        val headType = try {
            client.newCall(request.head().build()).execute().use { response ->
                if (response.isSuccessful) recognizedMime(response.header("Content-Type")) else null
            }
        } catch (_: Exception) { null }
        headType ?: run {
            fun inspect(response: okhttp3.Response): String? {
                if (!response.isSuccessful) return null
                return recognizedMime(response.header("Content-Type")) ?: run {
                    val prefix = response.peekBody(4096).string().removePrefix("\uFEFF").trimStart()
                    when {
                        prefix.startsWith("#EXTM3U") -> MimeTypes.APPLICATION_M3U8
                        Regex("""^(?:<\?xml[^>]*>\s*)?<MPD(?:\s|>)""").containsMatchIn(prefix) -> MimeTypes.APPLICATION_MPD
                        else -> null
                    }
                }
            }
            var rangeRejected = false
            val rangedType = client.newCall(request.get().header("Range", "bytes=0-4095").build()).execute().use { response ->
                rangeRejected = response.code in setOf(400, 405, 416)
                inspect(response)
            }
            // Some live gateways reject Range even though an ordinary player GET works.
            // Read at most the same prefix and close immediately; never retry auth failures.
            rangedType ?: if (rangeRejected) {
                client.newCall(request.get().removeHeader("Range").build()).execute().use(::inspect)
            } else null
        }
    } catch (_: Exception) { null }

    internal fun recognizedMime(value: String?): String? {
        val mime = value?.substringBefore(';')?.trim()?.lowercase() ?: return null
        return when (mime) {
            "application/x-mpegurl", "application/vnd.apple.mpegurl", "audio/mpegurl", "audio/x-mpegurl" -> MimeTypes.APPLICATION_M3U8
            "application/dash+xml" -> MimeTypes.APPLICATION_MPD
            "video/mp4", "application/mp4" -> MimeTypes.VIDEO_MP4
            "video/webm" -> MimeTypes.VIDEO_WEBM
            "video/x-matroska" -> MimeTypes.VIDEO_MATROSKA
            else -> null
        }
    }
}
