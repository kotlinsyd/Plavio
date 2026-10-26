package com.pulsestream.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Resilient hand-written parser for the Stremio addon protocol.
 * Addons in the wild vary (resources as strings or objects, missing fields),
 * so we parse JsonElement defensively with defaults instead of @Serializable.
 */
object StremioParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Safe string read: a field that arrives as an object/array (addons in
    // the wild do this) defaults to null instead of jsonPrimitive throwing
    // IllegalArgumentException and aborting the entire parse.
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    /**
     * Reads behaviorHints.proxyHeaders.request — extra HTTP headers an addon
     * requires on the stream URL (used by some debrid-backed addons).
     * Returns an empty map when absent.
    */
    private fun parseProxyHeaders(s: JsonObject, embeddedHeaders: Map<String, String>): Map<String, String> {
        val hints = s["behaviorHints"] as? JsonObject
        val req = (hints?.get("proxyHeaders") as? JsonObject)?.get("request") as? JsonObject
        val proxyHeaders = parseHeaderObject(req)
        // Explicit Stremio header objects retain their existing precedence over
        // proxy headers; both override URL-embedded IPTV headers.
        val merged = linkedMapOf<String, Pair<String, String>>()
        listOf(embeddedHeaders, proxyHeaders, parseHeaderObject(s["headers"] as? JsonObject)).forEach { source ->
            source.forEach { (name, value) -> merged[name.lowercase()] = name to value }
        }
        return merged.values.associate { it }
    }

    /**
     * IPTV playlists/addons often encode request headers as
     * `https://host/live.m3u8|User-Agent=...&Referer=...`. Treat the suffix as
     * headers only when every pair is well-formed; otherwise preserve the URL
     * unchanged rather than guessing at an arbitrary pipe in a URL.
     */
    private fun splitEmbeddedHeaders(rawUrl: String?): Pair<String?, Map<String, String>> {
        if (rawUrl == null) return null to emptyMap()
        val separator = rawUrl.lastIndexOf('|')
        if (separator < 0 || separator == rawUrl.lastIndex) return rawUrl to emptyMap()
        val pairs = rawUrl.substring(separator + 1).split('&')
        val headers = linkedMapOf<String, String>()
        for (pair in pairs) {
            val equals = pair.indexOf('=')
            if (equals <= 0) return rawUrl to emptyMap()
            val name = decodeHeaderComponent(pair.substring(0, equals)) ?: return rawUrl to emptyMap()
            val value = decodeHeaderComponent(pair.substring(equals + 1)) ?: return rawUrl to emptyMap()
            if (!name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) ||
                value.isBlank() || value.any { it == '\r' || it == '\n' || it == '\u0000' }) {
                return rawUrl to emptyMap()
            }
            headers[name] = value
        }
        return rawUrl.substring(0, separator) to headers
    }

    private fun decodeHeaderComponent(value: String): String? =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrNull()

    private fun parseHeaderObject(obj: JsonObject?): Map<String, String> =
        obj?.entries?.mapNotNull { (k, v) ->
            val value = (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            if (value != null) k to value else null
        }?.toMap() ?: emptyMap()

    private fun resourceNames(el: JsonElement): List<String> = when (el) {
        is JsonPrimitive -> listOfNotNull(el.contentOrNull)
        is JsonObject -> listOfNotNull(el.str("name"))
        else -> emptyList()
    }

    fun parseManifest(text: String): AddonManifest {
        val obj = json.parseToJsonElement(text).jsonObject
        val id = obj.str("id") ?: throw IllegalArgumentException("Manifest is missing 'id'")
        val name = obj.str("name") ?: id
        val resources = (obj["resources"] as? JsonArray)
            ?.flatMap { resourceNames(it) } ?: emptyList()
        val catalogs = (obj["catalogs"] as? JsonArray)?.mapNotNull { el ->
            val c = el as? JsonObject ?: return@mapNotNull null
            val type = c.str("type") ?: return@mapNotNull null
            val cid = c.str("id") ?: return@mapNotNull null
            // "extra" declares supported query extras. Stremio shapes vary:
            // strings ("search"), {name:"search"} objects, or {name, options}.
            val extra = (c["extra"] as? JsonArray)?.mapNotNull { x ->
                when (x) {
                    is JsonPrimitive -> x.contentOrNull
                    is JsonObject -> x.str("name")
                    else -> null
                }
            } ?: emptyList()
            CatalogRef(type = type, id = cid, name = c.str("name") ?: cid, extra = extra)
        } ?: emptyList()
        return AddonManifest(
            id = id,
            name = name,
            version = obj.str("version") ?: "",
            description = obj.str("description") ?: "",
            logo = obj.str("logo"),
            types = obj.strList("types"),
            catalogs = catalogs,
            resources = resources,
            configurable = ((obj["behaviorHints"] as? JsonObject)
                ?.get("configurable") as? JsonPrimitive)?.booleanOrNull ?: false
        )
    }

    private fun parseMetaPreview(obj: JsonObject, fallbackType: String): MetaPreview? {
        val id = obj.str("id") ?: return null
        return MetaPreview(
            id = id,
            type = obj.str("type") ?: fallbackType,
            name = obj.str("name") ?: id,
            poster = obj.str("poster"),
            background = obj.str("background"),
            logo = obj.str("logo"),
            description = obj.str("description"),
            releaseInfo = obj.str("releaseInfo"),
            imdbRating = obj.str("imdbRating"),
            genres = obj.strList("genres")
        )
    }

    fun parseCatalog(text: String, fallbackType: String): List<MetaPreview> {
        val obj = json.parseToJsonElement(text).jsonObject
        return (obj["metas"] as? JsonArray)?.mapNotNull { el ->
            (el as? JsonObject)?.let { parseMetaPreview(it, fallbackType) }
        } ?: emptyList()
    }

    fun parseMeta(text: String, fallbackType: String): MetaDetail? {
        val obj = json.parseToJsonElement(text).jsonObject
        val meta = obj["meta"] as? JsonObject ?: return null
        val preview = parseMetaPreview(meta, fallbackType) ?: return null
        val videos = (meta["videos"] as? JsonArray)?.mapNotNull { el ->
            val v = el as? JsonObject ?: return@mapNotNull null
            val vid = v.str("id") ?: return@mapNotNull null
            Video(
                id = vid,
                title = v.str("title") ?: v.str("name") ?: vid,
                season = (v["season"] as? JsonPrimitive)?.intOrNull ?: 0,
                episode = (v["episode"] as? JsonPrimitive)?.intOrNull ?: 0,
                overview = v.str("overview") ?: v.str("description"),
                thumbnail = v.str("thumbnail"),
                released = v.str("released")
            )
        } ?: emptyList()
        return MetaDetail(
            id = preview.id, type = preview.type, name = preview.name,
            poster = preview.poster, background = preview.background, logo = preview.logo,
            description = preview.description, releaseInfo = preview.releaseInfo,
            imdbRating = preview.imdbRating, genres = preview.genres, videos = videos,
            trailerStreams = parseTrailers(meta), cast = parseCast(meta),
            runtime = meta.str("runtime"),
            status = meta.str("status"),
            certification = meta.str("certification") ?: meta.str("contentRating")
                ?: meta.str("mpaaRating"),
            country = meta.str("country") ?: meta.str("originCountry"),
            network = meta.str("network") ?: meta.strList("networks").firstOrNull()
        )
    }

    /**
     * Cast list. PlavioMeta (TMDB mode) serves objects [{id,name,photo}];
     * keyless/Cinemeta mode serves plain name strings. Both are accepted;
     * string entries get id=0 (filmography unavailable).
     */
    private fun parseCast(meta: JsonObject): List<CastMember> {
        val arr = meta["cast"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            when (el) {
                is JsonObject -> {
                    val name = el.str("name") ?: return@mapNotNull null
                    CastMember(
                        id = (el["id"] as? JsonPrimitive)?.intOrNull ?: 0,
                        name = name,
                        photo = el.str("photo")
                    )
                }
                is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                    CastMember(name = it)
                }
                else -> null
            }
        }.take(12)
    }

    /**
     * YouTube trailer references. PlavioMeta serves `trailerStreams`
     * ([{title, ytId}]); the Stremio-standard `trailers` array carries the
     * YouTube id in `source` ([{source, type}]). Both shapes are accepted.
     */
    private fun parseTrailers(meta: JsonObject): List<TrailerStream> {
        val out = linkedMapOf<String, TrailerStream>()
        (meta["trailerStreams"] as? JsonArray)?.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val ytId = o.str("ytId")?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            out.putIfAbsent(ytId, TrailerStream(o.str("title") ?: "", ytId))
        }
        (meta["trailers"] as? JsonArray)?.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val ytId = o.str("source")?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            // Skip non-YouTube sources (ids are 11-char YouTube video ids).
            if (!ytId.matches(Regex("[A-Za-z0-9_-]{11}"))) return@forEach
            out.putIfAbsent(ytId, TrailerStream(o.str("title") ?: "", ytId))
        }
        return out.values.toList()
    }

    fun parseStreams(text: String): StreamResult {
        val obj = json.parseToJsonElement(text).jsonObject
        val streams = (obj["streams"] as? JsonArray)?.mapNotNull { el ->
            val s = el as? JsonObject ?: return@mapNotNull null
            val (url, embeddedHeaders) = splitEmbeddedHeaders(s.str("url"))
            val magnetHash = torrentHashFromMagnet(url)
            val infoHash = s.str("infoHash") ?: magnetHash
            val behaviorHints = s["behaviorHints"] as? JsonObject
            // Keep entries that have either a direct URL or an infoHash
            // (infoHash-only entries can be resolved via TorBox debrid).
            if (url == null && infoHash == null) return@mapNotNull null
            StreamItem(
                // Placeholder URL for infoHash-only entries so dedup still
                // works; StreamRepository replaces it with the real HTTP
                // link once TorBox resolves it.
                url = if (magnetHash != null) "infohash:$infoHash" else url ?: "infohash:$infoHash",
                name = s.str("name"),
                title = s.str("title") ?: s.str("description"),
                headers = parseProxyHeaders(s, embeddedHeaders),
                infoHash = infoHash,
                fileIdx = (s["fileIdx"] as? JsonPrimitive)?.intOrNull
                    ?: ((s["behaviorHints"] as? JsonObject)?.get("fileIdx") as? JsonPrimitive)?.intOrNull,
                filePath = s.str("filePath") ?: s.str("file") ?: s.str("path")
                    ?: s.str("filename") ?: behaviorHints?.str("filePath"),
                mimeType = s.str("mimeType") ?: behaviorHints?.str("mimeType"),
                videoSize = ((s["videoSize"] ?: behaviorHints?.get("videoSize")) as? JsonPrimitive)
                    ?.contentOrNull?.toLongOrNull(),
                filename = s.str("filename") ?: behaviorHints?.str("filename")
            )
        } ?: emptyList()
        val subtitles = (obj["subtitles"] as? JsonArray)?.mapNotNull { el ->
            val s = el as? JsonObject ?: return@mapNotNull null
            val url = s.str("url") ?: return@mapNotNull null
            Subtitle(url = url, lang = s.str("lang") ?: "und")
        } ?: emptyList()
        return StreamResult(streams, subtitles)
    }

    /** Parses a dedicated /subtitles/{type}/{id}.json response. */
    fun parseSubtitles(text: String): List<Subtitle> {
        val obj = json.parseToJsonElement(text).jsonObject
        return (obj["subtitles"] as? JsonArray)?.mapNotNull { el ->
            val s = el as? JsonObject ?: return@mapNotNull null
            val url = s.str("url") ?: return@mapNotNull null
            Subtitle(url = url, lang = s.str("lang") ?: "und")
        } ?: emptyList()
    }

    /** Parses PlavioMeta /related/{type}/{id}.json: { sections: [{id,title,metas}] }. */
    fun parseRelated(text: String, fallbackType: String): List<RelatedSection> {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()
        return ((obj["sections"] as? JsonArray) ?: return emptyList()).mapNotNull { el ->
            val s = el as? JsonObject ?: return@mapNotNull null
            val title = s.str("title") ?: return@mapNotNull null
            val metas = ((s["metas"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { m ->
                (m as? JsonObject)?.let { parseMetaPreview(it, fallbackType) }
            }
            if (metas.isEmpty()) return@mapNotNull null
            RelatedSection(
                id = s.str("id") ?: title,
                title = title,
                metas = metas
            )
        }
    }

    /** Parses PlavioMeta /person/{id}.json: { person, movies, series }. */
    fun parsePerson(text: String): PersonDetail? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return null
        val p = obj["person"] as? JsonObject ?: return null
        val name = p.str("name") ?: return null
        fun metas(key: String, type: String): List<MetaPreview> =
            ((obj[key] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { m ->
                (m as? JsonObject)?.let { parseMetaPreview(it, type) }
            }
        return PersonDetail(
            id = (p["id"] as? JsonPrimitive)?.intOrNull ?: 0,
            name = name,
            photo = p.str("photo"),
            movies = metas("movies", "movie"),
            series = metas("series", "series")
        )
    }

    /**
     * Parses the /person-search/{query}.json extension:
     * { "people": [ { "id": 42802, "name": "...", "photo": "...", "knownFor": "..." } ] }.
     * Returns null when the payload carries no people array at all —
     * the addon doesn't serve the route (an error page or another JSON
     * shape), so the caller tries the next addon. A present-but-empty
     * array is a real answer and returns an empty list.
     */
    fun parsePersonSearch(text: String): List<CastMember>? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return null
        val arr = obj["people"] as? JsonArray ?: return null
        return arr.mapNotNull { el ->
            val p = el as? JsonObject ?: return@mapNotNull null
            val name = p.str("name") ?: return@mapNotNull null
            CastMember(
                id = (p["id"] as? JsonPrimitive)?.intOrNull ?: 0,
                name = name,
                photo = p.str("photo"),
                knownFor = p.str("knownFor")
            )
        }
    }
}
