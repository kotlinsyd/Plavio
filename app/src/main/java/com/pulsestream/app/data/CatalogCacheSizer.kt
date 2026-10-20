package com.pulsestream.app.data

/** Produces cache JSON that respects the hard encoded-byte budget, even for a single huge or multibyte record. */
internal object CatalogCacheSizer {
    fun encode(items: List<MetaPreview>, maxBytes: Int): String {
        require(maxBytes > 0)
        fun bytes(value: String) = value.toByteArray(Charsets.UTF_8).size
        var kept = items
        var json = CatalogCodec.encodeMetas(kept)
        while (bytes(json) > maxBytes && kept.isNotEmpty()) {
            if (kept.size > 1) {
                kept = kept.dropLast(maxOf(1, kept.size / 2))
            } else {
                val item = kept.single()
                val shortened = item.copy(
                    id = item.id.take(512), type = item.type.take(64), name = item.name.take(256),
                    description = item.description?.take(2_000), poster = item.poster?.take(2_000),
                    background = item.background?.take(2_000), logo = item.logo?.take(2_000),
                    releaseInfo = item.releaseInfo?.take(256), imdbRating = item.imdbRating?.take(64),
                    genres = item.genres.take(50).map { it.take(128) }
                )
                val candidate = CatalogCodec.encodeMetas(listOf(shortened))
                if (bytes(candidate) <= maxBytes) return candidate
                kept = emptyList()
            }
            json = CatalogCodec.encodeMetas(kept)
        }
        return if (bytes(json) <= maxBytes) json else "[]"
    }
}
