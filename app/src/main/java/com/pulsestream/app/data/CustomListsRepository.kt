package com.pulsestream.app.data

import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.CustomList
import com.pulsestream.app.data.db.CustomListItem
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * User-created named lists, separate from the flat watchlist.
 *
 * Takes the [AppDatabase] directly (PulseApp exposes it as `database`) so
 * this can live outside StreamRepository. Write paths that read-then-write
 * are serialized with a mutex, mirroring StreamRepository's watchlist toggle.
 */
class CustomListsRepository(private val db: AppDatabase) {

    private val dao get() = db.customListDao()
    private val writeMutex = Mutex()

    /** All lists, newest first. */
    val lists: Flow<List<CustomList>> = dao.observeLists()

    fun observeItems(listId: String): Flow<List<CustomListItem>> =
        dao.observeItems(listId)

    fun observeItemCount(listId: String): Flow<Int> =
        dao.observeItemCount(listId)

    /** Ids of the lists that currently contain [itemKey]. */
    fun observeListsContaining(itemKey: String): Flow<List<String>> =
        dao.observeListsContaining(itemKey)

    suspend fun getList(id: String): CustomList? = withContext(Dispatchers.IO) {
        dao.getList(id)
    }

    /** Creates a list and returns its id. */
    suspend fun createList(name: String): String = withContext(Dispatchers.IO) {
        val trimmed = name.trim().take(60)
        require(trimmed.isNotEmpty()) { "List name must not be empty" }
        val id = UUID.randomUUID().toString()
        dao.upsertList(
            CustomList(id = id, name = trimmed, createdAt = System.currentTimeMillis())
        )
        id
    }

    suspend fun renameList(id: String, name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim().take(60)
        require(trimmed.isNotEmpty()) { "List name must not be empty" }
        dao.renameList(id, trimmed)
    }

    /** Deletes the list and all of its items. */
    suspend fun deleteList(id: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            dao.deleteItemsByList(id)
            dao.deleteList(id)
        }
    }

    suspend fun addToList(
        listId: String,
        addonId: String,
        type: String,
        metaId: String,
        name: String,
        poster: String?
    ) = withContext(Dispatchers.IO) {
        dao.addItem(
            CustomListItem(
                listId = listId,
                itemKey = "$addonId|$type|$metaId",
                addonId = addonId,
                type = type,
                metaId = metaId,
                name = name,
                poster = poster,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeFromList(listId: String, itemKey: String) =
        withContext(Dispatchers.IO) {
            dao.removeItem(listId, itemKey)
        }

    /** Shared key format with the watchlist: "addonId|type|metaId". */
    fun itemKey(addonId: String, type: String, metaId: String): String =
        "$addonId|$type|$metaId"
}
