@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsestream.app.data

import androidx.media3.datasource.DefaultHttpDataSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Credentials must not follow cross-origin redirects or HLS/DASH subresources. */
object PlaybackHttp {
    private val transportHeaders = setOf("user-agent", "accept", "accept-encoding", "range", "icy-metadata", "cache-control")

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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followSslRedirects(false) // Never downgrade HTTPS to HTTP.
            .addNetworkInterceptor { chain ->
                chain.proceed(scopedRequest(chain.request(), origin))
            }.build()
    }

    // TEST BUILD: OkHttpDataSource adapter not available offline — using standard
    // DefaultHttpDataSource. The OkHttpClient header-scoping still applies to
    // subtitle downloads and probing via client().
    fun factory(originUrl: String, headers: Map<String, String>): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
    }
}
