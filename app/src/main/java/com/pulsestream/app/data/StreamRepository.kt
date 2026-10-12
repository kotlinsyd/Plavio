package com.pulsestream.app.data

import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.CatalogCache
import com.pulsestream.app.data.db.InstalledAddon
import com.pulsestream.app.data.db.PlaybackState
import com.pulsestream.app.data.db.SubtitleCache
import com.pulsestream.app.data.db.WatchlistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single source of truth: Room for persistence, StremioApi for network,
 * in-memory caches for catalogs (search) and the pending playback request (navigation).
 */
class StreamRepository(
    private val db: AppDatabase,
    val api: StremioApi,
    val settings: SettingsStore
) {
    val addons: Flow<List<InstalledAddon>> = db.addonDao().observeAll()
    val continueWatching: Flow<List<PlaybackState>> = db.playbackDao().observeRecent()
    val watchlist: Flow<List<WatchlistItem>> = db.watchlistDao().observeAll()

    /** In-memory cache of every catalog item loaded on Home — used for search. */
    @Volatile
    var catalogCache: List<CachedMeta> = emptyList()
        private set

    /** In-memory handoff for the player screen (avoids stuffing long URLs into nav args). */
    @Volatile
    var pendingPlayback: PlaybackRequest? = null

    /**
     * Deep-link handoff: a plavio-addons:// VIEW intent carries a share-bundle
     * link. MainActivity stashes the raw link here; AddonsScreen opens its
     * import dialog pre-filled with it, then clears it.
     */
    private val _pendingAddonBundle = MutableStateFlow<String?>(null)
    val pendingAddonBundle: StateFlow<String?> = _pendingAddonBundle.asStateFlow()

    fun setPendingAddonBundle(link: String) { _pendingAddonBundle.value = link }
    fun clearPendingAddonBundle() { _pendingAddonBundle.value = null }

    /** In-memory handoff for the catalog "view all" screen. */
    @Volatile
    var pendingCatalog: CatalogRow? = null

    suspend fun addAddonFromUrl(input: String): Result<InstalledAddon> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = StremioApi.normalizeBaseUrl(input)
            val manifest = api.fetchManifest(baseUrl)
            // Real Stremio behavior: an addon is useful if it provides ANY of
            // catalog / meta / stream / subtitles — they no longer have to come bundled together.
            val hasAny = manifest.hasCatalog || manifest.hasMeta || manifest.hasStream || manifest.hasSubtitles
            if (!hasAny) {
                throw IllegalStateException(
                    "This addon doesn't provide catalogs, metadata, streams, or subtitles — " +
                        "there's nothing Pulse can use."
                )
            }
            val dao = db.addonDao()
            val sortOrder = (dao.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
            val addon = InstalledAddon(
                id = manifest.id,
                name = manifest.name,
                version = manifest.version,
                description = manifest.description,
                baseUrl = baseUrl,
                types = manifest.types.joinToString(","),
                resources = manifest.resources.joinToString(","),
                configurable = manifest.configurable,
                logoUrl = manifest.logo ?: "",
                sortOrder = sortOrder,
                addedAt = System.currentTimeMillis()
            )
            dao.upsert(addon)
            addon
        }
    }

    suspend fun removeAddon(id: String) = withContext(Dispatchers.IO) {
        val baseUrl = db.addonDao().getAll().firstOrNull { it.id == id }?.baseUrl
        db.addonDao().deleteById(id)
        // The DB row is gone but in-memory caches would keep serving the
        // addon's catalogs/manifest — invalidate both.
        if (baseUrl != null) manifestCache.remove(baseUrl)
        catalogCache = catalogCache.filter { it.addonId != id }
    }

    /** User on/off switch for an addon. Disabled addons are excluded everywhere. */
    suspend fun setAddonEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.addonDao().getAll().firstOrNull { it.id == id }?.let { addon ->
            db.addonDao().upsert(addon.copy(enabled = enabled))
            // Same staleness trap as removeAddon: drop the in-memory caches
            // so a disabled addon stops appearing in search immediately.
            manifestCache.remove(addon.baseUrl)
            if (!enabled) catalogCache = catalogCache.filter { it.addonId != id }
        }
    }

    /** Star an addon: its catalogs, streams and subtitles get priority. */
    suspend fun setAddonStarred(id: String, starred: Boolean) = withContext(Dispatchers.IO) {
        db.addonDao().getAll().firstOrNull { it.id == id }?.let { addon ->
            db.addonDao().upsert(addon.copy(starred = starred))
        }
    }

    /** Swap the manual order positions of two addons (rearrange up/down). */
    suspend fun swapAddonOrder(id1: String, id2: String) = withContext(Dispatchers.IO) {
        val dao = db.addonDao()
        val all = dao.getAll()
        val a = all.firstOrNull { it.id == id1 }
        val b = all.firstOrNull { it.id == id2 }
        if (a != null && b != null) {
            dao.upsert(a.copy(sortOrder = b.sortOrder))
            dao.upsert(b.copy(sortOrder = a.sortOrder))
        }
    }

    /**
     * One-time normalization: addons installed before manual ordering
     * existed all share sortOrder 0, which makes swaps no-ops. Assign
     * distinct sequential values preserving the current relative order.
     * Later runs are no-ops.
     */
    suspend fun normalizeAddonSortOrder() = withContext(Dispatchers.IO) {
        val dao = db.addonDao()
        dao.getAll().forEachIndexed { idx, addon ->
            if (addon.sortOrder != idx) dao.upsert(addon.copy(sortOrder = idx))
        }
    }

    /** Addons the user left switched on. */
    private suspend fun enabledAddons(): List<InstalledAddon> =
        db.addonDao().getAll().filter { it.enabled }

    /** Enabled addons that provide catalogs — the Home Catalogs settings list. */
    suspend fun catalogAddons(): List<InstalledAddon> = withContext(Dispatchers.IO) {
        enabledAddons().filter { it.hasCatalog }
    }

    /** Whether an addon's catalogs appear on the Home screen. */
    suspend fun setAddonShowOnHome(id: String, show: Boolean) = withContext(Dispatchers.IO) {
        db.addonDao().setShowOnHome(id, show)
        _catalogVisibility.value += 1
    }

    /** Bumped whenever Home catalog visibility changes — Home reloads on it. */
    private val _catalogVisibility = MutableStateFlow(0)
    val catalogVisibility: StateFlow<Int> = _catalogVisibility.asStateFlow()

    /**
     * Loads catalogs from every installed addon that declares the catalog
     * resource. Manifests are re-fetched so renamed/dead addons refresh
     * cleanly and capability changes are picked up; failures are skipped.
     * Fresh results are written to the catalog cache (Room).
     */
    // In-flight catalog refresh sharing: concurrent callers (splash warm-up,
    // Home load, pull-to-refresh) join the same network job instead of
    // stacking duplicate requests.
    private val catalogRefreshLock = Any()
    private var catalogRefreshJob: Deferred<List<CatalogRow>>? = null
    private val catalogRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Refreshes catalog rows from the network. If a refresh is already in
     * flight, joins it instead of starting duplicate work.
     */
    suspend fun refreshCatalogRows(): List<CatalogRow> {
        val job = synchronized(catalogRefreshLock) {
            val active = catalogRefreshJob?.takeIf { it.isActive }
            active ?: catalogRefreshScope.async { doRefreshCatalogRows() }
                .also { catalogRefreshJob = it }
        }
        return job.await()
    }

    /**
     * True when Home's catalog cache was refreshed within [maxAgeMs].
     * Freshness is tracked per-section via [SettingsStore.homeCatalogsRefreshedAt]:
     * the Live tab writes its own rows into the same catalog_cache table, so
     * judging freshness from the table's max fetchedAt let a Live visit
     * suppress Home's background refresh for 15 minutes.
     */
    suspend fun isCatalogCacheFresh(maxAgeMs: Long = 15 * 60 * 1000): Boolean =
        withContext(Dispatchers.IO) {
            val refreshedAt = settings.homeCatalogsRefreshedAt
            if (refreshedAt <= 0L) return@withContext false
            if (System.currentTimeMillis() - refreshedAt >= maxAgeMs) return@withContext false
            try {
                // The timestamp says Home refreshed — make sure there are
                // actually Home rows (Live/M3U rows don't count).
                db.catalogCacheDao().getAll().any { it.addonId != "m3u" }
            } catch (_: Exception) {
                false
            }
        }

    private suspend fun doRefreshCatalogRows(): List<CatalogRow> = withContext(Dispatchers.IO) {
        val installed = enabledAddons()
        // Only addons the user wants on Home contribute catalog rows.
        val rows = installed.filter { it.showOnHome }.map { addon ->
            async {
                val manifest = cachedManifest(addon.baseUrl)
                if (manifest != null) {
                    db.addonDao().upsert(
                        addon.copy(
                            resources = manifest.resources.joinToString(","),
                            types = manifest.types.joinToString(","),
                            configurable = manifest.configurable
                        )
                    )
                }
                val resources = manifest?.resources?.toSet() ?: addon.resourceSet
                if ("catalog" !in resources) return@async emptyList<CatalogRow>()
                val catalogs = manifest?.catalogs ?: emptyList()
                val addonRows = coroutineScope {
                    catalogs.map { catalog ->
                        async {
                            val items = runCatching {
                                api.fetchCatalog(addon.baseUrl, catalog.type, catalog.id)
                            }.getOrDefault(emptyList())
                            CatalogRow(
                                addonId = addon.id,
                                addonName = addon.name,
                                baseUrl = addon.baseUrl,
                                catalogName = catalog.name,
                                type = catalog.type,
                                catalogId = catalog.id,
                                items = items
                            )
                        }
                    }.awaitAll().filter { it.items.isNotEmpty() }
                }
                addonRows
            }
        }.awaitAll().flatten()
            // Defensive: two catalogs sharing (addon, type, id) would produce
            // duplicate LazyColumn keys and crash the list — keep the first.
            .distinctBy { "${it.addonId}|${it.type}|${it.catalogId}" }

        // Persist to the cache and drop rows for addons that are gone.
        val now = System.currentTimeMillis()
        db.catalogCacheDao().upsertAll(
            rows.map { row ->
                CatalogCache(
                    key = "${row.addonId}|${row.type}|${row.catalogId}",
                    addonId = row.addonId,
                    addonName = row.addonName,
                    baseUrl = row.baseUrl,
                    type = row.type,
                    catalogId = row.catalogId,
                    catalogName = row.catalogName,
                    itemsJson = cappedItemsJson(row.items),
                    fetchedAt = now
                )
            }
        )
        val installedIds = installed.map { it.id }
        // "m3u" is a virtual addonId for cached M3U playlist rows — never
        // prune those here; M3U rows manage their own TTL and cleanup.
        if (installedIds.isEmpty()) db.catalogCacheDao().pruneExcept(listOf("m3u"))
        else db.catalogCacheDao().pruneExcept(installedIds + "m3u")
        // Per-section freshness marker for isCatalogCacheFresh (see #31).
        settings.homeCatalogsRefreshedAt = now

        updateSearchCache(rows)
        // Preferred metadata source's catalogs first (PlavioMeta by default).
        return@withContext sortRowsByPreference(rows, installed.associateBy { it.id })
    }

    /**
     * Removes catalog rows too big for SQLite's CursorWindow. Call at
     * startup: heals installs poisoned by an oversized row without
     * wiping watchlist/addons/settings.
     */
    suspend fun pruneOversizedCache() = withContext(Dispatchers.IO) {
        try {
            db.catalogCacheDao().pruneOversized(MAX_CACHED_ROW_BYTES)
        } catch (_: Exception) {
        }
    }

    /**
     * Reads the last cached catalog rows from Room. Returns instantly —
     * Home shows these immediately while [refreshCatalogRows] runs quietly
     * in the background. Never throws: a poisoned cache must not crash Home.
     */
    suspend fun loadCatalogRowsCached(): List<CatalogRow> = withContext(Dispatchers.IO) {
        try {
            val visible = enabledAddons().filter { it.showOnHome }
            val visibleIds = visible.map { it.id }.toSet()
            val rows = db.catalogCacheDao().getAll()
                .filter { it.addonId in visibleIds }
                .map { cached ->
                    CatalogRow(
                        addonId = cached.addonId,
                        addonName = cached.addonName,
                        baseUrl = cached.baseUrl,
                        catalogName = cached.catalogName,
                        type = cached.type,
                        catalogId = cached.catalogId,
                        items = CatalogCodec.decodeMetas(cached.itemsJson)
                    )
                }.filter { it.items.isNotEmpty() }
                .distinctBy { "${it.addonId}|${it.type}|${it.catalogId}" }
            updateSearchCache(rows)
            sortRowsByPreference(rows, visible.associateBy { it.id })
        } catch (_: Exception) {
            // Oversized/corrupt row slipped through — drop it and carry on.
            pruneOversizedCache()
            emptyList()
        }
    }

    /** In-memory Live-tab TV rows: (fetchedAt, rows) — repeat visits within the TTL skip the network. */
    @Volatile
    private var tvRowsMemoryCache: Pair<Long, List<CatalogRow>>? = null

    /**
     * Live TV rows for the Live tab: catalogs of type "tv" from enabled
     * addons. Served from the in-memory cache when fresh (15 min TTL) so
     * every ON_RESUME doesn't re-download every catalog.
     */
    suspend fun loadTvRows(): List<CatalogRow> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        tvRowsMemoryCache?.let { (ts, rows) ->
            if (now - ts < TV_ROWS_TTL_MS) return@withContext rows
        }
        val installed = enabledAddons().filter { it.hasCatalog }
        val rows = coroutineScope {
            // OOM hotfix (v0.8.28-tv): cap concurrent TV catalog fetches.
            // Unbounded nested async + HTTP/2 segment buffers exhausted the
            // 48MB heap on low-RAM TVs (OutOfMemoryError in Http2Connection).
            val fetchGate = Semaphore(2)
            installed.map { addon ->
                async {
                    val manifest = cachedManifest(addon.baseUrl)
                    val tvCatalogs = (manifest?.catalogs ?: emptyList()).filter { it.type == "tv" }
                    tvCatalogs.map { catalog ->
                        async {
                            fetchGate.acquire()
                            try {
                                val items = runCatching {
                                    api.fetchCatalog(addon.baseUrl, catalog.type, catalog.id)
                                }.getOrDefault(emptyList())
                                CatalogRow(
                                    addonId = addon.id,
                                    addonName = addon.name,
                                    baseUrl = addon.baseUrl,
                                    catalogName = catalog.name,
                                    type = catalog.type,
                                    catalogId = catalog.id,
                                    items = items
                                )
                            } finally {
                                fetchGate.release()
                            }
                        }
                    }.awaitAll().filter { it.items.isNotEmpty() }
                }
            }.awaitAll().flatten()
                .distinctBy { "${it.addonId}|${it.type}|${it.catalogId}" }
        }
        // Persist so the next visit is instant (see loadTvRowsCached).
        if (rows.isNotEmpty()) {
            val fetchedAt = System.currentTimeMillis()
            runCatching {
                db.catalogCacheDao().upsertAll(
                    rows.map { row ->
                        CatalogCache(
                            key = "${row.addonId}|${row.type}|${row.catalogId}",
                            addonId = row.addonId,
                            addonName = row.addonName,
                            baseUrl = row.baseUrl,
                            type = row.type,
                            catalogId = row.catalogId,
                            catalogName = row.catalogName,
                            itemsJson = cappedItemsJson(row.items),
                            fetchedAt = fetchedAt
                        )
                    }
                )
            }
            // Only cache successful non-empty loads — a failed visit must
            // retry on the next resume instead of serving emptiness for 15 min.
            tvRowsMemoryCache = fetchedAt to rows
        }
        rows
    }

    /**
     * Cached TV rows for the Live tab — returns instantly so the tab never
     * stares at a spinner on repeat visits. Refreshed by [loadTvRows].
     */
    suspend fun loadTvRowsCached(): List<CatalogRow> = withContext(Dispatchers.IO) {
        try {
            val visibleIds = enabledAddons().map { it.id }.toSet()
            db.catalogCacheDao().getAll()
                .filter { it.addonId in visibleIds && it.type == "tv" }
                .map { cached ->
                    CatalogRow(
                        addonId = cached.addonId,
                        addonName = cached.addonName,
                        baseUrl = cached.baseUrl,
                        catalogName = cached.catalogName,
                        type = cached.type,
                        catalogId = cached.catalogId,
                        items = CatalogCodec.decodeMetas(cached.itemsJson)
                    )
                }.filter { it.items.isNotEmpty() }
                .distinctBy { "${it.addonId}|${it.type}|${it.catalogId}" }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun updateSearchCache(rows: List<CatalogRow>) {
        catalogCache = rows.flatMap { row ->
            row.items.map { meta ->
                CachedMeta(
                    addonId = row.addonId,
                    addonName = row.addonName,
                    baseUrl = row.baseUrl,
                    meta = meta
                )
            }
        }
    }

    /** [type] filters by content type ("movie", "series", "tv"); null searches everything. */
    fun search(query: String, type: String? = null): List<CachedMeta> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return catalogCache
            .filter { (type == null || it.meta.type == type) && it.meta.name.lowercase().contains(q) }
            .distinctBy { it.meta.id + it.addonId }
            .take(100)
    }

    /**
     * Live search against installed addons (the catalog `search=` extra).
     * This is what finds titles that were never browsed into the local cache.
     *
     * Protocol-correct: `search=` is only fired at catalogs that declare the
     * "search" extra — addons without it answer 404, so querying them is
     * pure waste. A hard overall timeout keeps "Searching…" from hanging.
     */
    suspend fun searchRemote(query: String, type: String? = null): List<CachedMeta> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()
            withTimeoutOrNull(REMOTE_SEARCH_TIMEOUT_MS) {
                val types = if (type != null) listOf(type) else listOf("movie", "series")
                // Query every addon concurrently — sequential requests were the
                // main reason search felt slow on mobile networks.
                val out = linkedMapOf<String, CachedMeta>()
                coroutineScope {
                    enabledAddons()
                        .filter { it.hasCatalog }
                        .map { addon ->
                            async {
                                val manifest = cachedManifest(addon.baseUrl)
                                    ?: return@async emptyList<CachedMeta>()
                                val found = mutableListOf<CachedMeta>()
                                for (t in types) {
                                    val targets = manifest.catalogs
                                        .filter { it.type == t && it.supportsSearch }
                                        .take(3)
                                    for (catalog in targets) {
                                        val metas =
                                            runCatching { api.searchCatalog(addon.baseUrl, t, catalog.id, q) }
                                                .getOrNull() ?: continue
                                        metas.forEach { m ->
                                            found.add(
                                                CachedMeta(
                                                    addon.id,
                                                    addon.name,
                                                    addon.baseUrl,
                                                    m.copy(type = t)
                                                )
                                            )
                                        }
                                    }
                                }
                                found
                            }
                        }
                        .awaitAll()
                        .flatten()
                        .forEach { cm -> out.putIfAbsent(cm.addonId + cm.meta.id, cm) }
                }
                out.values.toList()
            } ?: emptyList()
        }

    /**
     * Combined search: instant local-cache matches first, then live addon
     * results for everything the cache doesn't have.
     */
    suspend fun searchAll(query: String, type: String? = null): List<CachedMeta> =
        withContext(Dispatchers.IO) {
            val local = search(query, type)
            val seen = local.map { it.addonId + it.meta.id }.toMutableSet()
            local + searchRemote(query, type).filter { seen.add(it.addonId + it.meta.id) }
        }

    /** In-memory manifest cache so Home/Live/search don't refetch manifests per load. */
    private val manifestCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, AddonManifest>>()

    private suspend fun cachedManifest(baseUrl: String): AddonManifest? {
        val now = System.currentTimeMillis()
        manifestCache[baseUrl]?.let { (ts, manifest) ->
            if (now - ts < MANIFEST_TTL_MS) return manifest
        }
        val manifest = runCatching { api.fetchManifest(baseUrl) }.getOrNull() ?: return null
        manifestCache[baseUrl] = now to manifest
        return manifest
    }

    /**
     * Manual manifest refresh: bypasses the TTL cache and re-fetches from
     * the network. Use after an addon gains capabilities (e.g. newly
     * search-capable catalogs) instead of waiting out the TTL.
     */
    suspend fun refreshManifest(baseUrl: String): AddonManifest? {
        manifestCache.remove(baseUrl)
        return cachedManifest(baseUrl)
    }

    /** Drops the whole in-memory manifest cache (e.g. on pull-to-refresh). */
    fun clearManifestCache() {
        manifestCache.clear()
    }

    suspend fun getMeta(baseUrl: String, type: String, id: String): MetaDetail? =
        withContext(Dispatchers.IO) { api.fetchMeta(baseUrl, type, id) }

    private fun addonTypes(addon: InstalledAddon): Set<String> =
        addon.types.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** Addons that can serve metadata for [type] (hint addon's base URL first). */
    suspend fun metaAddonsFor(type: String, hintBaseUrl: String? = null): List<InstalledAddon> =
        withContext(Dispatchers.IO) {
            enabledAddons()
                .filter { addon ->
                    addon.hasMeta && run {
                        val types = addonTypes(addon)
                        types.isEmpty() || type in types
                    }
                }
                .sortedWith(
                    compareByDescending<InstalledAddon> { it.baseUrl == hintBaseUrl }
                        .thenBy { metaRank(it) }
                )
        }

    private fun isPlavioMeta(addon: InstalledAddon): Boolean =
        addon.id.contains("plaviometa", ignoreCase = true) ||
                addon.baseUrl.contains("plaviometa", ignoreCase = true)

    private fun isCinemeta(addon: InstalledAddon): Boolean =
        addon.id.contains("cinemeta", ignoreCase = true) ||
                addon.baseUrl.contains("cinemeta", ignoreCase = true)

    /**
     * Rank for metadata/catalog priority. PlavioMeta is always the default
     * source: its posters/details come first, Cinemeta is the fallback,
     * everything else keeps its relative order after.
     */
    private fun metaRank(addon: InstalledAddon): Int {
        val plavio = isPlavioMeta(addon)
        val cine = isCinemeta(addon)
        return when {
            plavio -> 0
            cine -> 1
            else -> 2
        }
    }

    /** Sorts catalog rows so the preferred metadata source's rows come first. */
    private fun sortRowsByPreference(
        rows: List<CatalogRow>,
        byAddonId: Map<String, InstalledAddon>
    ): List<CatalogRow> =
        rows.sortedWith(
            compareBy<CatalogRow> { byAddonId[it.addonId]?.let(::metaRank) ?: 2 }
                .thenBy { byAddonId[it.addonId]?.sortOrder ?: Int.MAX_VALUE }
        )

    /** Addons that can serve streams. */
    suspend fun streamAddons(): List<InstalledAddon> = withContext(Dispatchers.IO) {
        enabledAddons().filter { it.hasStream }
    }

    /**
     * Real Stremio behavior: metadata may live in a different addon than the
     * catalog the title was found in. Try every meta-capable addon in turn
     * until one returns details.
     */
    suspend fun getMetaFromAny(hintBaseUrl: String?, type: String, id: String): MetaDetail? =
        withContext(Dispatchers.IO) {
            for (addon in metaAddonsFor(type, hintBaseUrl)) {
                // Per-addon budget instead of unbounded sequential waits —
                // one hanging addon must not stall the whole lookup.
                val meta = withTimeoutOrNull(META_FETCH_TIMEOUT_MS) {
                    runCatching { api.fetchMeta(addon.baseUrl, type, id) }.getOrNull()
                } ?: continue
                return@withContext meta
            }
            null
        }

    /**
     * Streams may live in different addons than catalogs/metadata.
     * Queries every stream-capable addon concurrently and emits the merged
     * list PROGRESSIVELY — each emission holds everything found so far,
     * de-duplicated by URL and tagged with its source addon name — so the UI
     * can show (and autoplay) the first sources without waiting for the
     * slowest addon. Each addon fails fast after [STREAM_FETCH_TIMEOUT_MS].
     * The final emission is the complete merged result.
     */
    fun streamResultsFlow(type: String, videoId: String): Flow<StreamResult> = channelFlow {
        val addons = streamAddons()
        if (addons.isEmpty()) {
            send(StreamResult(emptyList(), emptyList()))
            return@channelFlow
        }
        // Starred addons go first — in every emission, so their streams show
        // and autoplay before anyone else's.
        val starredIds = addons.filter { it.starred }.map { it.id }.toSet()
        fun prioritize(streams: List<StreamItem>): List<StreamItem> =
            streams.sortedWith(
                compareByDescending<StreamItem> { it.sourceId in starredIds }
                    .thenBy { it.sourceId }
            )
        val mutex = Mutex()
        val seenStreams = LinkedHashSet<String>()
        val seenSubs = LinkedHashSet<String>()
        val accStreams = mutableListOf<StreamItem>()
        val accSubs = mutableListOf<Subtitle>()
        coroutineScope {
            addons.forEach { addon ->
                launch {
                    val res = withTimeoutOrNull(STREAM_FETCH_TIMEOUT_MS) {
                        runCatching { api.fetchStreams(addon.baseUrl, type, videoId) }.getOrNull()
                    } ?: return@launch
                    var changed = false
                    mutex.withLock {
                        res.streams.forEach { s ->
                            if (seenStreams.add(s.url)) {
                                accStreams.add(s.copy(sourceName = addon.name, sourceId = addon.id))
                                changed = true
                            }
                        }
                        res.subtitles.forEach { s ->
                            if (seenSubs.add(s.url)) {
                                accSubs.add(s)
                                changed = true
                            }
                        }
                    }
                    if (changed) {
                        mutex.withLock { send(StreamResult(prioritize(accStreams.toList()), accSubs.toList())) }
                    }
                }
            }
        }
        mutex.withLock { send(StreamResult(prioritize(accStreams.toList()), accSubs.toList())) }
    }.flowOn(Dispatchers.IO)

    suspend fun getStreams(baseUrl: String, type: String, videoId: String): StreamResult =
        withContext(Dispatchers.IO) { api.fetchStreams(baseUrl, type, videoId) }

    suspend fun getAddonBaseUrl(addonId: String): String? = withContext(Dispatchers.IO) {
        catalogCache.firstOrNull { it.addonId == addonId }?.baseUrl
            ?: db.addonDao().getAll().firstOrNull { it.id == addonId }?.baseUrl
    }

    suspend fun getSavedPosition(key: String): Long =
        withContext(Dispatchers.IO) { db.playbackDao().getByKey(key)?.positionMs ?: 0L }

    suspend fun deleteSavedPosition(key: String) =
        withContext(Dispatchers.IO) { db.playbackDao().deleteByKey(key) }

    suspend fun savePlayback(
        title: String,
        poster: String?,
        videoId: String,
        streamUrl: String,
        positionMs: Long,
        durationMs: Long,
        imdbId: String? = null,
        season: Int = 0,
        episode: Int = 0
    ) = withContext(Dispatchers.IO) {
        // Don't clutter Continue Watching with barely-started items, and drop
        // entries the user effectively finished (less than 30s left).
        if (durationMs > 0 && durationMs - positionMs < 30_000) {
            db.playbackDao().deleteByKey(streamUrl)
            return@withContext
        }
        if (positionMs < 10_000) return@withContext
        db.playbackDao().upsert(
            PlaybackState(
                key = streamUrl,
                title = title,
                poster = poster,
                videoId = videoId,
                streamUrl = streamUrl,
                positionMs = positionMs,
                durationMs = durationMs,
                imdbId = imdbId,
                season = season,
                episode = episode,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // ---- Watchlist ----

    fun observeWatchlistItem(key: String): Flow<WatchlistItem?> =
        db.watchlistDao().observeByKey(key)

    suspend fun toggleWatchlist(
        addonId: String,
        type: String,
        metaId: String,
        name: String,
        poster: String?
    ) = withContext(Dispatchers.IO) {
        // Read-modify-write: serialize concurrent toggles so a double-tap
        // can't upsert after the delete.
        writeMutex.withLock {
            val key = "$addonId|$type|$metaId"
            if (db.watchlistDao().getByKey(key) != null) {
                db.watchlistDao().deleteByKey(key)
            } else {
                db.watchlistDao().upsert(
                    WatchlistItem(
                        key = key,
                        addonId = addonId,
                        type = type,
                        metaId = metaId,
                        name = name,
                        poster = poster,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // ---- Live TV: M3U playlists, favorites, recents, zap handoff ----

    /** In-memory channel list handoff so the player can zap up/down. */
    @Volatile
    var pendingTvChannels: List<TvChannelRef> = emptyList()
        private set

    /** Index into [pendingTvChannels] of the currently playing channel. */
    @Volatile
    var pendingTvIndex: Int = 0

    fun setTvZapList(channels: List<TvChannelRef>, index: Int) {
        pendingTvChannels = channels
        pendingTvIndex = if (channels.isEmpty()) 0 else index.coerceIn(channels.indices)
    }

    /** Result of loading the stored M3U playlists: channel rows + per-playlist errors. */
    data class M3uLoadResult(
        val rows: List<CatalogRow>,
        /** Human-readable failures, one per failed playlist, naming the playlist host. */
        val errors: List<String>
    )

    /** Serializes read-modify-write settings updates (playlist add/remove, watchlist toggles). */
    private val writeMutex = Mutex()

    /** In-memory M3U playlist cache: normalized URL -> (fetchedAt, channels). */
    private val m3uMemoryCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<M3uChannel>>>()

    /**
     * Adds an M3U/IPTV playlist URL. Fetches + parses it now so a bad URL
     * fails fast with a readable error; the URL is stored for reloads.
     * Returns the channel count. Never throws — validation failures come
     * back as Result.failure.
     */
    suspend fun addM3uPlaylist(url: String): Result<Int> = withContext(Dispatchers.IO) {
        // Strip control characters so the "\n"-joined settings list can never
        // be corrupted by a crafted URL.
        val clean = M3uParser.normalizeUrl(url.trim().filterNot { it == '\n' || it == '\r' })
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            return@withContext Result.failure(
                IllegalArgumentException("Paste a full http(s) playlist URL")
            )
        }
        getM3uChannels(clean).map { channels ->
            writeMutex.withLock {
                val current = settings.m3uPlaylists
                if (current.none { M3uParser.normalizeUrl(it) == clean }) {
                    settings.m3uPlaylists = current + clean
                }
            }
            channels.size
        }
    }

    suspend fun removeM3uPlaylist(url: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val norm = M3uParser.normalizeUrl(url)
            // Clean up orphaned state before dropping the URL: cached
            // channels, disk rows, and any pins/recents pointing at this
            // playlist's channels ("m3u|<stream url>" keys).
            val channels = m3uMemoryCache[norm]?.second
                ?: m3uDiskRows(url).flatMap { row ->
                    CatalogCodec.decodeMetas(row.itemsJson)
                        .map { meta -> M3uChannel(meta.name, meta.id, meta.poster, null) }
                }
            val pinKeys = channels.map { "m3u|${it.url}" }.toSet()
            settings.m3uPlaylists = settings.m3uPlaylists
                .filter { M3uParser.normalizeUrl(it) != norm }
            if (pinKeys.isNotEmpty()) {
                settings.liveFavorites = settings.liveFavorites - pinKeys
                settings.liveRecent = settings.liveRecent.filter { it !in pinKeys }
            }
            m3uMemoryCache.remove(norm)
            // Both spellings: playlists stored by older builds may be raw.
            db.catalogCacheDao().deleteM3uPlaylist(url)
            db.catalogCacheDao().deleteM3uPlaylist(norm)
        }
    }

    /**
     * Loads every stored playlist into grouped "tv" catalog rows.
     * Playlists are fetched CONCURRENTLY (not sequentially), served from
     * the memory/disk cache when fresh (15 min TTL), and failures are
     * reported per-playlist instead of silently dropping channels.
     */
    suspend fun loadM3uRows(): M3uLoadResult = withContext(Dispatchers.IO) {
        val urls = settings.m3uPlaylists
        if (urls.isEmpty()) return@withContext M3uLoadResult(emptyList(), emptyList())
        val outcomes = coroutineScope {
            urls.map { url -> async { loadOnePlaylist(url) } }.awaitAll()
        }
        M3uLoadResult(
            rows = outcomes.flatMap { it.first },
            errors = outcomes.mapNotNull { it.second }
        )
    }

    /** One playlist -> (rows, optional error message naming the playlist). Never throws. */
    private suspend fun loadOnePlaylist(url: String): Pair<List<CatalogRow>, String?> {
        val host = playlistHost(url)
        return getM3uChannels(url).fold(
            onSuccess = { channels -> m3uChannelsToRows(url, host, channels) to null },
            onFailure = { e ->
                // A dead playlist must not nuke the tab: keep the last
                // cached channels visible (if any) and name the failure.
                val stale = m3uDiskRows(url).map { row ->
                    CatalogRow(
                        addonId = row.addonId,
                        addonName = row.addonName,
                        baseUrl = row.baseUrl,
                        catalogName = row.catalogName,
                        type = row.type,
                        catalogId = row.catalogId,
                        items = CatalogCodec.decodeMetas(row.itemsJson)
                    )
                }.filter { it.items.isNotEmpty() }
                val msg = "$host: ${readableM3uError(e)}"
                if (stale.isNotEmpty()) stale to "$msg — showing cached channels"
                else emptyList<CatalogRow>() to msg
            }
        )
    }

    /**
     * Channels for one playlist: fresh memory cache -> fresh disk cache
     * (Room catalog_cache, addonId "m3u") -> network (cached to both on
     * success). Never throws.
     */
    private suspend fun getM3uChannels(url: String): Result<List<M3uChannel>> {
        val key = M3uParser.normalizeUrl(url)
        val now = System.currentTimeMillis()
        m3uMemoryCache[key]?.let { (ts, channels) ->
            if (now - ts < M3U_CACHE_TTL_MS) return Result.success(channels)
        }
        val diskRows = m3uDiskRows(url)
        if (diskRows.isNotEmpty() && diskRows.all { now - it.fetchedAt < M3U_CACHE_TTL_MS }) {
            val channels = diskRows.flatMap { row ->
                val group = row.catalogName.takeIf { it != "All channels" }
                CatalogCodec.decodeMetas(row.itemsJson).map { meta ->
                    M3uChannel(meta.name, meta.id, meta.poster, group)
                }
            }
            m3uMemoryCache[key] = now to channels
            return Result.success(channels)
        }
        return M3uParser.fetch(url).onSuccess { channels ->
            m3uMemoryCache[key] = now to channels
            writeM3uDiskRows(url, playlistHost(url), channels, now)
        }
    }

    /** Cached M3U rows for one playlist from Room (tolerates raw and normalized baseUrls). */
    private suspend fun m3uDiskRows(url: String): List<CatalogCache> {
        val norm = M3uParser.normalizeUrl(url)
        return runCatching {
            db.catalogCacheDao().getAll()
                .filter { it.addonId == "m3u" && M3uParser.normalizeUrl(it.baseUrl) == norm }
        }.getOrDefault(emptyList())
    }

    private suspend fun writeM3uDiskRows(
        url: String,
        host: String,
        channels: List<M3uChannel>,
        now: Long
    ) {
        runCatching {
            // Replace (not merge): a shrunken playlist must not leave stale
            // group rows behind.
            db.catalogCacheDao().deleteM3uPlaylist(url)
            db.catalogCacheDao().upsertAll(
                m3uChannelsToRows(url, host, channels).map { row ->
                    CatalogCache(
                        key = "${row.addonId}|${row.type}|${row.catalogId}",
                        addonId = row.addonId,
                        addonName = row.addonName,
                        baseUrl = row.baseUrl,
                        type = row.type,
                        catalogId = row.catalogId,
                        catalogName = row.catalogName,
                        itemsJson = cappedItemsJson(row.items),
                        fetchedAt = now
                    )
                }
            )
        }
    }

    private fun m3uChannelsToRows(
        playlistUrl: String,
        host: String,
        channels: List<M3uChannel>
    ): List<CatalogRow> {
        if (channels.isEmpty()) return emptyList()
        return channels
            .groupBy { it.group?.takeIf { g -> g.isNotBlank() } ?: "All channels" }
            .map { (group, list) ->
                CatalogRow(
                    addonId = "m3u",
                    addonName = host,
                    baseUrl = playlistUrl,
                    catalogName = group,
                    type = "tv",
                    catalogId = "m3u:${M3uParser.normalizeUrl(playlistUrl).hashCode()}:${group.hashCode()}",
                    items = list.map { ch ->
                        MetaPreview(
                            id = ch.url,
                            type = "tv",
                            name = ch.name,
                            poster = ch.logo
                        )
                    }
                )
            }
    }

    private fun playlistHost(url: String): String =
        runCatching {
            java.net.URI(url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: "IPTV"
        }.getOrDefault("IPTV")

    private fun readableM3uError(e: Throwable): String = when (e) {
        is java.net.UnknownHostException ->
            "couldn't reach the server (check your connection)"
        is java.net.SocketTimeoutException ->
            "the server took too long to respond"
        else -> e.message?.takeIf { it.isNotBlank() } ?: "couldn't load the playlist"
    }

    /** Toggle a channel pin. Keys: "addonId|metaId" ("m3u|<url>" for playlists). */
    fun toggleLiveFavorite(key: String) {
        val cur = settings.liveFavorites.toMutableSet()
        if (!cur.add(key)) cur.remove(key)
        settings.liveFavorites = cur
    }

    /** Push a channel key to the front of the recents (max 20). */
    fun pushLiveRecent(key: String) {
        settings.liveRecent = (listOf(key) + settings.liveRecent.filter { it != key }).take(20)
    }

    // ---- Subtitle search (feature 9) ----

    /**
     * Queries every installed subtitle-capable addon via the Stremio addon
     * protocol ({base}/subtitles/{type}/{imdbId}.json), merged and
     * de-duplicated. Results are cached briefly (6h) in Room.
     */
    suspend fun searchSubtitles(type: String, imdbId: String): List<Subtitle> =
        withContext(Dispatchers.IO) {
            val key = "$type|$imdbId"
            val now = System.currentTimeMillis()
            val cached = db.subtitleCacheDao().getByKey(key)
            if (cached != null && now - cached.fetchedAt < SUBTITLE_CACHE_TTL_MS) {
                return@withContext CatalogCodec.decodeSubtitles(cached.itemsJson)
            }
            val addons = enabledAddons().filter { addon ->
                addon.hasSubtitles && run {
                    val types = addonTypes(addon)
                    types.isEmpty() || type in types
                }
            }
            val results = coroutineScope {
                addons.map { addon ->
                    async {
                        withTimeoutOrNull(STREAM_FETCH_TIMEOUT_MS) {
                            runCatching { api.fetchSubtitles(addon.baseUrl, type, imdbId) }
                                .getOrDefault(emptyList())
                        } ?: emptyList()
                    }
                }.awaitAll().flatten()
            }.distinctBy { it.url }
            if (results.isNotEmpty()) {
                db.subtitleCacheDao().upsert(
                    SubtitleCache(key, CatalogCodec.encodeSubtitles(results), now)
                )
            }
            results
        }

    // ---- Pre-installed addons (feature 4) ----

    /**
     * Fills in missing addon logos from their manifests (one quiet pass per
     * launch for addons installed before logos were stored).
     */
    suspend fun backfillAddonLogos() = withContext(Dispatchers.IO) {
        val dao = db.addonDao()
        for (addon in dao.getAll()) {
            if (addon.logoUrl.isNotBlank()) continue
            try {
                val logo = api.fetchManifest(addon.baseUrl).logo?.takeIf { it.isNotBlank() }
                    ?: continue
                dao.upsert(addon.copy(logoUrl = logo))
            } catch (_: Exception) {
                // Offline or dead URL — try again next launch.
            }
        }
    }
    /**
     * Auto-installs the bundled addons (PlavioMeta + Cinemeta + OpenSubtitles v3
     * + SubSense) once.
     * PlavioMeta is our first-party TMDB metadata addon; Cinemeta stays as fallback.
     * SubSense aggregates subtitles from OpenSubtitles, SubDL, SubSource,
     * Podnapisi and more for wider subtitle coverage.
     * Each addon is attempted at most once (tracked per manifest id), so a
     * user who removes one is never force-fed it again; a failed attempt
     * (e.g. offline first launch) is retried on the next launch.
     */
    suspend fun ensurePreinstalledAddons() = withContext(Dispatchers.IO) {
        val targets = listOf(
            "https://plaviometa.vercel.app/manifest.json",
            "https://plaviohd.vercel.app/manifest.json",
            "https://opensubtitles-v3.strem.io/manifest.json",
            "https://subsense.nepiraw.com/manifest.json"
        )
        for (url in targets) {
            try {
                val baseUrl = StremioApi.normalizeBaseUrl(url)
                val manifest = api.fetchManifest(baseUrl)
                if (settings.isPreinstallDone(manifest.id)) continue
                val alreadyThere = db.addonDao().getAll().any { it.id == manifest.id }
                addAddonFromUrl(url).onSuccess { addon ->
                    if (!alreadyThere) db.addonDao().upsert(addon.copy(preinstalled = true))
                    settings.setPreinstallDone(manifest.id)
                }
            } catch (_: Exception) {
                // Offline or dead URL — try again next launch.
            }
        }
    }

    companion object {
        private const val SUBTITLE_CACHE_TTL_MS = 6L * 3600L * 1000L
        /** Fail-fast budget per addon when resolving streams/subtitles. */
        private const val STREAM_FETCH_TIMEOUT_MS = 20000L
        /**
         * Max bytes per cached catalog row — well under SQLite's ~2MB
         * CursorWindow limit, so a row can never poison reads again.
         */
        private const val MAX_CACHED_ROW_BYTES = 900_000
        /** Manifests rarely change — skip the round-trip for 6h (24h delayed newly search-capable addons). */
        private const val MANIFEST_TTL_MS = 6L * 3600L * 1000L
        /** Hard ceiling for a whole remote-search pass so "Searching…" can't hang. */
        private const val REMOTE_SEARCH_TIMEOUT_MS = 10_000L
        /** Per-addon budget inside the sequential getMetaFromAny walk. */
        private const val META_FETCH_TIMEOUT_MS = 15_000L
        /** Freshness of cached M3U playlists (memory + disk). */
        private const val M3U_CACHE_TTL_MS = 15L * 60L * 1000L
        /** Freshness of the in-memory Live-tab TV rows. */
        private const val TV_ROWS_TTL_MS = 15L * 60L * 1000L
    }

    /**
     * Encodes catalog items for the cache, shrinking the list until the
     * JSON fits safely under the CursorWindow limit.
     */
    private fun cappedItemsJson(items: List<MetaPreview>): String {
        var take = items.size
        var json = CatalogCodec.encodeMetas(items)
        while (json.length > MAX_CACHED_ROW_BYTES && take > 20) {
            take /= 2
            json = CatalogCodec.encodeMetas(items.take(take))
        }
        return json
    }
}
