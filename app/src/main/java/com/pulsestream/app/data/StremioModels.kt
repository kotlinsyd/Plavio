package com.pulsestream.app.data

/** Stremio addon protocol data models (https://github.com/Stremio/stremio-addon-sdk). */

data class CatalogRef(
    val type: String,
    val id: String,
    val name: String
)

data class AddonManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val types: List<String>,
    val catalogs: List<CatalogRef>,
    val resources: List<String>,
    /** True when the manifest's behaviorHints mark it as configurable (has a /configure page). */
    val configurable: Boolean = false,
    /** Manifest logo URL — shown as the addon icon in the Addons tab. */
    val logo: String? = null
) {
    val hasCatalog: Boolean get() = resources.contains("catalog")
    val hasMeta: Boolean get() = resources.contains("meta")
    val hasStream: Boolean get() = resources.contains("stream")
    val hasSubtitles: Boolean get() = resources.contains("subtitles")
    val isFullAddon: Boolean get() = hasCatalog && hasMeta && hasStream
}

data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList()
)

data class Video(
    val id: String,
    val title: String,
    val season: Int = 0,
    val episode: Int = 0,
    val overview: String? = null,
    val thumbnail: String? = null,
    val released: String? = null
)

data class MetaDetail(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val videos: List<Video> = emptyList()
)

data class StreamItem(
    val url: String,
    val name: String? = null,
    val title: String? = null,
    /** Name of the addon this stream came from (set when merging multi-addon results). */
    val sourceName: String? = null,
    /** Id of the addon this stream came from — used for star priority. */
    val sourceId: String? = null
)

data class Subtitle(
    val url: String,
    val lang: String
)

data class StreamResult(
    val streams: List<StreamItem>,
    val subtitles: List<Subtitle>
)

/** A catalog row shown on Home: one catalog of one addon with its items. */
data class CatalogRow(
    val addonId: String,
    val addonName: String,
    val baseUrl: String,
    val catalogName: String,
    val type: String,
    val catalogId: String,
    val items: List<MetaPreview>
)

/** Cached meta used for search, tagged with its source addon. */
data class CachedMeta(
    val addonId: String,
    val addonName: String,
    val baseUrl: String,
    val meta: MetaPreview
)

/** Everything the player needs, passed in-memory via the repository. */
data class PlaybackRequest(
    val streamUrl: String,
    val title: String,
    val poster: String?,
    val videoId: String,
    val subtitles: List<Subtitle> = emptyList(),
    /** IMDb id of the title (from Cinemeta metadata) — powers skip-segments + subtitle search. */
    val imdbId: String? = null,
    val season: Int = 0,
    val episode: Int = 0,
    val isMovie: Boolean = false,
    /** Content type for stream re-resolution ("movie"/"series"/"tv"). */
    val type: String = if (isMovie) "movie" else "series",
    /** Display label for the chosen stream, e.g. "1080p". */
    val streamLabel: String? = null,
    /** Addon the chosen stream came from. */
    val sourceName: String? = null,
    /** All fetched streams (in-player Sources switcher); may be empty for resumed items. */
    val streams: List<StreamItem> = emptyList(),
    /** Full episode list for series (in-player Episodes picker). */
    val episodes: List<Video> = emptyList(),
    /** Show/movie id (episode-switching context). */
    val metaId: String = "",
    /** Series name for the player header (null for movies). */
    val seriesName: String? = null,
    /** Episode title for the player header (series only). */
    val episodeTitle: String? = null
)

/** Pulls a quality tag like "1080p"/"4K" out of a stream's name/title, if present. */
fun streamQualityLabel(stream: StreamItem): String? {
    val text = listOfNotNull(stream.name, stream.title).joinToString(" ")
    return Regex("""\b(2160p|1440p|1080p|720p|480p|360p|4K)\b""", RegexOption.IGNORE_CASE)
        .find(text)?.value?.uppercase()
}
