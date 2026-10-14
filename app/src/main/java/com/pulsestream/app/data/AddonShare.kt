package com.pulsestream.app.data

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One addon inside a share bundle. */
data class SharedAddon(val name: String, val url: String)

/**
 * Addon share bundles: checkbox-select addons → one shareable code.
 * Format: plavio-addons://<base64url(json)> where json is
 * {"v":1,"addons":[{"name":"…","url":"…"}]}.
 * Import accepts the full link or the bare code.
 */
object AddonShare {
    const val SCHEME = "plavio-addons://"

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(addons: List<SharedAddon>): String {
        val obj = buildJsonObject {
            put("v", 1)
            put("addons", buildJsonArray {
                addons.forEach { a ->
                    add(buildJsonObject {
                        put("name", a.name)
                        put("url", a.url)
                    })
                }
            })
        }
        val raw = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(), obj
        )
        val code = Base64.encodeToString(
            raw.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return SCHEME + code
    }

    /** Returns null when the text isn't a valid bundle. */
    fun decode(input: String): List<SharedAddon>? {
        return try {
            val text = extractPayload(input) ?: return null
            val raw = String(
                Base64.decode(text, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            val root = json.parseToJsonElement(raw).jsonObject
            root["addons"]?.jsonArray?.mapNotNull { el ->
                val o = el.jsonObject
                val url = o["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                SharedAddon(
                    name = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: url,
                    url = url
                )
            }?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Finds the bundle payload in free text. The share sheet sends
     * "My Plavio addons (a, b):\nplavio-addons://<code>", and people paste
     * with surrounding whitespace — so the scheme is EXTRACTED from anywhere
     * in the text, not just stripped when it's a prefix. A bare code (no
     * scheme) still works too.
     */
    private fun extractPayload(input: String): String? {
        val idx = input.indexOf(SCHEME, ignoreCase = true)
        val after = if (idx >= 0) input.substring(idx + SCHEME.length) else input
        // Tolerate people pasting with whitespace/newlines, then take the
        // base64url token (stops at the first non-token char of any
        // surrounding message text).
        val token = after.filterNot { it.isWhitespace() }
            .takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        return token.takeIf { it.isNotEmpty() }
    }

    /**
     * Decodes a bundle straight from an incoming VIEW intent (deep link).
     * Null unless the intent carries a plavio-addons:// link — used by
     * MainActivity's onNewIntent wiring for the manifest's intent-filter.
     */
    fun bundleFromIntent(intent: android.content.Intent): List<SharedAddon>? {
        val data = intent.dataString ?: return null
        if (!data.contains(SCHEME, ignoreCase = true)) return null
        return decode(data)
    }
}
