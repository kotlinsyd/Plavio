package com.pulsestream.app.data

import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.WatchlistItem
import com.pulsestream.app.data.simkl.SimklApi
import com.pulsestream.app.data.trakt.TraktApi
import com.pulsestream.app.data.trakt.TraktManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the in-app library (the watchlist) in sync with the user's Trakt
 * watchlist and Simkl library — GitHub issue #3 ("manageable library").
 *
 * Three-way merge against the key set stored after the last successful
 * sync (SettingsStore.librarySyncedKeys, keys are "type|imdbId"):
 *  - a title present anywhere ends up everywhere (local + both services);
 *  - a title that was synced before but is now gone from the local
 *    library, or gone from every linked service, is removed everywhere —
 *    that's how "delete it in Plavio" (or on Trakt/Simkl) propagates.
 * Adding a title to a service is also how it gets tracked there: Trakt
 * watchlist, Simkl "Plan to Watch".
 *
 * Only IMDb-keyed movies/series take part. Anything else in the local
 * library is left completely alone. A linked service that fails to answer
 * aborts the whole sync untouched — a hiccup can never delete anything.
 */
class LibrarySyncManager(
    private val db: AppDatabase,
    private val settings: SettingsStore
) {
    private val traktApi = TraktApi()
    private val traktManager = TraktManager(settings)
    private val simklApi = SimklApi()

    fun isLinked(): Boolean =
        !settings.traktAccessToken.isNullOrBlank() || !settings.simklToken.isNullOrBlank()

    data class SyncOutcome(val text: String)

    private data class RemoteTitle(val title: String, val source: String)

    private fun finish(text: String): SyncOutcome {
        settings.librarySyncLastResult = text
        return SyncOutcome(text)
    }

    suspend fun sync(): SyncOutcome = withContext(Dispatchers.IO) {
        settings.librarySyncLastAt = System.currentTimeMillis()

        val traktToken =
            if (!settings.traktAccessToken.isNullOrBlank()) traktManager.validAccessToken() else null
        val simklToken = settings.simklToken?.takeIf { it.isNotBlank() }
        if (traktToken == null && simklToken == null) {
            return@withContext finish("Link Trakt or Simkl to sync your library")
        }

        // Local library, IMDb-keyed movies/series only.
        val local = LinkedHashMap<String, WatchlistItem>()
        for (item in db.watchlistDao().getAll()) {
            if ((item.type == "movie" || item.type == "series") && item.metaId.startsWith("tt")) {
                local.putIfAbsent("${item.type}|${item.metaId}", item)
            }
        }

        // Remotes. A linked service that errors aborts the sync untouched.
        val traktRemote: Map<String, RemoteTitle>? = if (traktToken != null) {
            runCatching { traktApi.getWatchlist(traktToken) }.getOrNull()
                ?.associate { "${it.type}|${it.imdbId}" to RemoteTitle(it.title, "trakt") }
                ?: return@withContext finish("Sync failed — Trakt didn't answer. Nothing changed.")
        } else null
        val simklRemote: Map<String, RemoteTitle>? = if (simklToken != null) {
            runCatching { simklApi.getAllItems(simklToken) }.getOrNull()
                ?.associate { "${it.type}|${it.imdbId}" to RemoteTitle(it.title, "simkl") }
                ?: return@withContext finish("Sync failed — Simkl didn't answer. Nothing changed.")
        } else null

        val remotes = listOfNotNull(traktRemote, simklRemote)
        val remoteUnion = remotes.fold(emptySet<String>()) { acc, m -> acc + m.keys }
        val titles = HashMap<String, RemoteTitle>()
        remotes.forEach { titles.putAll(it) }

        val base = settings.librarySyncedKeys
        val deleted = base.filter { key -> key !in local.keys || key !in remoteUnion }.toSet()
        val desired = (local.keys + remoteUnion) - deleted

        // Local writes.
        var addedLocal = 0
        for (key in desired - local.keys) {
            val remote = titles[key] ?: continue
            val (type, imdb) = key.split("|", limit = 2)
            db.watchlistDao().upsert(
                WatchlistItem(
                    key = "${remote.source}|$type|$imdb",
                    addonId = remote.source,
                    type = type,
                    metaId = imdb,
                    name = remote.title.ifBlank { imdb },
                    poster = null,
                    addedAt = System.currentTimeMillis()
                )
            )
            addedLocal++
        }
        var removedLocal = 0
        for (key in local.keys - desired) {
            local[key]?.let { db.watchlistDao().deleteByKey(it.key) }
            removedLocal++
        }

        // Service writes.
        var pushFailed = false
        if (traktToken != null && traktRemote != null) {
            val adds = (desired - traktRemote.keys).map { key ->
                val (type, imdb) = key.split("|", limit = 2)
                TraktApi.WatchlistEntry(type, imdb, titles[key]?.title ?: local[key]?.name ?: "")
            }
            val removes = (traktRemote.keys - desired).map { key ->
                val (type, imdb) = key.split("|", limit = 2)
                TraktApi.WatchlistEntry(type, imdb, "")
            }
            if (!traktApi.addToWatchlist(traktToken, adds)) pushFailed = true
            if (!traktApi.removeFromWatchlist(traktToken, removes)) pushFailed = true
        }
        if (simklToken != null && simklRemote != null) {
            val adds = (desired - simklRemote.keys).map { key ->
                val (type, imdb) = key.split("|", limit = 2)
                SimklApi.ListEntry(type, imdb, titles[key]?.title ?: local[key]?.name ?: "")
            }
            val removes = (simklRemote.keys - desired).map { key ->
                val (type, imdb) = key.split("|", limit = 2)
                SimklApi.ListEntry(type, imdb, "")
            }
            if (!simklApi.addToList(simklToken, adds)) pushFailed = true
            if (!simklApi.removeFromList(simklToken, removes)) pushFailed = true
        }

        if (pushFailed) {
            // Keep the old merge base: the next sync re-derives the same
            // changes and retries the failed pushes.
            return@withContext finish("Synced here — a service update failed, will retry.")
        }

        settings.librarySyncedKeys = desired
        val services = listOfNotNull(
            if (traktRemote != null) "Trakt" else null,
            if (simklRemote != null) "Simkl" else null
        ).joinToString(" + ")
        finish(
            "Synced with $services · ${desired.size} titles" +
                if (addedLocal > 0) " · $addedLocal added here" else ""
        )
    }
}
