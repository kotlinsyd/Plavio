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
import java.net.URI
import java.net.URLDecoder

/** One addon inside a share bundle. */
data class SharedAddon(val name: String, val url: String)

/** A credential-like value found in an addon URL; the value itself is never retained. */
data class AddonShareRisk(val addonName: String, val reasons: List<String>)

/** Result of checking the URLs that would be copied into a share bundle. */
data class AddonShareAudit(val risks: List<AddonShareRisk>) {
    val isSafe: Boolean get() = risks.isEmpty()
}

/** Thrown rather than serializing a URL that appears to contain credentials. */
class UnsafeAddonShareException(val risks: List<AddonShareRisk>) : IllegalArgumentException(
    "Addon sharing was blocked because one or more URLs appear to contain credentials. " +
        "Remove the secret from the URL before sharing."
)

/**
 * Addon share bundles: checkbox-select addons → one shareable code.
 * Format: plavio-addons://<base64url(json)> where json is
 * {"v":1,"addons":[{"name":"…","url":"…"}]}.
 * Import accepts the full link or the bare code.
 */
object AddonShare {
    const val SCHEME = "plavio-addons://"

    private val sensitiveQueryNames = setOf(
        "key", "apikey", "apikeys", "accesskey", "secret", "clientsecret",
        "token", "accesstoken", "refreshtoken", "authtoken", "bearertoken",
        "password", "passwd", "credential", "credentials", "authorization",
        "signature", "sig", "jwt", "session", "privatekey"
    )

    // High-confidence token formats that can appear in paths or unlabelled query parameters.
    private val credentialPatterns = listOf(
        Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*"),
        Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"),
        Regex("\\bAKIA[0-9A-Z]{16}\\b"),
        Regex("\\bgh[pousr]_[A-Za-z0-9]{20,}\\b"),
        Regex("\\bAIza[0-9A-Za-z_-]{30,}\\b"),
        Regex("\\bsk-[A-Za-z0-9_-]{20,}\\b")
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Conservatively checks addon URLs before they are placed in a share code.
     * This is a heuristic, not a secret scanner: avoid putting credentials in
     * addon URLs even when this check does not flag them.
     */
    fun audit(addons: List<SharedAddon>): AddonShareAudit = AddonShareAudit(
        addons.mapNotNull { addon ->
            val reasons = credentialReasons(addon.url)
            reasons.takeIf { it.isNotEmpty() }?.let {
                AddonShareRisk(addon.name.ifBlank { "Unnamed addon" }, it)
            }
        }
    )

    private fun credentialReasons(url: String): List<String> {
        val reasons = linkedSetOf<String>()
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri?.rawUserInfo?.isNotBlank() == true) reasons += "URL user information"
        uri?.rawQuery?.split('&')?.forEach { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            val decodedKey = runCatching { URLDecoder.decode(key, "UTF-8") }
                .getOrDefault(key)
            val normalizedKey = decodedKey.lowercase().filter(Char::isLetterOrDigit)
            if (value.isNotBlank() && (
                    normalizedKey in sensitiveQueryNames ||
                        normalizedKey.endsWith("apikey") ||
                        normalizedKey.endsWith("token") ||
                        normalizedKey.endsWith("secret") ||
                        normalizedKey.endsWith("password") ||
                        normalizedKey.endsWith("credential")
                    )
            ) reasons += "credential-like query parameter"
        }
        if (credentialPatterns.any { it.containsMatchIn(url) }) {
            reasons += "credential-like token pattern"
        }
        return reasons.toList()
    }

    fun encode(addons: List<SharedAddon>): String {
        val audit = audit(addons)
        if (!audit.isSafe) throw UnsafeAddonShareException(audit.risks)
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
