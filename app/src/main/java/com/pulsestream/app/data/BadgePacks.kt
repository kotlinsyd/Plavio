package com.pulsestream.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Stream badge packs (v0.8.31+): image badges matched against a stream's
 * title via regex, in the style of modern streaming clients. Pack JSONs live
 * in app/src/main/assets/badges/.
 */
enum class BadgeStyle(val key: String, val label: String, val assetFile: String) {
    BLACK("black", "Black", "badges/black.json"),
    MONO_WHITE("mono_white", "Mono White", "badges/mono_white.json"),
    WHITE("white", "White", "badges/white.json"),
    SOLID("solid", "Solid", "badges/solid.json"),
    COLORED_OUTLINE("colored_outline", "Colored Outline", "badges/colored_outline.json"),
    TINTED("tinted", "Tinted", "badges/tinted.json"),
    CLASSIC("classic", "Classic", "badges/classic.json");

    companion object {
        fun fromKey(key: String?): BadgeStyle =
            values().firstOrNull { it.key == key } ?: BLACK
    }
}

/** One badge filter: regex-tested against the stream title. */
data class BadgeFilter(
    val id: String,
    val name: String,
    val groupId: String,
    val groupOrder: Int,
    val pattern: Regex?,
    val imageUrl: String
)

/** A loaded badge pack: groups in display order + enabled filters. */
data class BadgePack(
    val key: String,
    val label: String,
    val groupOrder: Map<String, Int>,
    val filters: List<BadgeFilter>
)

/** An imported custom pack. */
data class CustomPackInfo(
    val key: String,
    val name: String,
    val filterCount: Int
)

object BadgePacks {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, BadgePack?>()

    /** Setting-key prefix for imported packs (key = "custom:<filename>"). */
    const val CUSTOM_PREFIX = "custom:"

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** Loads (and caches) a built-in pack from assets. Null when unreadable. */
    fun load(context: Context, style: BadgeStyle): BadgePack? =
        loadByKey(context, style.key)

    /** Loads any pack by setting key (built-in key or "custom:<name>"). */
    fun loadByKey(context: Context, key: String): BadgePack? {
        synchronized(cache) {
            if (cache.containsKey(key)) return cache[key]
        }
        val pack = runCatching {
            if (key.startsWith(CUSTOM_PREFIX)) loadCustom(context, key.removePrefix(CUSTOM_PREFIX))
            else {
                val style = BadgeStyle.fromKey(key)
                val text = context.assets.open(style.assetFile).bufferedReader().use { it.readText() }
                parsePack(text, style.key, style.label)
            }
        }.getOrNull()
        synchronized(cache) { cache[key] = pack }
        return pack
    }

    /** Display label for any pack key. */
    fun labelForKey(context: Context, key: String): String {
        if (key.startsWith(CUSTOM_PREFIX)) {
            return listCustomPacks(context).firstOrNull { it.key == key }?.name
                ?: key.removePrefix(CUSTOM_PREFIX).replace('_', ' ')
        }
        return BadgeStyle.fromKey(key).label
    }

    // ---- Custom packs (app-private storage) ----

    private fun customDir(context: Context) =
        java.io.File(context.filesDir, "badges").apply { mkdirs() }

    /** Validates pack JSON. Returns null when valid, else a user-facing error. */
    fun validatePackJson(text: String): String? {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            return "That file isn't valid JSON."
        }
        if (root["groups"] !is JsonArray) return "Missing the \"groups\" list."
        val filters = root["filters"] as? JsonArray
            ?: return "Missing the \"filters\" list."
        if (filters.isEmpty()) return "No badge filters found in the file."
        val usable = filters.mapNotNull { it as? JsonObject }.count { o ->
            !o.str("pattern").isNullOrBlank() && !o.str("imageURL").isNullOrBlank()
        }
        if (usable == 0) return "No usable badges (each needs a pattern and an imageURL)."
        return null
    }

    /**
     * Imports a custom pack from picked file content.
     * Returns null on success, else a user-facing error message.
     */
    fun importCustomPack(context: Context, fileName: String, text: String): String? {
        validatePackJson(text)?.let { return it }
        val base = fileName.substringBeforeLast('.').take(40)
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .ifBlank { "custom" }
        val dir = customDir(context)
        var file = java.io.File(dir, "$base.json")
        var i = 1
        while (file.exists()) {
            file = java.io.File(dir, "${base}_$i.json")
            i++
        }
        runCatching { file.writeText(text) }.onFailure {
            return "Couldn't save the badge file."
        }
        synchronized(cache) { cache.clear() }
        return null
    }

    /** Lists imported custom packs. */
    fun listCustomPacks(context: Context): List<CustomPackInfo> {
        return (customDir(context).listFiles { _, name -> name.endsWith(".json") }
            ?.mapNotNull { f ->
                val text = runCatching { f.readText() }.getOrNull() ?: return@mapNotNull null
                if (validatePackJson(text) != null) return@mapNotNull null
                val filters = ((json.parseToJsonElement(text).jsonObject)["filters"] as? JsonArray)?.size ?: 0
                CustomPackInfo(
                    key = CUSTOM_PREFIX + f.nameWithoutExtension,
                    name = f.nameWithoutExtension.replace('_', ' '),
                    filterCount = filters
                )
            }?.sortedBy { it.name }) ?: emptyList()
    }

    /** Deletes an imported pack. True when the pack is gone. */
    fun deleteCustomPack(context: Context, key: String): Boolean {
        val file = java.io.File(customDir(context), key.removePrefix(CUSTOM_PREFIX) + ".json")
        val ok = !file.exists() || file.delete()
        synchronized(cache) { cache.clear() }
        return ok
    }

    private fun loadCustom(context: Context, name: String): BadgePack? {
        val file = java.io.File(customDir(context), "$name.json")
        if (!file.exists()) return null
        val text = file.readText()
        validatePackJson(text)?.let { return null }
        return parsePack(text, CUSTOM_PREFIX + name, name.replace('_', ' '))
    }

    private fun parsePack(text: String, key: String, label: String): BadgePack? {
        val root = json.parseToJsonElement(text).jsonObject
        val groups = (root["groups"] as? JsonArray) ?: return null
        val order = mutableMapOf<String, Int>()
        groups.forEachIndexed { i, el ->
            (el as? JsonObject)?.str("id")?.let { order[it] = i }
        }
        val filters = ((root["filters"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            if ((o["isEnabled"] as? JsonPrimitive)?.contentOrNull == "false") return@mapNotNull null
            val pattern = o.str("pattern") ?: return@mapNotNull null
            val imageUrl = o.str("imageURL") ?: return@mapNotNull null
            val groupId = o.str("groupId") ?: ""
            // A few patterns use variable-width lookbehind, which Java's
            // regex engine rejects — those badges simply never match.
            val regex = try { Regex(pattern) } catch (_: Exception) { null }
            BadgeFilter(
                id = o.str("id") ?: imageUrl,
                name = o.str("name") ?: "",
                groupId = groupId,
                groupOrder = order[groupId] ?: Int.MAX_VALUE,
                pattern = regex,
                imageUrl = imageUrl
            )
        }
        return BadgePack(key, label, order, filters)
    }

    /**
     * Matches badge filters against the stream's title/name text.
     * Badges are ordered by group, then by pack order; capped at [max].
     */
    fun match(pack: BadgePack, stream: StreamItem, max: Int = 5): List<BadgeFilter> {
        val haystack = listOfNotNull(stream.title, stream.name).joinToString("\n")
        if (haystack.isBlank()) return emptyList()
        return pack.filters
            .filter { f -> f.pattern?.containsMatchIn(haystack) == true }
            .sortedWith(compareBy({ it.groupOrder }, { pack.filters.indexOf(it) }))
            .distinctBy { it.imageUrl }
            .take(max)
    }
}
