package com.pulsestream.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "addons")
data class InstalledAddon(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val description: String,
    val baseUrl: String,
    val types: String,
    /** Comma-separated manifest resources, e.g. "catalog,meta,stream". Added in DB v2. */
    val resources: String = "catalog,meta,stream",
    /** True when this addon was installed automatically by the app (Cinemeta, OpenSubtitles). Added in DB v3. */
    val preinstalled: Boolean = false,
    /** User-facing on/off switch. Disabled addons are excluded from catalogs, metadata, streams and subtitle search. Added in DB v4. */
    val enabled: Boolean = true,
    /** Manifest behaviorHints.configurable — the addon has a web configure page. Added in DB v4. */
    val configurable: Boolean = false,
    /** Whether this addon's catalogs appear on the Home screen. Added in DB v5. */
    val showOnHome: Boolean = true,
    /** Manifest logo URL for the addon icon. Added in DB v6. */
    val logoUrl: String = "",
    /** Manual ordering position (user-rearranged). Added in DB v7. */
    val sortOrder: Int = 0,
    /** Starred addons get priority for catalogs, fetching and playback. Added in DB v7. */
    val starred: Boolean = false,
    val addedAt: Long
) {
    val resourceSet: Set<String>
        get() = resources.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val hasCatalog: Boolean get() = "catalog" in resourceSet
    val hasMeta: Boolean get() = "meta" in resourceSet
    val hasStream: Boolean get() = "stream" in resourceSet
    val hasSubtitles: Boolean get() = "subtitles" in resourceSet
}

@Dao
interface AddonDao {
    @Query("SELECT * FROM addons ORDER BY sortOrder ASC, addedAt ASC")
    fun observeAll(): Flow<List<InstalledAddon>>

    @Query("SELECT * FROM addons ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getAll(): List<InstalledAddon>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(addon: InstalledAddon)

    @Query("UPDATE addons SET showOnHome = :show WHERE id = :id")
    suspend fun setShowOnHome(id: String, show: Boolean)

    @Query("DELETE FROM addons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM addons")
    suspend fun clearAll()
}

@Entity(tableName = "playback")
data class PlaybackState(
    @PrimaryKey val key: String,
    val title: String,
    val poster: String?,
    val videoId: String,
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    /** IMDb id + season/episode so Continue Watching can offer skip-segments and subtitle search. Added in DB v3. */
    val imdbId: String? = null,
    val season: Int = 0,
    val episode: Int = 0,
    val updatedAt: Long
)

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback ORDER BY updatedAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<PlaybackState>>

    @Query("SELECT * FROM playback WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): PlaybackState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PlaybackState)

    @Query("DELETE FROM playback WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT * FROM playback")
    suspend fun getAll(): List<PlaybackState>

    @Query("DELETE FROM playback")
    suspend fun clearAll()
}

/** Cached catalog rows (per addon + type + catalog id) so Home renders instantly. Added in DB v3. */
@Entity(tableName = "catalog_cache")
data class CatalogCache(
    @PrimaryKey val key: String,
    val addonId: String,
    val addonName: String,
    val baseUrl: String,
    val type: String,
    val catalogId: String,
    val catalogName: String,
    val itemsJson: String,
    val fetchedAt: Long
)

@Dao
interface CatalogCacheDao {
    @Query("SELECT * FROM catalog_cache")
    suspend fun getAll(): List<CatalogCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CatalogCache>)

    @Query("DELETE FROM catalog_cache WHERE addonId NOT IN (:ids)")
    suspend fun pruneExcept(ids: List<String>)

    @Query("DELETE FROM catalog_cache")
    suspend fun clearAll()

    @Query("DELETE FROM catalog_cache WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM catalog_cache WHERE addonId = :addonId")
    suspend fun deleteByAddonId(addonId: String)

    /**
     * Drops rows whose JSON exceeds SQLite's ~2MB CursorWindow limit —
     * reading one would crash with SQLiteBlobTooBigException.
     */
    @Query("DELETE FROM catalog_cache WHERE length(CAST(itemsJson AS BLOB)) > :maxBytes")
    suspend fun pruneOversized(maxBytes: Int): Int

    /**
     * Drops every cached M3U row of one playlist (exact stored baseUrl).
     * Callers pass both the raw and the normalized URL to cover playlists
     * stored by older builds.
     */
    @Query("DELETE FROM catalog_cache WHERE addonId = 'm3u' AND baseUrl = :baseUrl")
    suspend fun deleteM3uPlaylist(baseUrl: String)
}

/** User's watchlist — titles bookmarked for later. Added in DB v3. */
@Entity(tableName = "watchlist")
data class WatchlistItem(
    @PrimaryKey val key: String,
    val addonId: String,
    val type: String,
    val metaId: String,
    val name: String,
    val poster: String?,
    val addedAt: Long
)

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchlistItem>>

    @Query("SELECT * FROM watchlist WHERE `key` = :key LIMIT 1")
    fun observeByKey(key: String): Flow<WatchlistItem?>

    @Query("SELECT * FROM watchlist WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): WatchlistItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchlistItem)

    @Query("DELETE FROM watchlist WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT * FROM watchlist")
    suspend fun getAll(): List<WatchlistItem>

    @Query("DELETE FROM watchlist")
    suspend fun clearAll()
}

/** User-created custom lists — named collections beyond the flat watchlist. Added in DB v9. */
@Entity(tableName = "custom_lists")
data class CustomList(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

/**
 * Items inside a custom list. itemKey mirrors the watchlist key format
 * ("addonId|type|metaId"). addonId is stored (not in the original spec) so
 * tapping an item can navigate back to its detail screen.
 * Added in DB v9.
 */
@Entity(
    tableName = "custom_list_items",
    primaryKeys = ["listId", "itemKey"]
)
data class CustomListItem(
    val listId: String,
    val itemKey: String,
    val addonId: String,
    val type: String,
    val metaId: String,
    val name: String,
    val poster: String?,
    val addedAt: Long
)

@Dao
interface CustomListDao {
    @Query("SELECT * FROM custom_lists ORDER BY createdAt DESC")
    fun observeLists(): Flow<List<CustomList>>

    @Query("SELECT * FROM custom_lists ORDER BY createdAt DESC")
    suspend fun getLists(): List<CustomList>

    @Query("SELECT * FROM custom_lists WHERE id = :id LIMIT 1")
    suspend fun getList(id: String): CustomList?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(list: CustomList)

    @Query("UPDATE custom_lists SET name = :name WHERE id = :id")
    suspend fun renameList(id: String, name: String)

    @Query("DELETE FROM custom_lists WHERE id = :id")
    suspend fun deleteList(id: String)

    @Query("DELETE FROM custom_lists")
    suspend fun clearLists()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLists(lists: List<CustomList>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<CustomListItem>)

    @Query("SELECT * FROM custom_list_items ORDER BY addedAt DESC")
    suspend fun getAllItems(): List<CustomListItem>

    @Query("DELETE FROM custom_list_items")
    suspend fun clearAllItems()

    @Query("SELECT * FROM custom_list_items WHERE listId = :listId ORDER BY addedAt DESC")
    fun observeItems(listId: String): Flow<List<CustomListItem>>

    @Query("SELECT listId FROM custom_list_items WHERE itemKey = :itemKey")
    fun observeListsContaining(itemKey: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: CustomListItem)

    @Query("DELETE FROM custom_list_items WHERE listId = :listId AND itemKey = :itemKey")
    suspend fun removeItem(listId: String, itemKey: String)

    @Query("DELETE FROM custom_list_items WHERE listId = :listId")
    suspend fun deleteItemsByList(listId: String)

    @Query("SELECT COUNT(*) FROM custom_list_items WHERE listId = :listId")
    fun observeItemCount(listId: String): Flow<Int>
}

/** Briefly-cached subtitle search results (per type + IMDb id). Added in DB v3. */
@Entity(tableName = "subtitle_cache")
data class SubtitleCache(
    @PrimaryKey val key: String,
    val itemsJson: String,
    val fetchedAt: Long
)

@Dao
interface SubtitleCacheDao {
    @Query("DELETE FROM subtitle_cache")
    suspend fun clearAll()
    @Query("SELECT * FROM subtitle_cache WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): SubtitleCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: SubtitleCache)
}

/** Offline download tracking. Added in DB v8. */
@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val fileName: String,
    val filePath: String,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    /** QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED */
    val status: String = "QUEUED",
    val error: String? = null,
    val headersJson: String = "{}",
    val createdAt: Long,
    val completedAt: Long? = null
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadItem?

    @Query("SELECT * FROM downloads WHERE url = :url AND status != 'CANCELLED' LIMIT 1")
    suspend fun getByUrl(url: String): DownloadItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadItem)

    @Query("UPDATE downloads SET downloadedBytes = :bytes, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long, status: String)

    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String? = null)

    @Query("UPDATE downloads SET status = 'COMPLETED', downloadedBytes = totalBytes, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: String, completedAt: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt ASC")
    suspend fun getActive(): List<DownloadItem>
}
