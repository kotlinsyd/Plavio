package com.pulsestream.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class SegmentType { INTRO, RECAP, OUTRO, POST_CREDITS }

data class SkipSegment(
    val type: SegmentType,
    val startMs: Long,
    val endMs: Long
)

fun SegmentType.buttonLabel(): String = when (this) {
    SegmentType.INTRO -> "Skip intro"
    SegmentType.RECAP -> "Skip recap"
    SegmentType.OUTRO -> "Skip outro"
    SegmentType.POST_CREDITS -> "Skip credits"
}

/**
 * Client for the community IntroDB database (https://introdb.app) — no auth
 * needed. Returns intro/recap/outro/post-credits windows for an IMDb id,
 * or an empty list when nothing is known. All failures degrade to "no data".
 */
class IntroDbApi {

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetchSegments(
        imdbId: String,
        season: Int,
        episode: Int,
        isMovie: Boolean
    ): List<SkipSegment> = withContext(Dispatchers.IO) {
        val url = if (isMovie) {
            "https://api.introdb.app/segments?imdb_id=$imdbId&is_movie=true"
        } else {
            "https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=$episode"
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PulseStream/0.4.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            parse(response.body?.string() ?: return@withContext emptyList())
        }
    }

    private fun parse(text: String): List<SkipSegment> =
        runCatching {
            val obj = json.parseToJsonElement(text).jsonObject
            listOf(
                SegmentType.INTRO to obj["intro"],
                SegmentType.RECAP to obj["recap"],
                SegmentType.OUTRO to obj["outro"],
                SegmentType.POST_CREDITS to obj["post_credits"]
            ).mapNotNull { (type, el) ->
                val seg = (el as? kotlinx.serialization.json.JsonObject) ?: return@mapNotNull null
                val start = seg["start_sec"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val end = seg["end_sec"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                if (end <= start) return@mapNotNull null
                SkipSegment(type, (start * 1000).toLong(), (end * 1000).toLong())
            }
        }.getOrDefault(emptyList())
}
