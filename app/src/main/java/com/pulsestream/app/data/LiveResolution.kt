package com.pulsestream.app.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Each addon request has a 20-second network budget, followed by a cached
 * debrid resolution stage with a 45-second per-candidate budget. Allow that
 * common pipeline more time than the old 25-second UI deadline while keeping
 * the overall UI wait bounded; larger queues can still exhaust this deadline.
 */
const val LIVE_SOURCE_RESOLUTION_TIMEOUT_MS = 65_000L

/**
 * Identifies catalogs that are explicitly described as live TV/channels.
 * Stremio manifests commonly use the `tv` resource type, while some addons
 * expose a custom type and identify the catalog by its id/name instead.
 * Ordinary movie/series catalogs are deliberately not treated as live unless
 * their type, id, or display name carries an explicit live-channel marker.
 */
fun isLiveCatalog(type: String, id: String, name: String): Boolean {
    val liveMarkers = setOf("tv", "live", "iptv", "channel", "channels")
    fun hasMarker(value: String): Boolean =
        value.replace(Regex("([a-z0-9])([A-Z])"), "\$1 \$2")
            .lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .any { it in liveMarkers }

    return hasMarker(type) || hasMarker(id) || hasMarker(name)
}

/** A meta item can override the catalog type; use it for /stream requests. */
fun liveResourceType(metaType: String, catalogType: String): String =
    metaType.takeIf { it.isNotBlank() } ?: catalogType

/** Only direct HTTP(S) URLs are immediately playable as Live streams. */
fun isPlayableLiveStreamUrl(url: String): Boolean = url.toHttpUrlOrNull() != null

/** Keep provider/network exception details (which may contain URLs) out of the UI. */
fun liveResolutionFailureMessage(channelName: String): String =
    "Couldn't load \"$channelName\". The source may be unavailable; please retry."
