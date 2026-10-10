package com.pulsestream.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Manual JSON encode/decode for Room-cached lists. The Stremio models are
 * parsed defensively by StremioParser (not @Serializable), so caching
 * reuses that same shape instead of duplicating the models.
 */
object CatalogCodec {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun MetaPreview.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("type", type)
        put("name", name)
        poster?.let { put("poster", it) }
        background?.let { put("background", it) }
        logo?.let { put("logo", it) }
        description?.let { put("description", it) }
        releaseInfo?.let { put("releaseInfo", it) }
        imdbRating?.let { put("imdbRating", it) }
        putJsonArray("genres") { genres.forEach { add(it) } }
    }

    private fun JsonObject.toMetaPreview(): MetaPreview? {
        fun str(key: String): String? =
            this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val id = str("id") ?: return null
        return MetaPreview(
            id = id,
            type = str("type") ?: "movie",
            name = str("name") ?: id,
            poster = str("poster"),
            background = str("background"),
            logo = str("logo"),
            description = str("description"),
            releaseInfo = str("releaseInfo"),
            imdbRating = str("imdbRating"),
            genres = (this["genres"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        )
    }

    fun encodeMetas(metas: List<MetaPreview>): String =
        buildJsonArray { metas.forEach { add(it.toJson()) } }.toString()

    fun decodeMetas(text: String): List<MetaPreview> =
        runCatching {
            json.parseToJsonElement(text).jsonArray.mapNotNull { it.jsonObject.toMetaPreview() }
        }.getOrDefault(emptyList())

    fun encodeSubtitles(subs: List<Subtitle>): String =
        buildJsonArray {
            subs.forEach { s ->
                addJsonObject {
                    put("url", s.url)
                    put("lang", s.lang)
                }
            }
        }.toString()

    fun decodeSubtitles(text: String): List<Subtitle> =
        runCatching {
            json.parseToJsonElement(text).jsonArray.mapNotNull { el ->
                val o = el.jsonObject
                val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Subtitle(url = url, lang = o["lang"]?.jsonPrimitive?.contentOrNull ?: "und")
            }
        }.getOrDefault(emptyList())
}
