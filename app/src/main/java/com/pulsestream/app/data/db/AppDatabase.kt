package com.pulsestream.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        InstalledAddon::class,
        PlaybackState::class,
        CatalogCache::class,
        WatchlistItem::class,
        SubtitleCache::class,
        DownloadItem::class,
        CustomList::class,
        CustomListItem::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun catalogCacheDao(): CatalogCacheDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun subtitleCacheDao(): SubtitleCacheDao
    abstract fun downloadDao(): DownloadDao
    abstract fun customListDao(): CustomListDao
}

/**
 * v1 → v2: add the `resources` column to the addons table so each addon
 * remembers which Stremio resources it provides (catalog / meta / stream).
 * v0.1.0 only accepted full addons, so the default is correct for existing rows.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE addons ADD COLUMN resources TEXT NOT NULL DEFAULT 'catalog,meta,stream'"
        )
    }
}

/**
 * v2 → v3: new tables for catalog caching, the watchlist, and subtitle
 * search caching; a `preinstalled` flag on addons; IMDb/season/episode
 * columns on playback rows so Continue Watching keeps full context.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS catalog_cache (
                `key` TEXT NOT NULL PRIMARY KEY,
                addonId TEXT NOT NULL,
                addonName TEXT NOT NULL,
                baseUrl TEXT NOT NULL,
                type TEXT NOT NULL,
                catalogId TEXT NOT NULL,
                catalogName TEXT NOT NULL,
                itemsJson TEXT NOT NULL,
                fetchedAt INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS watchlist (
                `key` TEXT NOT NULL PRIMARY KEY,
                addonId TEXT NOT NULL,
                type TEXT NOT NULL,
                metaId TEXT NOT NULL,
                name TEXT NOT NULL,
                poster TEXT,
                addedAt INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS subtitle_cache (
                `key` TEXT NOT NULL PRIMARY KEY,
                itemsJson TEXT NOT NULL,
                fetchedAt INTEGER NOT NULL
            )"""
        )
        db.execSQL("ALTER TABLE addons ADD COLUMN preinstalled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playback ADD COLUMN imdbId TEXT")
        db.execSQL("ALTER TABLE playback ADD COLUMN season INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playback ADD COLUMN episode INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v3 → v4: per-addon `enabled` flag (user on/off switch) and `configurable`
 * flag (manifest behaviorHints — the addon ships a web configure page).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE addons ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE addons ADD COLUMN configurable INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v4 → v5: per-addon `showOnHome` flag — controls whether the addon's
 * catalogs appear on the Home screen (Settings > Home Catalogs).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE addons ADD COLUMN showOnHome INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v5 → v6: `logoUrl` on addons — the manifest logo, shown as the addon
 * icon in the redesigned Addons tab.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE addons ADD COLUMN logoUrl TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v6 → v7: `sortOrder` (user-rearranged addon order) and `starred`
 * (priority addon flag) on addons.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE addons ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE addons ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v7 → v8: new `downloads` table for the offline downloader.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS downloads (
                id TEXT NOT NULL PRIMARY KEY,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                fileName TEXT NOT NULL,
                filePath TEXT NOT NULL,
                totalBytes INTEGER NOT NULL DEFAULT -1,
                downloadedBytes INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'QUEUED',
                error TEXT,
                headersJson TEXT NOT NULL DEFAULT '{}',
                createdAt INTEGER NOT NULL,
                completedAt INTEGER
            )"""
        )
    }
}

/**
 * v8 → v9: `custom_lists` + `custom_list_items` tables for user-created
 * named lists. Composite primary key (listId, itemKey) prevents duplicates.
 * NOTE: register in PulseApp.buildDatabase()'s addMigrations chain —
 * that file is intentionally untouched by the custom-lists change.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Drop existing tables first to ensure clean schema (handles cases where
        // test builds created tables with slightly different schemas)
        db.execSQL("DROP TABLE IF EXISTS custom_list_items")
        db.execSQL("DROP TABLE IF EXISTS custom_lists")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS custom_lists (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS custom_list_items (
                listId TEXT NOT NULL,
                itemKey TEXT NOT NULL,
                addonId TEXT NOT NULL,
                type TEXT NOT NULL,
                metaId TEXT NOT NULL,
                name TEXT NOT NULL,
                poster TEXT,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY (listId, itemKey)
            )"""
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_custom_list_items_listId ON custom_list_items (listId)"
        )
    }
}
