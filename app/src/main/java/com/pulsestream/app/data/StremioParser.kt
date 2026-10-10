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

/**
 * Resilient hand-written parser for the Stremio addon protocol.
 * Addons in the wild vary (resources as strings or objects, missing fields),
 * so we parse JsonElement defensively with defaults instead of @Serializable.
 */
object StremioParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

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
            CatalogRef(type = type, id = cid, name = c.str("name") ?: cid)
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
            configurable = (obj["behaviorHints"] as? JsonObject)
                ?.get("configurable")?.jsonPrimitive?.booleanOrNull ?: false
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
                season = v["season"]?.jsonPrimitive?.intOrNull ?: 0,
                episode = v["episode"]?.jsonPrimitive?.intOrNull ?: 0,
                overview = v.str("overview") ?: v.str("description"),
                thumbnail = v.str("thumbnail"),
                released = v.str("released")
            )
        } ?: emptyList()
        return MetaDetail(
            id = preview.id, type = preview.type, name = preview.name,
            poster = preview.poster, background = preview.background, logo = preview.logo,
            description = preview.description, releaseInfo = preview.releaseInfo,
            imdbRating = preview.imdbRating, genres = preview.genres, videos = videos
        )
    }

    fun parseStreams(text: String): StreamResult {
        val obj = json.parseToJsonElement(text).jsonObject
        val streams = (obj["streams"] as? JsonArray)?.mapNotNull { el ->
            val s = el as? JsonObject ?: return@mapNotNull null
            val url = s.str("url") ?: return@mapNotNull null // skip p2p/infoHash-only entries
            StreamItem(
                url = url,
                name = s.str("name"),
                title = s.str("title") ?: s.str("description")
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
}
