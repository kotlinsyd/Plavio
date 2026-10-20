package com.pulsestream.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.pulsestream.app.data.db.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReviewMigrationChainTest {
    private val migrations = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)

    private fun verifyUpgrade(from: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "review-upgrade-$from-${UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE addons (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, version TEXT NOT NULL, description TEXT NOT NULL, baseUrl TEXT NOT NULL, types TEXT NOT NULL, addedAt INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE playback (`key` TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, poster TEXT, videoId TEXT NOT NULL, streamUrl TEXT NOT NULL, positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        val legacy = helper.writableDatabase
        legacy.execSQL("INSERT INTO addons (id,name,version,description,baseUrl,types,addedAt) VALUES ('mine','Configured','1','','https://example.test/configured/manifest.json','movie',1)")
        legacy.execSQL("INSERT INTO playback (`key`,title,videoId,streamUrl,positionMs,durationMs,updatedAt) VALUES ('saved','Title','tt123','https://example.test/video.mp4',4567,9000,1)")
        migrations.filter { it.endVersion <= from }.forEach { it.migrate(legacy) }
        if (from >= 3) legacy.execSQL("INSERT INTO watchlist (`key`,addonId,type,metaId,name,addedAt) VALUES ('watch','mine','movie','tt123','Saved title',1)")
        if (from >= 9) {
            legacy.execSQL("INSERT INTO custom_lists (id,name,createdAt) VALUES ('list','My list',1)")
            legacy.execSQL("INSERT INTO custom_list_items (listId,itemKey,addonId,type,metaId,name,addedAt) VALUES ('list','item','mine','movie','tt123','Saved title',1)")
        }
        legacy.version = from
        helper.close()
        val current = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*migrations).allowMainThreadQueries().build()
        try {
            val sql = current.openHelper.writableDatabase // Includes Room's complete schema validation.
            assertEquals(11, sql.version)
            sql.query("SELECT baseUrl FROM addons WHERE id='mine'").use {
                assertTrue(it.moveToFirst()); assertEquals("https://example.test/configured/manifest.json", it.getString(0))
            }
            sql.query("SELECT positionMs FROM playback WHERE `key`='saved'").use {
                assertTrue(it.moveToFirst()); assertEquals(4567L, it.getLong(0))
            }
            if (from >= 3) sql.query("SELECT COUNT(*) FROM watchlist").use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
            if (from >= 9) {
                sql.query("SELECT name FROM custom_lists WHERE id='list'").use {
                    assertTrue(it.moveToFirst()); assertEquals("My list", it.getString(0))
                }
                sql.query("SELECT COUNT(*) FROM custom_list_items WHERE listId='list'").use {
                    assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
                }
            }
        } finally { current.close(); context.deleteDatabase(name) }
    }

    @Test fun upgrade1() = verifyUpgrade(1)
    @Test fun upgrade2() = verifyUpgrade(2)
    @Test fun upgrade3() = verifyUpgrade(3)
    @Test fun upgrade4() = verifyUpgrade(4)
    @Test fun upgrade5() = verifyUpgrade(5)
    @Test fun upgrade6() = verifyUpgrade(6)
    @Test fun upgrade7() = verifyUpgrade(7)
    @Test fun upgrade8() = verifyUpgrade(8)
    @Test fun upgrade9PreservesLists() = verifyUpgrade(9)
    @Test fun upgrade10PreservesLists() = verifyUpgrade(10)
}
