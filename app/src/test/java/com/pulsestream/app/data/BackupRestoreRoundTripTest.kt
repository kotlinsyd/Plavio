package com.pulsestream.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pulsestream.app.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BackupRestoreRoundTripTest {
    private fun fixture(test: suspend (AppDatabase, SettingsStore, StreamRepository) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsStore(context)
        val repo = StreamRepository(db, StremioApi(), settings)
        try { test(db, settings, repo) } finally { db.close() }
    }

    @Test fun portableBackupRestoresCustomListMembershipAndExplicitlyResetsEmptyProfile() = fixture { db, settings, repo ->
        settings.autoplay = false
        db.customListDao().upsertList(CustomList("list", "Keep this list", 1))
        db.customListDao().addItem(CustomListItem("list", "entry", "addon", "movie", "tt123", "Title", null, 1))
        db.watchlistDao().upsert(WatchlistItem("watch", "addon", "movie", "tt123", "Title", null, 1))
        val exported = repo.exportBackupJson().getOrThrow()
        repo.importBackupJson(repo.emptyBackupJson().getOrThrow()).getOrThrow()
        assertTrue(settings.autoplay)
        assertTrue(db.customListDao().getLists().isEmpty())
        assertTrue(db.watchlistDao().getAll().isEmpty())
        repo.importBackupJson(exported).getOrThrow()
        assertFalse(settings.autoplay)
        assertEquals("Keep this list", db.customListDao().getLists().single().name)
        assertEquals("entry", db.customListDao().getAllItems().single().itemKey)
        assertEquals("watch", db.watchlistDao().getAll().single().key)
    }

    @Test fun malformedBackupDoesNotChangeExistingLibraryOrPreferences() = fixture { db, settings, repo ->
        settings.autoplay = false
        db.watchlistDao().upsert(WatchlistItem("keep", "addon", "movie", "tt123", "Keep", null, 1))
        val result = repo.importBackupJson("""{"format":"plavio-backup","version":2,"watchlist":"invalid","settings":{}}""")
        assertTrue(result.isFailure)
        assertEquals("keep", db.watchlistDao().getAll().single().key)
        assertFalse(settings.autoplay)
    }

    @Test fun injectedDatabaseFailureRollsBackEarlierTableClears() = fixture { db, settings, repo ->
        settings.autoplay = false
        db.customListDao().upsertList(CustomList("old", "Existing", 1))
        db.watchlistDao().upsert(WatchlistItem("keep", "addon", "movie", "tt123", "Keep", null, 1))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_list_insert BEFORE INSERT ON custom_lists BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        val result = repo.importBackupJson("""{"format":"plavio-backup","version":2,"settings":{},"watchlist":[],"customLists":[{"id":"new","name":"New","createdAt":1}],"customListItems":[]}""")
        assertTrue(result.isFailure)
        assertEquals("keep", db.watchlistDao().getAll().single().key)
        assertEquals("old", db.customListDao().getLists().single().id)
        assertFalse(settings.autoplay)
    }
    @Test fun legacyCatalogCleanupMeasuresUtf8BytesRatherThanCharacterCount() = fixture { db, _, _ ->
        db.catalogCacheDao().upsertAll(listOf(CatalogCache("large", "addon", "Addon", "https://example.test",
            "movie", "catalog", "Catalog", "😀".repeat(400), 1)))
        assertEquals(1, db.catalogCacheDao().pruneOversized(1000))
        assertTrue(db.catalogCacheDao().getAll().isEmpty())
    }

}
