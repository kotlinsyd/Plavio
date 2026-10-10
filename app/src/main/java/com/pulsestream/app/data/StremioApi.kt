package com.pulsestream.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** HTTP client for the Stremio addon protocol. */
class StremioApi {

    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Normalizes user input into an addon base URL:
     * accepts a full manifest URL or a bare host, strips /manifest.json and trailing slashes.
     */
    fun normalizeBaseUrl(input: String): String {
        var url = input.trim()
        require(url.isNotEmpty()) { "Please paste an addon URL" }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        url = url.removeSuffix("/manifest.json").trimEnd('/')
        require(url.toHttpUrlOrNull() != null) { "That doesn't look like a valid URL" }
        return url
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PulseStream/0.4.0")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Request failed (HTTP ${response.code})")
            }
            response.body?.string() ?: throw IllegalStateException("Empty response")
        }
    }

    private fun endpoint(baseUrl: String, vararg segments: String): String {
        val builder = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid addon URL")
        segments.forEachIndexed { i, seg ->
            builder.addPathSegment(if (i == segments.lastIndex) "$seg.json" else seg)
        }
        return builder.build().toString()
    }

    suspend fun fetchManifest(baseUrl: String): AddonManifest =
        StremioParser.parseManifest(get("$baseUrl/manifest.json"))

    suspend fun fetchCatalog(baseUrl: String, type: String, catalogId: String): List<MetaPreview> =
        StremioParser.parseCatalog(get(endpoint(baseUrl, "catalog", type, catalogId)), type)

    /**
     * Live catalog search, e.g. {base}/catalog/movie/top/search=avengers.json.
     * Addons that don't support the search extra answer 404 — the caller swallows that.
     */
    suspend fun searchCatalog(
        baseUrl: String,
        type: String,
        catalogId: String,
        query: String
    ): List<MetaPreview> =
        StremioParser.parseCatalog(
            get(endpoint(baseUrl, "catalog", type, catalogId, "search=$query")),
            type
        )

    suspend fun fetchMeta(baseUrl: String, type: String, id: String): MetaDetail? =
        StremioParser.parseMeta(get(endpoint(baseUrl, "meta", type, id)), type)

    suspend fun fetchStreams(baseUrl: String, type: String, videoId: String): StreamResult =
        StremioParser.parseStreams(get(endpoint(baseUrl, "stream", type, videoId)))

    /** Dedicated subtitles endpoint, e.g. {base}/subtitles/movie/tt1234567.json. */
    suspend fun fetchSubtitles(baseUrl: String, type: String, imdbId: String): List<Subtitle> =
        StremioParser.parseSubtitles(get(endpoint(baseUrl, "subtitles", type, imdbId)))
}
