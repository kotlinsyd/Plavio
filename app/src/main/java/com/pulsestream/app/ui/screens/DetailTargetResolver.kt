package com.pulsestream.app.ui.screens

import com.pulsestream.app.data.StreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DetailTarget(val addonId: String, val type: String, val metaId: String)
data class TmdbReference(val mediaType: String, val tmdbId: String)

/** Accepts both TMDB credit IDs and raw TMDB media IDs, normalizing their type for app routes. */
internal fun parseTmdbReference(metaId: String, type: String): TmdbReference? {
    val match = Regex("^tmdb:(movie|tv|series):([0-9]+)$", RegexOption.IGNORE_CASE).matchEntire(metaId.trim())
        ?: return null
    val mediaType = when (match.groupValues[1].lowercase()) {
        "movie" -> "movie"
        else -> "tv"
    }
    return TmdbReference(mediaType, match.groupValues[2])
}

internal fun normalizeDetailType(type: String): String =
    if (type.equals("tv", ignoreCase = true)) "series" else type.lowercase()

/**
 * Resolve TMDB-native IDs through TMDB external_ids before navigating, shared by search, actor
 * filmography, related titles, and future entry points. If TMDB does not expose an IMDb ID or
 * there is no API key, retain the TMDB ID and use PlavioMeta's detail lookup as a best effort.
 */
suspend fun resolveDetailTarget(
    repository: StreamRepository,
    addonId: String,
    type: String,
    metaId: String
): DetailTarget {
    val reference = parseTmdbReference(metaId, type)
        ?: return DetailTarget(addonId, normalizeDetailType(type), metaId)
    val key = repository.settings.tmdbApiKey?.takeIf { it.isNotBlank() }
    val imdbId = if (key == null) null else withContext(Dispatchers.IO) {
        runCatching {
            val encodedKey = URLEncoder.encode(key, Charsets.UTF_8.name())
            val url = "https://api.themoviedb.org/3/${reference.mediaType}/${reference.tmdbId}/external_ids?api_key=$encodedKey"
            val client = OkHttpClient.Builder()
                .callTimeout(8, TimeUnit.SECONDS)
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) null
                else JSONObject(response.body?.string().orEmpty()).optString("imdb_id").takeIf(String::isNotBlank)
            }
        }.getOrNull()
    }
    val resolvedType = if (reference.mediaType == "movie") "movie" else "series"
    if (imdbId != null) {
        val metaAddon = repository.metaAddonsFor(resolvedType)
        val preferred = metaAddon.firstOrNull { it.id.contains("cinemeta", ignoreCase = true) }
            ?: metaAddon.firstOrNull()
        return DetailTarget(preferred?.id ?: addonId, resolvedType, imdbId)
    }
    return DetailTarget("community.plaviometa", resolvedType, metaId)
}
