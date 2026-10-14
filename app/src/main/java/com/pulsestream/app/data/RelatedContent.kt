package com.pulsestream.app.data

import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One "More like this" / "More from <actor>" row from PlavioMeta. */
data class RelatedSection(
    val id: String,
    val title: String,
    val metas: List<MetaPreview>
)

/**
 * PlavioMeta related-content endpoint.
 * GET https://plaviometa.vercel.app/related/:type/:ttid.json
 *   -> { sections: [{ id, title, metas: [{ id, type, name, poster }] }] }
 * Keyless mode returns { sections: [] } — the UI just shows nothing.
 */
object RelatedContent {
    private const val BASE = "https://plaviometa.vercel.app"

    private val client = OkHttpClient.Builder()
        .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(type: String, ttId: String): List<RelatedSection> =
        withContext(Dispatchers.IO) {
            if (!ttId.matches(Regex("^tt\\d+$"))) return@withContext emptyList()
            val url = "$BASE/related/$type/$ttId.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Plavio/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    parse(body, type)
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    // Same defensive read as StremioParser.str: a wrong-typed field
    // defaults instead of jsonPrimitive throwing and killing the parse.
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun parse(body: String, fallbackType: String): List<RelatedSection> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val sections = root["sections"]?.jsonArray ?: return emptyList()
            sections.mapIndexedNotNull { index, el ->
                // One bad section must never kill the whole row set — a
                // failing section just yields nothing, the others render.
                runCatching { parseSection(el, fallbackType) }.getOrNull()
                    // Guarantee unique lazy keys: the server can send two
                    // sections with the same id, which crashed Compose.
                    ?.let { it.copy(id = "${it.id}__$index") }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSection(el: JsonElement, fallbackType: String): RelatedSection? {
        val o = el.jsonObject
        val title = o.str("title") ?: return null
        val metas = o["metas"]?.jsonArray?.mapNotNull { m ->
            val mo = m.jsonObject
            val id = mo.str("id") ?: return@mapNotNull null
            MetaPreview(
                id = id,
                type = mo.str("type") ?: fallbackType,
                name = mo.str("name") ?: id,
                poster = mo.str("poster")
            )
        } ?: emptyList()
        if (metas.isEmpty()) return null
        return RelatedSection(
            id = o.str("id") ?: title,
            title = title,
            metas = metas
        )
    }
}
