package com.pulsestream.app.data

import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.SocketTimeoutException

/** HTTP client for the Stremio addon protocol. */
class StremioApi {


    companion object {
        /** Host of the locked house addon — the only host that ever gets the gate key. */
        const val LOCKED_ADDON_HOST = "addon.plavio.site"
        const val GATE_HEADER = "x-plavio-key"

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

    private suspend fun get(url: String): String {
        val started = System.nanoTime()
        val host = url.toHttpUrlOrNull()?.host ?: "unknown"
        SourceDiagnostics.record(host, "loading", 0)
        val headers = buildMap {
            put("User-Agent", "Plavio/${BuildConfig.VERSION_NAME}")
            put("Accept", "application/json")
            // Locked house addon (owner's order 2026-10-24): addon.plavio.site
            // serves ONLY this app, so every request to that host carries the
            // gate key. No other host ever sees it.
            if (host == LOCKED_ADDON_HOST) {
                put(GATE_HEADER, BuildConfig.PLAVIO_GATE_KEY)
            }
        }
        return try {
            BoundedHttp.get(
                url = url,
                headers = headers
            ).also {
                SourceDiagnostics.record(host, "succeeded", (System.nanoTime() - started) / 1_000_000)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val outcome = when {
                e is BoundedHttp.HttpFailure && e.statusCode in setOf(401, 403) -> "authentication_needed"
                e is java.io.InterruptedIOException || e.cause is SocketTimeoutException -> "timed_out"
                else -> "failed"
            }
            SourceDiagnostics.record(host, outcome, (System.nanoTime() - started) / 1_000_000)
            throw e
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
     * Genre-filtered catalog, e.g. {base}/catalog/movie/top/genre=Action.json.
     * Only fired at catalogs that declare the "genre" extra — others 404
     * and the caller swallows it.
     */
    suspend fun fetchCatalogGenre(
        baseUrl: String,
        type: String,
        catalogId: String,
        genre: String
    ): List<MetaPreview> =
        StremioParser.parseCatalog(
            get(endpoint(baseUrl, "catalog", type, catalogId, "genre=$genre")),
            type
        )

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
        StremioParser.parseStreams(get(endpoint(baseUrl, "stream", type, videoId))).also { result ->
            if (result.streams.isEmpty()) SourceDiagnostics.record(baseUrl.toHttpUrlOrNull()?.host ?: "unknown", "no_results", 0)
        }

    /** Dedicated subtitles endpoint, e.g. {base}/subtitles/movie/tt1234567.json. */
    suspend fun fetchSubtitles(baseUrl: String, type: String, imdbId: String): List<Subtitle> =
        StremioParser.parseSubtitles(get(endpoint(baseUrl, "subtitles", type, imdbId)))

    /**
     * Related sections, e.g. {base}/related/movie/tt1234567.json — a
     * PlavioMeta extension other metadata addons may also serve. The
     * repository tries every meta addon in preference order, so the
     * rows follow whichever addon serves them (metadata flexibility,
     * owner's order 2026-10-26). Keyless mode returns no sections.
     */
    suspend fun fetchRelated(baseUrl: String, type: String, id: String): List<RelatedSection> =
        StremioParser.parseRelated(get(endpoint(baseUrl, "related", type, id)), type)

    /** Person filmography, e.g. {base}/person/123.json (same extension as [fetchRelated]). */
    suspend fun fetchPerson(baseUrl: String, personId: Int): PersonDetail? =
        StremioParser.parsePerson(get(endpoint(baseUrl, "person", personId.toString())))

    /**
     * Person search by name, e.g. {base}/person-search/salman%20khan.json
     * (same extension family as [fetchPerson]; PlavioMeta serves it since
     * v2.5.7). Returns null when the addon doesn't serve the route —
     * the request failed or the payload has no people array — so the
     * repository can try the next addon. An empty list is a real answer:
     * the addon searched and found nobody.
     */
    suspend fun fetchPersonSearch(baseUrl: String, query: String): List<CastMember>? =
        StremioParser.parsePersonSearch(get(endpoint(baseUrl, "person-search", query)))
}
