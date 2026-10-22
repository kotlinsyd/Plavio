package com.pulsestream.app.data

import com.pulsestream.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.CatalogCache
import com.pulsestream.app.data.db.CustomList
import com.pulsestream.app.data.db.CustomListItem
import com.pulsestream.app.data.db.InstalledAddon
import com.pulsestream.app.data.db.PlaybackState
import com.pulsestream.app.data.db.SubtitleCache
import com.pulsestream.app.data.db.WatchlistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import androidx.room.withTransaction
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single source of truth: Room for persistence, StremioApi for network,
 * in-memory caches for catalogs (search) and the pending playback request (navigation).
 */

/** Outcome of a manual per-addon manifest refresh. */
sealed interface AddonRefreshOutcome {
    data class Updated(val oldVersion: String, val newVersion: String) : AddonRefreshOutcome
    data class UpToDate(val version: String) : AddonRefreshOutcome
}

data class BackupPreview(val version: Int, val addonCount: Int, val watchlistCount: Int,
                         val playbackCount: Int, val customListCount: Int, val customListItemCount: Int)

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

    /**
     * Pending manifest URL from a stremio:// deep link. MainActivity stashes
     * the https:// manifest URL here; AddonsScreen picks it up and offers
     * to add it as an addon, then clears it.
     */
    private val _pendingManifestUrl = MutableStateFlow<String?>(null)
    val pendingManifestUrl: StateFlow<String?> = _pendingManifestUrl.asStateFlow()

    fun setPendingManifestUrl(url: String) { _pendingManifestUrl.value = url }
    fun clearPendingManifestUrl() { _pendingManifestUrl.value = null }

    /** In-memory handoff for the catalog "view all" screen. */
    @Volatile
    var pendingCatalog: CatalogRow? = null

    suspend fun addAddonFromUrl(input: String): Result<InstalledAddon> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = StremioApi.normalizeBaseUrl(input)
            val manifest = addonRequest { api.fetchManifest(baseUrl) }
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
            // Upsert may replace an existing manifest URL or capabilities;
            // discard every result derived from its previous configuration.
            manifestCache.remove(baseUrl)
            invalidateAddonCaches(addon.id)
            addon
        }
    }

    /**
     * Re-fetch an installed addon's manifest.json from its stored base URL
     * and update the local record (name, version, description, types,
     * resources, configure flag, logo). User-owned fields — enabled,
     * manual order, starred, pre-installed flag, show-on-home, install
     * date — are preserved. On any fetch/parse failure the stored record
     * is left untouched and the error is returned.
     */
    suspend fun refreshAddon(id: String): Result<AddonRefreshOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val dao = db.addonDao()
            val current = dao.getAll().firstOrNull { it.id == id }
                ?: throw IllegalStateException("Addon not found")
            val manifest = api.fetchManifest(current.baseUrl)
            val updated = current.copy(
                name = manifest.name,
                version = manifest.version,
                description = manifest.description,
                types = manifest.types.joinToString(","),
                resources = manifest.resources.joinToString(","),
                configurable = manifest.configurable,
                logoUrl = manifest.logo ?: current.logoUrl
            )
            dao.upsert(updated)
            // Drop the cached manifest so catalogs/streams use the fresh one.
            manifestCache.remove(current.baseUrl)
            invalidateAddonCaches(id)
            if (current.version.isNotBlank() && manifest.version != current.version) {
                AddonRefreshOutcome.Updated(current.version, manifest.version)
            } else {
                AddonRefreshOutcome.UpToDate(manifest.version)
            }
        }
    }

    suspend fun removeAddon(id: String) = withContext(Dispatchers.IO) {
        val baseUrl = db.addonDao().getAll().firstOrNull { it.id == id }?.baseUrl
        db.addonDao().deleteById(id)
        if (baseUrl != null) manifestCache.remove(baseUrl)
        invalidateAddonCaches(id)
        db.catalogCacheDao().deleteByAddonId(id)
    }

    /** User on/off switch for an addon. Disabled addons are excluded everywhere. */
    suspend fun setAddonEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.addonDao().getAll().firstOrNull { it.id == id }?.let { addon ->
            db.addonDao().upsert(addon.copy(enabled = enabled))
            // All provider-derived caches must move together on enable/disable.
            manifestCache.remove(addon.baseUrl)
            invalidateAddonCaches(id)
            if (!enabled) db.catalogCacheDao().deleteByAddonId(id)
        }
    }

    private suspend fun invalidateAddonCaches(addonId: String) {
        catalogCache = catalogCache.filter { it.addonId != addonId }
        tvRowsMemoryCache = null
        trailersCache = null
        // Persisted catalog rows are provider-derived too; otherwise cached
        // Live/Home results can reappear after disable or manifest reconfigure.
        db.catalogCacheDao().deleteByAddonId(addonId)
        // Subtitle keys aggregate all providers; without source ownership, clear all.
        db.subtitleCacheDao().clearAll()
    }

    /**
     * Export raw addon URLs only for the caller's encrypted private snapshot.
     * Locked placeholders are excluded from ordinary sync exports so a prior
     * redacted import cannot overwrite a good private URL on another device.
     * Rollback callers may opt in because that snapshot is trusted local state.
     */
    suspend fun exportPortableAddonUrls(
        includeLockedPlaceholdersForRollback: Boolean = false
    ): Map<String, PortableAddonUrlConfig> = withContext(Dispatchers.IO) {
        db.addonDao().getAll().asSequence()
            .filterNot { isLockedAddonUrlPlaceholder(it.baseUrl) && !includeLockedPlaceholdersForRollback }
            .associate { it.id to PortableAddonUrlConfig(it.baseUrl, it.enabled) }
    }

    /**
     * Restore only URLs for addons already installed locally. Validation,
     * missing-ID checks and conflict checks all finish before the first write;
     * writes are then atomic and never fetch manifests or contact providers.
     * [allowOverwriteForRollback] is reserved for a trusted pre-import snapshot.
     */
    suspend fun restorePortableAddonUrls(
        configs: Map<String, PortableAddonUrlConfig>,
        allowOverwriteForRollback: Boolean = false
    ): Result<Int> = try {
        withContext(Dispatchers.IO) {
            require(configs.size <= MAX_PORTABLE_ADDON_URLS) { "Too many addon URLs in the private snapshot." }
            configs.forEach { (id, config) ->
                require(id.isNotBlank() && id.length <= 300 && id == id.trim() && id.none { Character.isISOControl(it) }) {
                    "Invalid addon id in the private snapshot."
                }
                require(config.baseUrl.isNotBlank() && config.baseUrl.length <= MAX_ADDON_URL_LENGTH &&
                    isHttpUrl(config.baseUrl)) { "Invalid addon URL in the private snapshot." }
                require(!isLockedAddonUrlPlaceholder(config.baseUrl) ||
                    allowOverwriteForRollback && !config.enabled) {
                    "Locked addon placeholders can only be restored from a trusted rollback snapshot."
                }
            }

            val changed = mutableListOf<Pair<InstalledAddon, InstalledAddon>>()
            db.withTransaction {
                val dao = db.addonDao()
                val installed = dao.getAll().associateBy(InstalledAddon::id)
                val unknownIds = configs.keys - installed.keys
                require(unknownIds.isEmpty()) {
                    "Restore the profile first; addon URL config references addons that are not installed."
                }
                configs.forEach { (id, incoming) ->
                    val current = installed.getValue(id)
                    val currentIsPlaceholder = isLockedAddonUrlPlaceholder(current.baseUrl)
                    require(allowOverwriteForRollback || currentIsPlaceholder ||
                        current.baseUrl == incoming.baseUrl || !isConfiguredAddonUrl(current.baseUrl)) {
                        "Addon '$id' already has a different local URL; refusing to overwrite it."
                    }
                }
                configs.forEach { (id, incoming) ->
                    val current = installed.getValue(id)
                    if (current.baseUrl != incoming.baseUrl || current.enabled != incoming.enabled) {
                        val updated = current.copy(baseUrl = incoming.baseUrl, enabled = incoming.enabled)
                        dao.upsert(updated)
                        changed += current to updated
                    }
                }
            }

            // Invalidate only after the Room transaction commits. A full clear
            // avoids retaining responses keyed by either the old or new URL.
            if (changed.isNotEmpty()) {
                manifestCache.clear()
                catalogCache = emptyList()
                trailersCache = null
                tvRowsMemoryCache = null
                db.catalogCacheDao().clearAll()
                db.subtitleCacheDao().clearAll()
            }
            Result.success(changed.size)
        }
    } catch (cancel: CancellationException) { throw cancel }
      catch (e: Exception) { Result.failure(e) }

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

    /**
     * Trending movies + series for the Trailers tab. PlavioMeta first (its
     * metas carry trailerStreams), then any other enabled catalog addon.
     * Returns catalog items only — trailer YouTube ids resolve lazily per
     * tap via [getMetaFromAny], so the tab loads fast.
     */
    suspend fun trendingForTrailers(): List<TrailerItem> = withContext(Dispatchers.IO) {
        val addons = catalogAddons()
        if (addons.isEmpty()) return@withContext emptyList()
        val ordered = addons.sortedBy { metaRank(it) }
        val out = linkedMapOf<String, TrailerItem>()
        for (addon in ordered) {
            val manifest = runCatching { cachedManifest(addon.baseUrl) }.getOrNull() ?: continue
            for (type in listOf("movie", "series")) {
                val catalog = manifest.catalogs.firstOrNull { it.type == type && "trend" in it.id.lowercase() }
                    ?: manifest.catalogs.firstOrNull { it.type == type }
                    ?: continue
                val items = runCatching { api.fetchCatalog(addon.baseUrl, type, catalog.id) }.getOrNull()
                    ?: continue
                items.forEach { meta ->
                    out.putIfAbsent(
                        "${addon.id}|${meta.id}",
                        TrailerItem(addon.id, addon.baseUrl, type, meta)
                    )
                }
            }
            // One addon's trending rows cover movies+series — no need to merge more.
            if (out.isNotEmpty()) break
        }
        out.values.toList()
    }

    /**
     * TTL-cached trending trailers, split into (movies, series). Revisiting
     * the Trailers tab within the TTL reuses memory — no spinner, no network.
     * Pass [forceRefresh] for pull-to-refresh.
     */
    suspend fun trendingTrailersCached(
        forceRefresh: Boolean = false
    ): Pair<List<TrailerItem>, List<TrailerItem>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        trailersCache?.let { (ts, data) ->
            if (!forceRefresh && now - ts < TRAILERS_CACHE_TTL_MS) return@withContext data
        }
        val all = trendingForTrailers()
        val data = all.filter { it.type == "movie" } to all.filter { it.type == "series" }
        trailersCache = now to data
        data
    }

    /** In-memory Trailers tab data: (fetchedAt, movies to series). */
    @Volatile
    private var trailersCache: Pair<Long, Pair<List<TrailerItem>, List<TrailerItem>>>? = null

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
    private val addonRequestGate = Semaphore(4)

    private suspend fun <T> addonRequest(block: suspend () -> T): T {
        addonRequestGate.acquire()
        try { return block() } finally { addonRequestGate.release() }
    }

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
        val installed = enabledAddons().filter { it.showOnHome }
        val successfulManifests = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val authoritativeKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val listedManifestKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val rows = coroutineScope {
            installed.map { addon ->
                async {
                    val manifest = cachedManifest(addon.baseUrl) ?: return@async emptyList<CatalogRow>()
                    successfulManifests.add(addon.id)
                    try {
                        db.addonDao().upsert(addon.copy(
                            resources = manifest.resources.joinToString(","),
                            types = manifest.types.joinToString(","),
                            configurable = manifest.configurable
                        ))
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { /* metadata refresh must not lose existing rows */ }
                    if ("catalog" !in manifest.resources) return@async emptyList()
                    if (manifest.catalogs.isEmpty()) authoritativeKeys.add("${addon.id}|*")
                    coroutineScope {
                        manifest.catalogs.map { catalog ->
                            async {
                                val key = "${addon.id}|${catalog.type}|${catalog.id}"
                                listedManifestKeys.add(key)
                                val items = try {
                                    addonRequest { api.fetchCatalog(addon.baseUrl, catalog.type, catalog.id) }
                                } catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { null }
                                if (items != null) authoritativeKeys.add(key)
                                items?.takeIf { it.isNotEmpty() }?.let {
                                    CatalogRow(addon.id, addon.name, addon.baseUrl, catalog.name,
                                        catalog.type, catalog.id, it)
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }
                }
            }.awaitAll().flatten()
        }.distinctBy { "${it.addonId}|${it.type}|${it.catalogId}" }

        val now = System.currentTimeMillis()
        val newRows = rows.associateBy { "${it.addonId}|${it.type}|${it.catalogId}" }
        val existingCache = db.catalogCacheDao().getAll()
        // Preserve failures/offline cached values. Delete only keys proven obsolete
        // by a successful manifest or successfully fetched empty catalog response.
        for (old in existingCache) {
            if (old.addonId !in successfulManifests) continue
            val key = old.key
            val manifestAuthoritative = "${old.addonId}|*" in authoritativeKeys
            val obsolete = !manifestAuthoritative && key !in listedManifestKeys
            if (manifestAuthoritative || obsolete || key in authoritativeKeys) {
                if (key !in newRows) db.catalogCacheDao().deleteByKey(key)
            }
        }
        if (rows.isNotEmpty()) db.catalogCacheDao().upsertAll(rows.map { row ->
            CatalogCache("${row.addonId}|${row.type}|${row.catalogId}", row.addonId,
                row.addonName, row.baseUrl, row.type, row.catalogId, row.catalogName,
                cappedItemsJson(row.items), now)
        })
        // Cache is authoritative only if at least one provider succeeded, including
        // a valid empty manifest; a total outage must remain stale/retryable.
        if (authoritativeKeys.isNotEmpty()) settings.homeCatalogsRefreshedAt = now
        val retained = existingCache.filter { cached ->
            cached.key !in newRows && cached.key !in authoritativeKeys &&
                (cached.addonId !in successfulManifests || cached.key in listedManifestKeys) &&
                installed.any { it.id == cached.addonId }
        }.map { cached -> CatalogRow(cached.addonId, cached.addonName, cached.baseUrl,
            cached.catalogName, cached.type, cached.catalogId, CatalogCodec.decodeMetas(cached.itemsJson)) }
        val displayedRows = (rows + retained).distinctBy { "${it.addonId}|${it.type}|${it.catalogId}" }
        updateSearchCache(displayedRows)
        sortRowsByPreference(displayedRows, installed.associateBy { it.id })
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
     * Live TV rows for the Live tab: standard "tv" catalogs and explicitly
     * named live/channel catalogs with custom resource types. Served from
     * the in-memory cache when fresh (15 min TTL) so
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
                    val liveCatalogs = (manifest?.catalogs ?: emptyList()).filter {
                        isLiveCatalog(it.type, it.id, it.name)
                    }
                    liveCatalogs.map { catalog ->
                        async {
                            fetchGate.acquire()
                            try {
                                val items = try { addonRequest { api.fetchCatalog(addon.baseUrl, catalog.type, catalog.id) } }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { emptyList() }
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
                .filter {
                    it.addonId in visibleIds && isLiveCatalog(it.type, it.catalogId, it.catalogName)
                }
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
    /** Emits a cumulative result snapshot whenever a provider finishes. A slow
     * addon is bounded independently and cannot erase faster providers' results. */
    fun searchRemoteProgressive(query: String, type: String? = null) = channelFlow {
        val q = query.trim()
        if (q.length < 2) { send(emptyList()); return@channelFlow }
        val types = if (type != null) listOf(type) else listOf("movie", "series")
        val resultMap = java.util.concurrent.ConcurrentHashMap<String, CachedMeta>()
        val providers = enabledAddons().filter { it.hasCatalog }
        providers.map { addon ->
            launch {
                val found = withTimeoutOrNull(REMOTE_SEARCH_PROVIDER_TIMEOUT_MS) {
                    val manifest = cachedManifest(addon.baseUrl) ?: return@withTimeoutOrNull emptyList()
                    buildList {
                        for (t in types) {
                            for (catalog in manifest.catalogs.filter { it.type == t && it.supportsSearch }.take(3)) {
                                try {
                                    addonRequest { api.searchCatalog(addon.baseUrl, t, catalog.id, q) }.forEach { meta ->
                                        add(CachedMeta(addon.id, addon.name, addon.baseUrl, meta.copy(type = t)))
                                    }
                                } catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { /* another catalog/provider can still succeed */ }
                            }
                        }
                    }
                } ?: emptyList()
                found.forEach { resultMap.putIfAbsent(it.addonId + it.meta.id, it) }
                send(resultMap.values.toList())
            }
        }.joinAll()
    }

    suspend fun searchRemote(query: String, type: String? = null): List<CachedMeta> =
        searchRemoteProgressive(query, type).last()

    /**
     * Combined search: instant local-cache matches plus live addon results,
     * then de-duplicated across addons and RANKED (v0.12.0) — previously the
     * raw cache order surfaced obscure old titles first (e.g. "sultan"
     * listed 1930s titles before Salman Khan's Sultan, 2016).
     */
    suspend fun searchAll(query: String, type: String? = null): List<CachedMeta> =
        withContext(Dispatchers.IO) {
            // Warm the in-memory search cache from the DB when Home hasn't
            // loaded it yet this session (e.g. the user opened the Search
            // tab first, or the cache was pruned) — otherwise local search
            // silently returns nothing.
            if (catalogCache.isEmpty()) {
                runCatching { loadCatalogRowsCached() }
            }
            val local = search(query, type)
            val seen = local.map { it.addonId + it.meta.id }.toMutableSet()
            // A remote failure must never wipe out local results.
            val remote = try { searchRemote(query, type) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { emptyList() }
            val merged = local + remote.filter { seen.add(it.addonId + it.meta.id) }
            rankSearchResults(merged, query)
        }

    /**
     * "Popular right now" for the Search tab's landing state (v0.12.4).
     * With a TMDB key: TMDB's weekly trending chart. Without one: the
     * best in-app source — titles from cached catalog rows whose name/id
     * reads as a chart (trending / popular / top — Cinemeta's "top"
     * catalogs land in that bucket), best-scored rows first, flattened
     * in row order and de-duplicated by title id. Empty when neither
     * source can supply anything (the landing then shows recents only).
     */
    suspend fun loadPopularTitles(limit: Int = 10): List<CachedMeta> =
        withContext(Dispatchers.IO) {
            val key = settings.tmdbApiKey.takeIf { it.isNotBlank() }
            if (key != null) {
                val trending = TmdbClient.trending(key, limit)
                if (trending.isNotEmpty()) {
                    return@withContext trending.map { meta ->
                        CachedMeta(
                            addonId = "tmdb",
                            addonName = "TMDB",
                            baseUrl = "https://api.themoviedb.org/3",
                            meta = meta
                        )
                    }
                }
            }
            val rows = runCatching { loadCatalogRowsCached() }.getOrDefault(emptyList())
            if (rows.isEmpty()) return@withContext emptyList()
            fun chartScore(row: CatalogRow): Int {
                val text = "${row.catalogName} ${row.catalogId}".lowercase()
                var score = 0
                if ("trending" in text) score += 3
                if ("popular" in text) score += 3
                if ("top" in text) score += 2
                if ("featured" in text) score += 1
                return score
            }
            rows.filter { it.type == "movie" || it.type == "series" }
                .map { it to chartScore(it) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .flatMap { (row, _) -> row.items.map { row to it } }
                .distinctBy { it.second.id }
                .take(limit)
                .map { (row, meta) ->
                    CachedMeta(
                        addonId = row.addonId,
                        addonName = row.addonName,
                        baseUrl = row.baseUrl,
                        meta = meta
                    )
                }
        }

    private val searchYearPattern = Regex("(?<!\\d)(18\\d{2}|19\\d{2}|20\\d{2})(?!\\d)")

    private fun searchYearOf(releaseInfo: String?): Int? =
        releaseInfo?.let { searchYearPattern.find(it)?.value?.toIntOrNull() }

    private fun searchRatingOf(imdbRating: String?): Double? =
        imdbRating?.substringBefore('/')?.trim()?.toDoubleOrNull()?.takeIf { it in 0.0..10.0 }

    /** Lowercase, punctuation-stripped title form for match/dedupe comparisons. */
    private fun normalizeSearchTitle(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    // TMDB popularity enrichment cache (v0.12.1): one entry per normalized
    // query, 10-minute TTL — repeat searches and progressive re-ranks never
    // refetch, and a TMDB outage costs at most one bounded wait per query.
    private val tmdbPopularityCache =
        java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<TmdbClient.TmdbSearchHit>>>()

    /**
     * TMDB popularity hits for [query], or an empty list when no TMDB key
     * is configured or the lookup fails/times out. Bounded at 2.5s so
     * enrichment can never make search feel slower than the addon fetches
     * it already waits for; failures are silently ignored by design.
     */
    private suspend fun tmdbPopularityFor(query: String): List<TmdbClient.TmdbSearchHit> {
        val key = settings.tmdbApiKey.takeIf { it.isNotBlank() } ?: return emptyList()
        val cacheKey = normalizeSearchTitle(query)
        val now = System.currentTimeMillis()
        tmdbPopularityCache[cacheKey]?.let { (ts, hits) ->
            if (now - ts < 10 * 60 * 1000L) return hits
        }
        val hits = withTimeoutOrNull(2_500L) {
            TmdbClient.searchWithPopularity(key, query)
        } ?: emptyList()
        if (tmdbPopularityCache.size > 32) tmdbPopularityCache.clear()
        tmdbPopularityCache[cacheKey] = now to hits
        return hits
    }

    /**
     * Search ordering: exact title match first, then prefix match, then
     * contains. Within a tier: TMDB popularity, then TMDB vote average
     * (v0.12.1 — only when a TMDB key is configured and hits matched);
     * otherwise/without a key, higher IMDb rating first (the closest
     * popularity signal MetaPreview carries), newer year as the next
     * tiebreak, preferred metadata source after that. The sort is stable,
     * so equal rows keep their incoming (provider) order. Typo tolerance
     * is untouched — ranking only reorders what the existing search
     * already found.
     *
     * Before ranking, the same title offered by several addons (e.g.
     * PlavioMeta and Cinemeta both carrying tt… ids) collapses into ONE
     * row — the preferred source's copy. Entries whose known years
     * conflict (remakes) are never merged.
     */
    private suspend fun rankSearchResults(results: List<CachedMeta>, query: String): List<CachedMeta> {
        if (results.size < 2) return results
        val q = normalizeSearchTitle(query)
        val byAddonId = try { enabledAddons().associateBy { it.id } } catch (_: Exception) { emptyMap() }
        fun addonRank(cm: CachedMeta): Int = byAddonId[cm.addonId]?.let(::metaRank) ?: 2
        fun bestOf(group: List<CachedMeta>): CachedMeta = group.minWithOrNull(
            compareBy<CachedMeta> { addonRank(it) }
                .thenByDescending { searchRatingOf(it.meta.imdbRating) ?: -1.0 }
                .thenByDescending { searchYearOf(it.meta.releaseInfo) ?: 0 }
        ) ?: group.first()

        // Cross-addon dedupe by (normalized title, type).
        val groups = LinkedHashMap<Pair<String, String>, MutableList<CachedMeta>>()
        for (cm in results) {
            groups.getOrPut(normalizeSearchTitle(cm.meta.name) to cm.meta.type) { mutableListOf() }.add(cm)
        }
        val deduped = ArrayList<CachedMeta>(groups.size)
        for ((_, group) in groups) {
            if (group.size == 1) {
                deduped += group[0]
                continue
            }
            val byYear = group.groupBy { searchYearOf(it.meta.releaseInfo) }
            val knownYears = byYear.keys.filterNotNull().sorted()
            if (knownYears.size <= 1) {
                deduped += bestOf(group)
            } else {
                // Remakes: one merged row per distinct known year;
                // year-less copies join the newest bucket.
                knownYears.forEachIndexed { index, year ->
                    val yearless: List<CachedMeta> =
                        if (index == knownYears.lastIndex) byYear[null].orEmpty() else emptyList()
                    deduped += bestOf(byYear[year].orEmpty() + yearless)
                }
            }
        }

        fun matchTier(name: String): Int {
            val n = normalizeSearchTitle(name)
            return when {
                q.isEmpty() -> 2
                n == q -> 0
                n.startsWith(q) -> 1
                n.contains(q) -> 2
                else -> 3
            }
        }

        // TMDB popularity enrichment (v0.12.1, owner's "best fit" call):
        // when a TMDB key is configured, rank by TMDB popularity + vote
        // average — the signals that actually separate Salman Khan's
        // Sultan (2016) from the 1930s namesakes. Matching is by
        // normalized title + type, preferring a year-equal hit when the
        // result's year is known. No key / no hits → the v0.12.0
        // IMDb-rating order below applies unchanged.
        val tmdbHits = tmdbPopularityFor(query)
        if (tmdbHits.isEmpty()) {
            return deduped.sortedWith(
                compareBy<CachedMeta> { matchTier(it.meta.name) }
                    .thenByDescending { searchRatingOf(it.meta.imdbRating) ?: -1.0 }
                    .thenByDescending { searchYearOf(it.meta.releaseInfo) ?: 0 }
                    .thenBy { addonRank(it) }
            )
        }
        val popularityOf = java.util.IdentityHashMap<CachedMeta, Double>()
        val voteOf = java.util.IdentityHashMap<CachedMeta, Double>()
        for (cm in deduped) {
            val title = normalizeSearchTitle(cm.meta.name)
            val year = searchYearOf(cm.meta.releaseInfo)
            val candidates = tmdbHits.filter {
                normalizeSearchTitle(it.title) == title && it.type == cm.meta.type
            }
            val hit = candidates.firstOrNull { year != null && it.year == year }
                ?: candidates.maxByOrNull { it.popularity }
            if (hit != null) {
                popularityOf[cm] = hit.popularity
                voteOf[cm] = hit.voteAverage
            }
        }
        return deduped.sortedWith(
            compareBy<CachedMeta> { matchTier(it.meta.name) }
                .thenByDescending { popularityOf[it] ?: -1.0 }
                .thenByDescending { voteOf[it] ?: -1.0 }
                .thenByDescending { searchRatingOf(it.meta.imdbRating) ?: -1.0 }
                .thenByDescending { searchYearOf(it.meta.releaseInfo) ?: 0 }
                .thenBy { addonRank(it) }
        )
    }

    /** In-memory manifest cache so Home/Live/search don't refetch manifests per load. */
    private val manifestCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, AddonManifest>>()

    private suspend fun cachedManifest(baseUrl: String): AddonManifest? {
        val now = System.currentTimeMillis()
        manifestCache[baseUrl]?.let { (ts, manifest) ->
            if (now - ts < MANIFEST_TTL_MS) return manifest
        }
        val manifest = try { addonRequest { api.fetchManifest(baseUrl) } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return null }
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
     * PlavioMeta base URL (the user's configured URL when they set a TMDB
     * key). Powers /related and /person, which need TMDB server-side.
     */
    suspend fun plavioMetaBaseUrl(): String? = withContext(Dispatchers.IO) {
        enabledAddons().firstOrNull { isPlavioMeta(it) }?.baseUrl
    }

    /** "More like this" sections for a title; empty when unavailable/keyless. */
    suspend fun getRelated(type: String, id: String): List<RelatedSection> =
        withContext(Dispatchers.IO) {
            val base = plavioMetaBaseUrl() ?: return@withContext emptyList()
            withTimeoutOrNull(META_FETCH_TIMEOUT_MS) {
                runCatching { api.fetchRelated(base, type, id) }.getOrNull()
            } ?: emptyList()
        }

    /** Actor filmography; null when unavailable/keyless. */
    suspend fun getPerson(personId: Int): PersonDetail? = withContext(Dispatchers.IO) {
        val base = plavioMetaBaseUrl() ?: return@withContext null
        withTimeoutOrNull(META_FETCH_TIMEOUT_MS) {
            runCatching { api.fetchPerson(base, personId) }.getOrNull()
        }
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
    /** Resolves a Live channel using its own resource type and catalog origin. */
    fun liveStreamResultsFlow(channel: TvChannelRef): Flow<StreamResult> =
        streamResultsFlow(
            channel.type, channel.metaId, preferredAddonId = channel.addonId,
            preserveHeaderVariants = true
        )

    fun streamResultsFlow(
        type: String,
        videoId: String,
        preferredAddonId: String? = null,
        preserveHeaderVariants: Boolean = false
    ): Flow<StreamResult> = channelFlow {
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
                    .thenByDescending { it.sourceId == preferredAddonId }
                    .thenBy { it.sourceId }
            )
        val mutex = Mutex()
        val seenStreams = LinkedHashSet<String>()
        fun streamIdentity(stream: StreamItem): String = listOf(
            stream.url, stream.infoHash.orEmpty(), stream.fileIdx?.toString().orEmpty(),
            stream.filePath.orEmpty(),
            if (preserveHeaderVariants) LivePlaybackPolicy.candidateIdentity(stream) else ""
        ).joinToString("|")
        val seenSubs = LinkedHashSet<String>()
        val accStreams = mutableListOf<StreamItem>()
        val accSubs = mutableListOf<Subtitle>()
        coroutineScope {
            // Foreground playback must not queue behind Home/Live catalog
            // refreshes on addonRequestGate. Keep this flow bounded separately,
            // and give the selected channel's own addon the first request slot.
            val streamGate = Semaphore(4)
            val requestOrder = addons.sortedWith(
                compareByDescending<InstalledAddon> { it.id == preferredAddonId }
                    .thenByDescending { it.starred }
            )
            requestOrder.forEach { addon ->
                launch {
                    streamGate.acquire()
                    val res = try {
                        // The network budget starts after a request slot is
                        // granted, not while waiting behind unrelated work.
                        withTimeoutOrNull(STREAM_FETCH_TIMEOUT_MS) {
                            try { api.fetchStreams(addon.baseUrl, type, videoId) }
                            catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { null }
                        }
                    } finally {
                        streamGate.release()
                    } ?: return@launch
                    var changed = false
                    mutex.withLock {
                        res.streams.forEach { s ->
                            if (seenStreams.add(streamIdentity(s))) {
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
        // Debrid: resolve infoHash-only streams to direct HTTP links.
        // Runs after the main fetch so direct streams show first; resolved
        // streams are appended as they complete (parallel, non-blocking).
        // Only active when at least one debrid API key is configured.
        // Per stream, providers are tried in order (TorBox → Real-Debrid)
        // and the first success wins.
        data class DebridProvider(
            val tag: String,
            val resolve: suspend (String, Int?, String?) -> String?
        )
        val debridProviders = buildList {
            val torboxKey = settings.torboxApiKey
            if (!torboxKey.isNullOrBlank()) {
                val torbox = com.pulsestream.app.data.debrid.TorBoxApi(torboxKey)
                add(DebridProvider("⚡ TorBox") { hash, fileIdx, filePath ->
                    val started = System.nanoTime()
                    try {
                        val result = torbox.resolveCachedDetailed(hash, fileIdx, filePath)
                        val outcome = when (result) {
                            is com.pulsestream.app.data.debrid.DebridResolveResult.Resolved -> "succeeded"
                            is com.pulsestream.app.data.debrid.DebridResolveResult.NotCached -> "no_results"
                            is com.pulsestream.app.data.debrid.DebridResolveResult.Error ->
                                if (result.isAuthError) "authentication_needed" else "failed"
                        }
                        SourceDiagnostics.record("TorBox", outcome, (System.nanoTime() - started) / 1_000_000)
                        (result as? com.pulsestream.app.data.debrid.DebridResolveResult.Resolved)?.url
                    }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) {
                        SourceDiagnostics.record("TorBox", "failed", (System.nanoTime() - started) / 1_000_000)
                        null
                    }
                })
            }
            val rdKey = settings.realDebridApiKey
            if (!rdKey.isNullOrBlank()) {
                val rd = com.pulsestream.app.data.debrid.RealDebridApi(rdKey)
                add(DebridProvider("⚡ RD") { hash, fileIdx, filePath ->
                    try { rd.resolveCachedToHttp(hash, fileIdx, filePath) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { null }
                })
            }
        }
        if (debridProviders.isNotEmpty()) {
            val pending = mutex.withLock {
                accStreams.filter { it.infoHash != null && it.url.startsWith("infohash:") }
            }
            if (pending.isNotEmpty()) {
                val resolveGate = Semaphore(3)
                coroutineScope {
                    pending.forEach { stream ->
                        launch {
                            var resolved: Pair<String, String>? = null
                            resolveGate.acquire()
                            try {
                                withTimeoutOrNull(45_000) {
                                    for (provider in debridProviders) {
                                        val httpUrl = provider.resolve(stream.infoHash!!, stream.fileIdx, stream.filePath)
                                        if (httpUrl != null && isPlayableLiveStreamUrl(httpUrl)) {
                                            resolved = provider.tag to httpUrl
                                            break
                                        }
                                    }
                                }
                            } finally {
                                resolveGate.release()
                            }
                            val (tag, httpUrl) = resolved ?: return@launch
                            var changed = false
                            mutex.withLock {
                                val idx = accStreams.indexOfFirst {
                                    it.infoHash == stream.infoHash && it.fileIdx == stream.fileIdx &&
                                        it.filePath == stream.filePath && it.url.startsWith("infohash:")
                                }
                                if (idx >= 0 && seenStreams.add(streamIdentity(stream.copy(url = httpUrl, infoHash = null)))) {
                                    val old = accStreams[idx]
                                    accStreams[idx] = old.copy(
                                        url = httpUrl,
                                        infoHash = null,
                                        resolvedInfoHash = old.infoHash,
                                        sourceName = tag + (old.sourceName?.let { " · $it" } ?: "")
                                    )
                                    changed = true
                                }
                            }
                            if (changed) {
                                mutex.withLock { send(StreamResult(prioritize(accStreams.toList()), accSubs.toList())) }
                            }
                        }
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
        // Preserve the endpoint exactly for the request and durable storage;
        // comparisonKey is only for deduplication/cache identity.
        val clean = url.trim().filterNot { it == '\n' || it == '\r' }
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            return@withContext Result.failure(
                IllegalArgumentException("Paste a full http(s) playlist URL")
            )
        }
        getM3uChannels(clean).map { channels ->
            writeMutex.withLock {
                val current = settings.m3uPlaylists
                if (current.none { M3uParser.comparisonKey(it) == M3uParser.comparisonKey(clean) }) {
                    settings.m3uPlaylists = current + clean
                }
            }
            channels.size
        }
    }

    suspend fun removeM3uPlaylist(url: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val norm = M3uParser.comparisonKey(url)
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
                .filter { M3uParser.comparisonKey(it) != norm }
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
        val key = M3uParser.comparisonKey(url)
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
        val norm = M3uParser.comparisonKey(url)
        return runCatching {
            db.catalogCacheDao().getAll()
                .filter { it.addonId == "m3u" && M3uParser.comparisonKey(it.baseUrl) == norm }
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
                    catalogId = "m3u:${M3uParser.comparisonKey(playlistUrl).hashCode()}:${group.hashCode()}",
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
                            try { addonRequest { api.fetchSubtitles(addon.baseUrl, type, imdbId) } }
                            catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { emptyList() }
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
            "https://hd.plavio.site/manifest.json",
            "https://v3-cinemeta.strem.io/manifest.json",
            "https://opensubtitles-v3.strem.io/manifest.json",
            "https://subsense.nepiraw.com/manifest.json"
        )
        for (url in targets) {
            try {
                val baseUrl = StremioApi.normalizeBaseUrl(url)
                val manifest = addonRequest { api.fetchManifest(baseUrl) }
                // Migrate only the exact historical bare PlavioHD host. A user
                // configured URL (including options/query/key) is never replaced.
                val existing = db.addonDao().getAll().firstOrNull { it.id == manifest.id }
                val legacyPlavioHd = "https://plaviohd.edon.workers.dev/manifest.json"
                val previousPlavioHd = "https://plaviohd.vercel.app/manifest.json"
                if (url == "https://hd.plavio.site/manifest.json" &&
                    (existing?.baseUrl == legacyPlavioHd || existing?.baseUrl == previousPlavioHd)) {
                    db.addonDao().upsert(existing.copy(baseUrl = baseUrl))
                    invalidateAddonCaches(existing.id)
                }
                if (settings.isPreinstallDone(manifest.id)) continue
                val alreadyThere = existing != null
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
        private const val REMOTE_SEARCH_PROVIDER_TIMEOUT_MS = 5_000L
        /** Per-addon budget inside the sequential getMetaFromAny walk. */
        private const val META_FETCH_TIMEOUT_MS = 15_000L
        /** Freshness of cached M3U playlists (memory + disk). */
        private const val M3U_CACHE_TTL_MS = 15L * 60L * 1000L
        /** Freshness of the in-memory Live-tab TV rows. */
        private const val TV_ROWS_TTL_MS = 15L * 60L * 1000L
        /** Freshness of the in-memory Trailers-tab data. */
        private const val TRAILERS_CACHE_TTL_MS = 15L * 60L * 1000L
        private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
        private const val MAX_BACKUP_ROWS = 50_000
        private const val MAX_BACKUP_SETTINGS = 2_000
        private const val MAX_BACKUP_STRING = 32_768
        private const val MAX_PORTABLE_ADDON_URLS = 1_000
        private const val MAX_ADDON_URL_LENGTH = 8_192
        private const val LOCKED_ADDON_URL_PLACEHOLDER_PATH = "/__plavio_encrypted_config_required__"
    }

    /**
     * Encodes catalog items for the cache, shrinking the list until the
     * JSON fits safely under the CursorWindow limit.
     */
    private fun cappedItemsJson(items: List<MetaPreview>): String {
        fun bytes(value: String) = value.toByteArray(Charsets.UTF_8).size
        var kept = items
        var json = CatalogCodec.encodeMetas(kept)
        while (bytes(json) > MAX_CACHED_ROW_BYTES && kept.isNotEmpty()) {
            if (kept.size > 1) {
                kept = kept.dropLast(maxOf(1, kept.size / 2))
            } else {
                // Bound large user/provider-controlled text fields as well as list count.
                val item = kept.single()
                val shortened = item.copy(
                    name = item.name.take(256),
                    description = item.description?.take(2_000),
                    poster = item.poster?.take(2_000),
                    background = item.background?.take(2_000),
                    logo = item.logo?.take(2_000)
                )
                val candidate = CatalogCodec.encodeMetas(listOf(shortened))
                if (bytes(candidate) <= MAX_CACHED_ROW_BYTES) return candidate
                kept = emptyList()
            }
            json = CatalogCodec.encodeMetas(kept)
        }
        return if (bytes(json) <= MAX_CACHED_ROW_BYTES) json else "[]"
    }

    // ------------------------------------------------------------------
    // Versioned local backup. Credentials, caches, download records/media,
    // onboarding state and cloud-auth tokens are never exported.
    // ------------------------------------------------------------------
    private data class ParsedBackup(
        val version: Int,
        val settings: Map<String, Any?>?,
        val addons: List<InstalledAddon>?,
        val watchlist: List<WatchlistItem>?,
        val playback: List<PlaybackState>?,
        val lists: List<CustomList>?,
        val listItems: List<CustomListItem>?,
        val apiKeys: Map<String, String>?
    )

    suspend fun exportBackupJson(enabledCategories: Set<String>? = null): Result<String> = try {
        withContext(Dispatchers.IO) {
            val root = JSONObject().apply {
                put("format", "plavio-backup"); put("version", 2)
                put("exportedAt", System.currentTimeMillis()); put("appVersion", BuildConfig.VERSION_NAME)
                put("coverage", "settings,addons,watchlist,playback,customLists; downloads excluded")
                // Sync category filtering (user's order): skip disabled categories
                val includeSettings = enabledCategories?.contains("settings") ?: true
                val includeAddons = enabledCategories?.contains("addons") ?: true
                val includeWatchlist = enabledCategories?.contains("watchlist") ?: true
                val includePlayback = enabledCategories?.contains("watch_history") ?: true
                val includeCustomLists = enabledCategories?.contains("custom_lists") ?: true
                val includeSearchHistory = enabledCategories?.contains("search_history") ?: true
                if (includeSettings) {
                    val prefsJson = JSONObject()
                    settings.exportAllPrefs().forEach { (k, v) ->
                        if (!isSecretPreference(k)) when (v) {
                            is Boolean, is Int, is Long, is Float, is Double -> prefsJson.put(k, v)
                            is String -> prefsJson.put(k, safeBackupSetting(k, v))
                            is Set<*> -> prefsJson.put(k, JSONArray(v.map { it.toString() }))
                            null -> prefsJson.put(k, JSONObject.NULL)
                        }
                    }
                    put("settings", prefsJson)
                    // API keys: simple sync (user's order) - include directly in backup
                    // when the api_keys category is enabled. No separate passphrase.
                    if (enabledCategories?.contains("api_keys") ?: false) {
                        val apiKeysJson = JSONObject()
                        settings.exportApiKeysForBackup().forEach { (k, v) -> apiKeysJson.put(k, v) }
                        put("api_keys", apiKeysJson)
                    }
                }
                if (includeAddons) {
                    put("addons", JSONArray(db.addonDao().getAll().map { a -> JSONObject().apply {
                        val requiresEncryptedUrl = addonNeedsEncryptedUrl(a.baseUrl)
                        put("id", a.id); put("name", a.name); put("version", a.version)
                        put("description", a.description); put("baseUrl", safeAddonBackupUrl(a.baseUrl))
                        if (requiresEncryptedUrl) put("encryptedUrlRequired", true)
                        put("types", a.types); put("resources", a.resources); put("preinstalled", a.preinstalled)
                        put("enabled", a.enabled); put("configurable", a.configurable); put("showOnHome", a.showOnHome)
                        put("logoUrl", safeAddonAuxUrl(a.logoUrl)); put("sortOrder", a.sortOrder)
                        put("starred", a.starred); put("addedAt", a.addedAt)
                    } }))
                }
                if (includeWatchlist) {
                    put("watchlist", JSONArray(db.watchlistDao().getAll().map { w -> JSONObject().apply {
                        put("key", w.key); put("addonId", w.addonId); put("type", w.type); put("metaId", w.metaId)
                        put("name", w.name); put("poster", w.poster?.let(::safeBackupUrl) ?: JSONObject.NULL); put("addedAt", w.addedAt)
                    } }))
                }
                if (includePlayback) {
                    put("playback", JSONArray(db.playbackDao().getAll().map { state -> JSONObject().apply {
                        put("key", state.key); put("title", state.title)
                        put("poster", state.poster?.let(::safeBackupUrl) ?: JSONObject.NULL); put("videoId", state.videoId)
                        put("streamUrl", safeBackupUrl(state.streamUrl)); put("positionMs", state.positionMs)
                        put("durationMs", state.durationMs); put("imdbId", state.imdbId ?: JSONObject.NULL)
                        put("season", state.season); put("episode", state.episode); put("updatedAt", state.updatedAt)
                    } }))
                }
                if (includeCustomLists) {
                    put("customLists", JSONArray(db.customListDao().getLists().map { l -> JSONObject().apply {
                        put("id", l.id); put("name", l.name); put("createdAt", l.createdAt)
                    } }))
                    put("customListItems", JSONArray(db.customListDao().getAllItems().map { item -> JSONObject().apply {
                        put("listId", item.listId); put("itemKey", item.itemKey); put("addonId", item.addonId)
                        put("type", item.type); put("metaId", item.metaId); put("name", item.name)
                        put("poster", item.poster?.let(::safeBackupUrl) ?: JSONObject.NULL); put("addedAt", item.addedAt)
                    } }))
                }
                // Search history is stored in SharedPreferences, handled via settings if enabled
            }
            val payload = root.toString()
            require(payload.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup exceeds the 10 MiB safety limit." }
            Result.success(payload)
        }
    } catch (cancel: CancellationException) { throw cancel }
      catch (e: Exception) { Result.failure(e) }

    /** Fully validates a backup without mutating app state; suitable for a replacement preview. */
    fun previewBackupJson(json: String): Result<BackupPreview> = try {
        val parsed = parseBackup(json)
        Result.success(BackupPreview(parsed.version, parsed.addons?.size ?: 0,
            parsed.watchlist?.size ?: 0, parsed.playback?.size ?: 0,
            parsed.lists?.size ?: 0, parsed.listItems?.size ?: 0))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun importBackupJson(json: String): Result<Unit> = try {
        val parsed = parseBackup(json) // validation is complete before any mutation
        withContext(Dispatchers.IO) {
            val previousPrefs = settings.exportAllPrefs().toMutableMap()
            try {
                db.withTransaction {
                    parsed.addons?.let { rows -> db.addonDao().clearAll(); rows.forEach { db.addonDao().upsert(it) } }
                    parsed.watchlist?.let { rows -> db.watchlistDao().clearAll(); rows.forEach { db.watchlistDao().upsert(it) } }
                    parsed.playback?.let { rows -> db.playbackDao().clearAll(); rows.forEach { db.playbackDao().upsert(it) } }
                    parsed.lists?.let { rows ->
                        db.customListDao().clearAllItems(); db.customListDao().clearLists()
                        if (rows.isNotEmpty()) db.customListDao().insertLists(rows)
                        val items = parsed.listItems.orEmpty()
                        if (items.isNotEmpty()) db.customListDao().insertItems(items)
                    } ?: parsed.listItems?.let { rows ->
                        db.customListDao().clearAllItems()
                        if (rows.isNotEmpty()) db.customListDao().insertItems(rows)
                    }
                    db.subtitleCacheDao().clearAll(); db.catalogCacheDao().clearAll()
                    parsed.settings?.let { settings.importAllPrefs(it) }
                    // Restore API keys (simple sync, user's order)
                    parsed.apiKeys?.let { settings.importApiKeysFromBackup(it) }
                }
            } catch (cancel: CancellationException) {
                settings.importAllPrefs(previousPrefs)
                throw cancel
            } catch (e: Exception) {
                settings.importAllPrefs(previousPrefs)
                throw e
            }
            // All provider-owned cached responses are invalid after replacing settings/addons.
            manifestCache.clear(); catalogCache = emptyList(); trailersCache = null; tvRowsMemoryCache = null
            Result.success(Unit)
        }
    } catch (cancel: CancellationException) { throw cancel }
      catch (e: Exception) { Result.failure(e) }

    /** A clean profile target for cloud switching: reset portable preferences,
     * replaceable libraries/addons and custom lists are explicitly empty. */
    fun emptyBackupJson(): Result<String> = try {
        val payload = JSONObject().apply {
            put("format", "plavio-backup"); put("version", 2)
            put("exportedAt", System.currentTimeMillis()); put("appVersion", BuildConfig.VERSION_NAME)
            put("coverage", "settings,addons,watchlist,playback,customLists; downloads excluded")
            put("settings", JSONObject())
            put("addons", JSONArray()); put("watchlist", JSONArray()); put("playback", JSONArray())
            put("customLists", JSONArray()); put("customListItems", JSONArray())
        }.toString()
        Result.success(payload)
    } catch (e: Exception) { Result.failure(e) }

    private fun parseBackup(json: String): ParsedBackup {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup exceeds the 10 MiB safety limit." }
        val root = JSONObject(json)
        require(root.opt("format") is String && root.getString("format") == "plavio-backup") { "Not a Plavio backup file." }
        val versionValue = root.opt("version")
        require(versionValue is Number) { "Backup version is missing or invalid." }
        val version = versionValue.toInt()
        require(version in 1..2 && versionValue.toDouble() == version.toDouble()) { "This backup version is not supported." }
        val settingsJson = if (root.has("settings")) root.get("settings") as? JSONObject
            ?: throw IllegalArgumentException("Backup settings must be an object.") else null
        require((settingsJson?.length() ?: 0) <= MAX_BACKUP_SETTINGS) { "Too many settings in backup." }
        val prefs = settingsJson?.let { obj ->
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next().also { require(it.length <= 120) }
                    if (isSecretPreference(key)) continue
                    val v = obj.get(key)
                    put(key, when (v) {
                        JSONObject.NULL -> null
                        is JSONArray -> {
                            require(v.length() <= 1_000)
                            (0 until v.length()).map { index -> v.get(index).also { require(it is String) } as String }.toSet()
                        }
                        is String -> v.also { require(it.length <= MAX_BACKUP_STRING) }
                        is Boolean, is Int, is Long, is Double -> v
                        else -> throw IllegalArgumentException("Invalid setting value for $key.")
                    })
                }
            }
        }
        fun section(name: String, max: Int = MAX_BACKUP_ROWS): List<JSONObject>? {
            if (!root.has(name)) return null
            val arr = root.get(name) as? JSONArray ?: throw IllegalArgumentException("Backup section '$name' must be an array.")
            require(arr.length() <= max) { "Too many rows in '$name'." }
            return (0 until arr.length()).map { arr.get(it) as? JSONObject
                ?: throw IllegalArgumentException("Invalid row in '$name'.") }
        }
        fun str(o: JSONObject, k: String, required: Boolean = true, max: Int = 4_096): String? {
            if (!o.has(k) || o.isNull(k)) { require(!required) { "Missing '$k'." }; return null }
            val v = o.get(k) as? String ?: throw IllegalArgumentException("Invalid '$k'.")
            require(v.length <= max && (!required || v.isNotBlank())) { "Invalid '$k'." }
            return v
        }
        fun optionalUrl(o: JSONObject, k: String): String? {
            val value = str(o, k, required = false, max = 8_192) ?: return null
            require(value.isBlank() || isHttpUrl(value)) { "Invalid URL in '$k'." }
            return value.takeIf { it.isNotBlank() }?.let(::safeBackupUrl)
        }
        fun num(o: JSONObject, k: String, default: Long? = null): Long {
            if (!o.has(k)) return default ?: throw IllegalArgumentException("Missing '$k'.")
            val value = o.get(k) as? Number ?: throw IllegalArgumentException("Invalid '$k'.")
            val n = value.toLong(); require(value.toDouble() == n.toDouble()) { "Invalid '$k'." }; return n
        }
        fun bool(o: JSONObject, k: String, default: Boolean): Boolean = if (!o.has(k)) default else
            (o.get(k) as? Boolean ?: throw IllegalArgumentException("Invalid '$k'."))
        fun array(name: String) = section(name)?.map { it }
        val addons = array("addons")?.map { o ->
            val id = str(o,"id",max=300)!!; val url = str(o,"baseUrl",max=8_192)!!
            require(isHttpUrl(url)) { "Addon URL must be an HTTP(S) URL." }
            val encryptedUrlRequired = bool(o, "encryptedUrlRequired", false)
            val importedUrl = if (encryptedUrlRequired) lockedAddonUrlPlaceholder(url) else safeBackupUrl(url)
            InstalledAddon(id, str(o,"name",max=512)!!, str(o,"version",false,128) ?: "",
                str(o,"description",false,4_096) ?: "", importedUrl, str(o,"types",false,1_000) ?: "",
                str(o,"resources",false,1_000) ?: "catalog,meta,stream", bool(o,"preinstalled",false),
                bool(o,"enabled",true) && !encryptedUrlRequired, bool(o,"configurable",false), bool(o,"showOnHome",true),
                str(o,"logoUrl",false,8_192)?.let(::safeAddonAuxUrl) ?: "", num(o,"sortOrder",0).toInt(),
                bool(o,"starred",false), num(o,"addedAt",0))
        }?.also { require(it.map(InstalledAddon::id).distinct().size == it.size) { "Duplicate addon ids." } }
        val watch = array("watchlist")?.map { o -> WatchlistItem(str(o,"key",max=1_024)!!,
            str(o,"addonId",false,300) ?: "", str(o,"type",false,80) ?: "", str(o,"metaId",false,1_024) ?: "",
            str(o,"name",false,512) ?: "", optionalUrl(o,"poster"), num(o,"addedAt",0)) }
            ?.also { require(it.map(WatchlistItem::key).distinct().size == it.size) { "Duplicate watchlist keys." } }
        val play = array("playback")?.map { o ->
            val url = str(o,"streamUrl",false,8_192) ?: ""
            require(url.isBlank() || isHttpUrl(url)) { "Invalid playback URL." }
            PlaybackState(str(o,"key",max=1_024)!!, str(o,"title",false,512) ?: "",
                optionalUrl(o,"poster"), str(o,"videoId",false,1_024) ?: "",
                safeBackupUrl(url), num(o,"positionMs",0), num(o,"durationMs",0),
                str(o,"imdbId",false,256), num(o,"season",0).toInt(), num(o,"episode",0).toInt(), num(o,"updatedAt",0))
        }?.also {
            require(it.map(PlaybackState::key).distinct().size == it.size) { "Duplicate playback keys." }
            require(it.all { row -> row.positionMs >= 0 && row.durationMs >= 0 }) { "Invalid playback position." }
        }
        val lists = array("customLists")?.map { o -> CustomList(str(o,"id",max=300)!!,
            str(o,"name",max=256)!!, num(o,"createdAt",0)) }
            ?.also { require(it.map(CustomList::id).distinct().size == it.size) { "Duplicate custom list ids." } }
        val listItems = array("customListItems")?.map { o -> CustomListItem(str(o,"listId",max=300)!!,
            str(o,"itemKey",max=1_024)!!, str(o,"addonId",false,300) ?: "", str(o,"type",false,80) ?: "",
            str(o,"metaId",false,1_024) ?: "", str(o,"name",false,512) ?: "",
            optionalUrl(o,"poster"), num(o,"addedAt",0)) }
            ?.also { require(it.map { row -> row.listId to row.itemKey }.distinct().size == it.size) { "Duplicate custom-list items." } }
        if (version >= 2) require(lists != null && listItems != null) { "Version 2 backup is missing custom-list data." }
        if (listItems != null && lists != null) require(listItems.all { item -> lists.any { it.id == item.listId } }) { "A custom-list item references a missing list." }
        for (name in listOf("addons","watchlist","playback","customLists","customListItems")) if (root.has(name)) section(name)
        // Parse API keys (simple sync, user's order)
        val apiKeys: Map<String, String>? = if (root.has("api_keys")) {
            val obj = root.get("api_keys") as? JSONObject ?: throw IllegalArgumentException("Backup api_keys must be an object.")
            buildMap<String, String> {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k: String = keys.next()
                    val v = obj.optString(k, "")
                    if (v.isNotBlank()) put(k, v)
                }
            }
        } else null
        return ParsedBackup(version, prefs, addons, watch, play, lists, listItems, apiKeys)
    }

    private fun isSecretPreference(key: String): Boolean {
        val k = key.lowercase()
        return k in setOf("tmdb_api_key", "simkl_token", "torbox_api_key", "realdebrid_api_key", "trakt_access_token", "trakt_refresh_token") ||
            listOf("token", "api_key", "apikey", "password", "secret", "auth").any { it in k } ||
            k.startsWith("plavio_privacy")
    }

    private fun isHttpUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun isLockedAddonUrlPlaceholder(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.path == LOCKED_ADDON_URL_PLACEHOLDER_PATH && uri.userInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && isHttpUrl(value)
    }.getOrDefault(false)

    private fun isConfiguredAddonUrl(value: String): Boolean = value.isNotBlank() &&
        !isLockedAddonUrlPlaceholder(value)

    /** A URL may contain secrets in any component, so only ordinary public roots are retained. */
    private fun addonNeedsEncryptedUrl(value: String): Boolean {
        if (isLockedAddonUrlPlaceholder(value)) return true
        return runCatching {
            val uri = java.net.URI(value)
            !isHttpUrl(value) || uri.userInfo != null || uri.rawQuery != null ||
                uri.rawFragment != null || uri.path.orEmpty().isNotEmpty() && uri.path != "/"
        }.getOrDefault(true)
    }

    private fun addonOrigin(value: String): String? = runCatching {
        val uri = java.net.URI(value)
        if (!isHttpUrl(value)) null
        else java.net.URI(uri.scheme, null, uri.host, uri.port, null, null, null).toASCIIString()
    }.getOrNull()

    /** Plaintext addon metadata may retain only a credential-free public root or the origin. */
    private fun safeAddonBackupUrl(value: String): String {
        if (!addonNeedsEncryptedUrl(value)) return value.take(MAX_ADDON_URL_LENGTH)
        return addonOrigin(value) ?: "https://invalid.invalid"
    }

    /** Logo URLs can be signed or derived from addon config; do not export non-root components. */
    private fun safeAddonAuxUrl(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val uri = java.net.URI(value)
            if (!isHttpUrl(value)) ""
            else if (uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                (uri.path.isNullOrEmpty() || uri.path == "/")) value.take(MAX_ADDON_URL_LENGTH)
            else addonOrigin(value).orEmpty()
        }.getOrDefault("")
    }

    /** Marked URLs always become a durable, non-working placeholder until private restore succeeds. */
    private fun lockedAddonUrlPlaceholder(value: String): String {
        val origin = addonOrigin(value) ?: throw IllegalArgumentException("Invalid addon URL placeholder.")
        return origin + LOCKED_ADDON_URL_PLACEHOLDER_PATH
    }

    /** Sanitize URL-bearing portable settings (notably newline-delimited M3U endpoints). */
    private fun safeBackupSetting(key: String, value: String): String {
        if (key.equals("m3u_playlists", ignoreCase = true)) {
            return value.lineSequence().joinToString("\n") { line ->
                if (isHttpUrl(line)) safeBackupUrl(line) else line.take(MAX_BACKUP_STRING)
            }
        }
        return value.take(MAX_BACKUP_STRING)
    }

    /** Remove URL credentials from local exports while keeping the configured endpoint and safe options. */
    private fun safeBackupUrl(value: String): String {
        if (!isHttpUrl(value)) return value.take(8_192)
        return try {
            val uri = java.net.URI(value)
            val safeQuery = uri.rawQuery?.split('&')?.filterNot { component ->
                val key = component.substringBefore('=').lowercase()
                listOf("token", "key", "secret", "password", "auth", "credential",
                    "signature", "sig", "jwt", "access", "refresh", "session", "code").any { it in key }
            }?.joinToString("&")
            java.net.URI(uri.scheme, null, uri.host, uri.port, uri.path, safeQuery, null).toASCIIString()
        } catch (_: Exception) { value.take(8_192) }
    }


}
