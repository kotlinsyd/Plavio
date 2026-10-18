package com.pulsestream.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * TMDB API client for enriching cast data with photos and IDs.
 * Used when user has provided a TMDB API key in Integrations.
 */
object TmdbClient {
    private const val BASE = "https://api.themoviedb.org/3"
    private const val IMG_BASE = "https://image.tmdb.org/t/p/w185"
    
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Enrich cast members with TMDB photos and IDs.
     * Takes a Stremio/IMDb ID string and resolves it to TMDB ID first.
     * Only enriches members that lack photo or have id=0.
     */
    suspend fun enrichCast(
        apiKey: String,
        stremioId: String,
        mediaType: String, // "movie" or "series"
        cast: List<CastMember>
    ): List<CastMember> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext cast
        // Only enrich if needed
        if (cast.all { it.photo != null && it.id > 0 }) return@withContext cast
        
        try {
            // Resolve Stremio/IMDb ID to TMDB ID via find API
            val tmdbType = if (mediaType == "series") "tv" else "movie"
            val findUrl = "$BASE/find/$stremioId?api_key=$apiKey&external_source=imdb_id"
            val findResponse = java.net.URL(findUrl).readText()
            val findJson = json.parseToJsonElement(findResponse).jsonObject
            val results = findJson["${tmdbType}_results"]?.jsonArray
            val tmdbId = results?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
                ?: return@withContext cast
            
            val url = "$BASE/$tmdbType/$tmdbId/credits?api_key=$apiKey"
            val response = java.net.URL(url).readText()
            val root = json.parseToJsonElement(response).jsonObject
            val tmdbCast = root["cast"]?.jsonArray ?: return@withContext cast
            
            val photoMap = mutableMapOf<String, Pair<Int, String?>>()
            val tmdbFullCast = mutableListOf<CastMember>()
            for (el in tmdbCast) {
                val obj = el.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: continue
                val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val profile = obj["profile_path"]?.jsonPrimitive?.content
                val photoUrl = if (profile != null) "$IMG_BASE$profile" else null
                photoMap[name.lowercase()] = Pair(id, photoUrl)
                // Build full cast list (up to 15) from TMDB
                if (tmdbFullCast.size < 15) {
                    tmdbFullCast.add(CastMember(id = id, name = name, photo = photoUrl))
                }
            }
            
            // If addon provided few actors (< 5), use TMDB's full cast list instead
            if (cast.size < 5 && tmdbFullCast.isNotEmpty()) {
                return@withContext tmdbFullCast
            }
            
            cast.map { member ->
                val match = photoMap[member.name.lowercase()]
                if (match != null) {
                    member.copy(
                        id = if (member.id == 0) match.first else member.id,
                        photo = member.photo ?: match.second
                    )
                } else member
            }
        } catch (e: Exception) {
            cast // Return original on failure
        }
    }

    /**
     * Fetch actor details + filmography directly from TMDB.
     * Returns PersonDetail with movies/series lists.
     */
    suspend fun getPerson(
        apiKey: String,
        personId: Int
    ): PersonDetail? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || personId <= 0) return@withContext null
        try {
            // Person details
            val personUrl = "$BASE/person/$personId?api_key=$apiKey"
            val personResponse = java.net.URL(personUrl).readText()
            val personJson = json.parseToJsonElement(personResponse).jsonObject
            val name = personJson["name"]?.jsonPrimitive?.content ?: return@withContext null
            val profile = personJson["profile_path"]?.jsonPrimitive?.content
            val photo = if (profile != null) "https://image.tmdb.org/t/p/w185$profile" else null

            // Combined credits (movies + TV)
            val creditsUrl = "$BASE/person/$personId/combined_credits?api_key=$apiKey"
            val creditsResponse = java.net.URL(creditsUrl).readText()
            val creditsJson = json.parseToJsonElement(creditsResponse).jsonObject
            val castArray = creditsJson["cast"]?.jsonArray ?: return@withContext PersonDetail(
                id = personId, name = name, photo = photo,
                movies = emptyList(), series = emptyList()
            )

            val movies = mutableListOf<MetaPreview>()
            val series = mutableListOf<MetaPreview>()
            for (el in castArray) {
                val obj = el.jsonObject
                val mediaType = obj["media_type"]?.jsonPrimitive?.content ?: continue
                val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                val title = obj["title"]?.jsonPrimitive?.content
                    ?: obj["name"]?.jsonPrimitive?.content ?: continue
                val poster = obj["poster_path"]?.jsonPrimitive?.content
                val posterUrl = if (poster != null) "https://image.tmdb.org/t/p/w342$poster" else null
                // TMDB IDs need to be mapped back; use a tmdb: prefix so the app can resolve
                val metaId = "tmdb:$mediaType:$id"
                val item = MetaPreview(id = metaId, type = mediaType, name = title, poster = posterUrl)
                if (mediaType == "movie") movies.add(item) else series.add(item)
            }

            PersonDetail(
                id = personId,
                name = name,
                photo = photo,
                movies = movies.take(20),
                series = series.take(20)
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun search(apiKey: String, query: String): List<MetaPreview> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || query.trim().length < 2) return@withContext emptyList()
        try {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$BASE/search/multi?api_key=$apiKey&query=$encoded&page=1&include_adult=false"
            val response = java.net.URL(url).readText()
            val root = json.parseToJsonElement(response).jsonObject
            val results = root["results"]?.jsonArray ?: return@withContext emptyList()
            val out = mutableListOf<MetaPreview>()
            for (el in results.take(20)) {
                val obj = el.jsonObject
                val mediaType = obj["media_type"]?.jsonPrimitive?.content ?: continue
                if (mediaType != "movie" && mediaType != "tv") continue
                val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                val title = obj["title"]?.jsonPrimitive?.content
                    ?: obj["name"]?.jsonPrimitive?.content ?: continue
                val poster = obj["poster_path"]?.jsonPrimitive?.content
                val posterUrl = if (poster != null) "https://image.tmdb.org/t/p/w342$poster" else null
                val type = if (mediaType == "movie") "movie" else "series"
                out.add(MetaPreview(id = "tmdb:$mediaType:$id", type = type, name = title, poster = posterUrl))
            }
            out
        } catch (e: Exception) { emptyList() }
    }
}
