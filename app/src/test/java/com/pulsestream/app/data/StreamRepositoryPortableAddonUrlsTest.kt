package com.pulsestream.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.InstalledAddon
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StreamRepositoryPortableAddonUrlsTest {
    private fun withRepository(test: suspend (AppDatabase, StreamRepository) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = StreamRepository(db, StremioApi(), SettingsStore(context))
        try { test(db, repo) } finally { db.close() }
    }

    private fun addon(id: String, url: String, enabled: Boolean = true) = InstalledAddon(
        id = id, name = id, version = "1", description = "fixture", baseUrl = url,
        types = "movie", resources = "stream", enabled = enabled, addedAt = 1L
    )

    @Test fun publicRootAddonUrlIsKeptInPlainBackupWithoutLockedMarker() = withRepository { db, repo ->
        val rootUrl = "https://public-addon.test/"
        db.addonDao().upsert(addon("public.addon", rootUrl))

        val backup = JSONObject(repo.exportBackupJson().getOrThrow())
        val row = backup.getJSONArray("addons").getJSONObject(0)
        assertEquals(rootUrl, row.getString("baseUrl"))
        assertFalse(row.optBoolean("encryptedUrlRequired", false))
        repo.importBackupJson(backup.toString()).getOrThrow()
        assertEquals(rootUrl, db.addonDao().getAll().single().baseUrl)
        assertTrue(db.addonDao().getAll().single().enabled)
    }

    @Test fun conflictingLocalUrlRejectsEntireRestoreBeforeAnyWrites() = withRepository { db, repo ->
        val placeholder = "https://example.test/__plavio_encrypted_config_required__"
        val local = "https://local.test/config"
        db.addonDao().upsert(addon("placeholder.addon", placeholder, enabled = false))
        db.addonDao().upsert(addon("local.addon", local))

        val result = repo.restorePortableAddonUrls(linkedMapOf(
            "placeholder.addon" to PortableAddonUrlConfig("https://restored.test/private", true),
            "local.addon" to PortableAddonUrlConfig("https://other.test/config", false)
        ))

        assertTrue(result.isFailure)
        val rows = db.addonDao().getAll().associateBy { it.id }
        assertEquals(placeholder, rows.getValue("placeholder.addon").baseUrl)
        assertFalse(rows.getValue("placeholder.addon").enabled)
        assertEquals(local, rows.getValue("local.addon").baseUrl)
        assertTrue(rows.getValue("local.addon").enabled)
    }

    @Test fun malformedAndUnknownEntriesAreRejectedAtomically() = withRepository { db, repo ->
        val placeholder = "https://example.test/__plavio_encrypted_config_required__"
        db.addonDao().upsert(addon("installed.addon", placeholder, enabled = false))

        val malformed = repo.restorePortableAddonUrls(linkedMapOf(
            "installed.addon" to PortableAddonUrlConfig("https://restored.test/config", true),
            "also-installed" to PortableAddonUrlConfig("file:///not-http", true)
        ))
        assertTrue(malformed.isFailure)
        assertEquals(placeholder, db.addonDao().getAll().single().baseUrl)
        assertFalse(db.addonDao().getAll().single().enabled)

        val unknown = repo.restorePortableAddonUrls(linkedMapOf(
            "installed.addon" to PortableAddonUrlConfig("https://restored.test/config", true),
            "missing.addon" to PortableAddonUrlConfig("https://missing.test/config", true)
        ))
        assertTrue(unknown.isFailure)
        assertTrue(unknown.exceptionOrNull()?.message.orEmpty().contains("Restore the profile first"))
        assertEquals(placeholder, db.addonDao().getAll().single().baseUrl)
        assertFalse(db.addonDao().getAll().single().enabled)
    }

    @Test fun rollbackCapabilityCanRestoreKnownPriorLocalConfiguration() = withRepository { db, repo ->
        val current = "https://current.test/addon"
        val previous = "https://previous-user:previous-pass@previous.test/path?private=prior"
        db.addonDao().upsert(addon("fixture.addon", current, enabled = true))

        val ordinary = repo.restorePortableAddonUrls(mapOf(
            "fixture.addon" to PortableAddonUrlConfig(previous, false)
        ))
        assertTrue(ordinary.isFailure)
        assertEquals(current, db.addonDao().getAll().single().baseUrl)
        assertTrue(db.addonDao().getAll().single().enabled)

        assertEquals(1, repo.restorePortableAddonUrls(mapOf(
            "fixture.addon" to PortableAddonUrlConfig(previous, false)
        ), allowOverwriteForRollback = true).getOrThrow())
        assertEquals(previous, db.addonDao().getAll().single().baseUrl)
        assertFalse(db.addonDao().getAll().single().enabled)

        val rollbackSnapshot = repo.exportPortableAddonUrls(includeLockedPlaceholdersForRollback = true)
        assertEquals(previous, rollbackSnapshot.getValue("fixture.addon").baseUrl)
    }
}
