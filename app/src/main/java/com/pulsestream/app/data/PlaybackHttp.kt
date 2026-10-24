@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsestream.app.data

import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Credentials must not follow cross-origin redirects or HLS/DASH subresources. */
object PlaybackHttp {
    private val transportHeaders = setOf("user-agent", "accept", "accept-encoding", "range", "icy-metadata", "cache-control")

    /**
     * One connection pool for every playback client: a connection opened by
     * the detail page's prober or warm-up is still warm when the player
     * opens the same host a moment later (DNS + TLS already done).
     */
    private val sharedPool = okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES)

    internal fun sameOrigin(left: HttpUrl?, right: HttpUrl): Boolean = left != null &&
        left.scheme == right.scheme && left.host == right.host && left.port == right.port

    internal fun scopedRequest(request: okhttp3.Request, origin: HttpUrl?): okhttp3.Request {
        if (sameOrigin(origin, request.url)) return request
        val safe = request.newBuilder()
        request.headers.names().filter { it.lowercase() !in transportHeaders && it.lowercase() != "host" }
            .forEach { safe.removeHeader(it) }
        return safe.build()
    }

    internal fun client(originUrl: String): OkHttpClient {
        val origin = originUrl.toHttpUrlOrNull()
        return OkHttpClient.Builder()
            .connectionPool(sharedPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followSslRedirects(false) // Never downgrade HTTPS to HTTP.
            .addNetworkInterceptor { chain ->
                chain.proceed(scopedRequest(chain.request(), origin))
            }.build()
    }

    /**
     * The player's HTTP data source runs on the same scoped OkHttp client
     * as probing and subtitle downloads: per-stream headers apply to the
     * origin request, and the network interceptor strips everything except
     * transport headers if a redirect or subresource crosses origins.
     */
    fun factory(originUrl: String, headers: Map<String, String>): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(client(originUrl)).setDefaultRequestProperties(headers)
    }

    /**
     * Detail-page warm-up (owner's player round, 2026-10-23): touch the
     * likely first source with a 1-byte range read so its connection sits
     * warm in the shared pool when the player starts. Reads only a few KB
     * even when a host ignores Range and answers with the whole file.
     * Fire-and-forget; every failure is silent.
     */
    suspend fun warmUp(url: String) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-0")
                    .build()
                client(url).newCall(request).execute().use { response ->
                    val source = response.body?.source()
                    if (source != null) {
                        val buffer = ByteArray(4096)
                        runCatching { source.read(buffer) }
                    }
                }
            }
        }
    }
}
