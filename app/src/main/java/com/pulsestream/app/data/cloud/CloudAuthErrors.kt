package com.pulsestream.app.data.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI

/** Local checks and user-safe translations for Plavio Cloud account requests. */
internal object CloudAuthErrors {
    private const val CONFIG_ERROR =
        "Plavio Cloud is not configured in this app build. Please install a correctly configured build or contact the app maintainer."

    fun configurationError(baseUrl: String, publicKeyConfigured: Boolean): String? {
        val uri = runCatching { URI(baseUrl) }.getOrNull() ?: return CONFIG_ERROR
        val host = uri.host?.lowercase() ?: return CONFIG_ERROR
        val placeholderHost = listOf("placeholder", "your-project", "example.com", "changeme")
            .any { marker -> host.contains(marker) }
        val invalidUrl = !uri.scheme.equals("https", ignoreCase = true) ||
            uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            (uri.rawPath?.let { it.isNotEmpty() && it != "/" } == true)
        if (invalidUrl || placeholderHost || !publicKeyConfigured) return CONFIG_ERROR
        return null
    }

    /** Never echo provider response text: it can be misleading or contain internal details. */
    fun responseMessage(statusCode: Int, responseBody: String, fallback: String): String {
        val providerText = extractProviderText(responseBody).lowercase()
        val apiKeyFailure = providerText.contains("api key") || providerText.contains("apikey") ||
            providerText.contains("invalid key") || providerText.contains("missing key")
        if (apiKeyFailure) {
            return "Plavio Cloud rejected this app's API configuration (HTTP $statusCode). This is not an account password problem; the app maintainer must verify the cloud project and public API configuration."
        }
        if (statusCode == 404) {
            return "Plavio Cloud's authentication endpoint was not found (HTTP 404). The app maintainer must verify the cloud endpoint configuration."
        }
        if (statusCode in 500..599) {
            return "Plavio Cloud authentication is temporarily unavailable (HTTP $statusCode). Please try again later."
        }
        if (providerText.contains("email not confirmed") || providerText.contains("email_not_confirmed")) {
            return "Please verify your email using the link we sent, then try again."
        }
        if (statusCode == 429 || providerText.contains("too many") || providerText.contains("rate limit")) {
            return "Too many attempts. Please wait a little while and try again."
        }
        return fallback
    }

    private fun extractProviderText(body: String): String {
        val json = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return body.take(512)
        return listOf("msg", "message", "error_description", "error", "code")
            .mapNotNull { key -> (json[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
            .joinToString(" ")
    }
}
