package com.pulsestream.app.data

import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** HTTP client for the Stremio addon protocol. */
class StremioApi {

    // OOM hotfix (v0.8.28-tv): cap concurrent requests and pin HTTP/1.1 on
    // low-memory devices. HTTP/2 multiplexes every stream through okio segment
    // buffers; with many parallel catalog fetches on a 48MB-heap TV this
    // exhausts memory and crashes (see crash report: OutOfMemoryError in
    // Http2Connection).
    //
    // v0.8.29 fix: the caps must be heap-aware. The aggressive 8/4 limits
    // caused "No playable sources" on phones: streamResultsFlow fires one
    // request per addon concurrently, each wrapped in withTimeoutOrNull(20s)
    // which counts dispatcher QUEUE time too. On cold start the queue backs
    // up, requests time out before even starting, and the first attempt shows
    // empty while a manual retry (warm, less contention) succeeds.
    // Phones have ample heap — give them room; keep TVs constrained.
    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .dispatcher(Dispatcher().apply {
            // Runtime.maxMemory is in bytes; 128MB threshold separates
            // constrained TVs from phones.
            val lowMem = Runtime.getRuntime().maxMemory() < 128L * 1024 * 1024
            maxRequests = if (lowMem) 8 else 32
            maxRequestsPerHost = if (lowMem) 4 else 16
        })
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    companion object {
        /**
         * Normalizes user input into an addon base URL:
         * accepts a full manifest URL or a bare host, strips /manifest.json and trailing slashes.
         *
         * Query strings are preserved: many addons carry their config there
         * (e.g. /manifest.json?language=English), and dropping the query would
         * lose the user's configuration. Idempotent — safe to call on an
         * already-normalized URL (also repairs base URLs stored by older builds
         * that kept a stray /manifest.json in the path).
         */
        fun normalizeBaseUrl(input: String): String {
            var url = input.trim()
            require(url.isNotEmpty()) { "Please paste an addon URL" }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            val parsed = url.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("That doesn't look like a valid URL")
            val segments = parsed.pathSegments.filter { it.isNotEmpty() }
            val stripped = if (segments.lastOrNull() == "manifest.json") segments.dropLast(1) else segments
            return parsed.newBuilder().apply {
                encodedPath("/")
                stripped.forEach { addPathSegment(it) }
            }.build().toString().trimEnd('/')
        }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Plavio/${BuildConfig.VERSION_NAME}")
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
        // Normalize first: repairs base URLs stored by older builds that kept a
        // stray /manifest.json in the path (query-string addon configs).
        val clean = normalizeBaseUrl(baseUrl)
        val builder = clean.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid addon URL")
        segments.forEachIndexed { i, seg ->
            builder.addPathSegment(if (i == segments.lastIndex) "$seg.json" else seg)
        }
        return builder.build().toString()
    }

    suspend fun fetchManifest(baseUrl: String): AddonManifest {
        // Build with HttpUrl so a query-string addon config stays in the query
        // (plain string concatenation would append after the "?").
        val url = normalizeBaseUrl(baseUrl).toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("manifest.json")?.build()?.toString()
            ?: throw IllegalArgumentException("Invalid addon URL")
        return StremioParser.parseManifest(get(url))
    }

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

    /**
     * PlavioMeta related sections, e.g. {base}/related/movie/tt1234567.json.
     * Keyless mode returns no sections — the caller shows nothing then.
     */
    suspend fun fetchRelated(baseUrl: String, type: String, id: String): List<RelatedSection> =
        StremioParser.parseRelated(get(endpoint(baseUrl, "related", type, id)), type)

    /** PlavioMeta person filmography, e.g. {base}/person/123.json. */
    suspend fun fetchPerson(baseUrl: String, personId: Int): PersonDetail? =
        StremioParser.parsePerson(get(endpoint(baseUrl, "person", personId.toString())))
}
