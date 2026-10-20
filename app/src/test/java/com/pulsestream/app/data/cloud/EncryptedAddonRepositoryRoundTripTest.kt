package com.pulsestream.app.data.cloud

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pulsestream.app.data.PortableAddonUrlConfig
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.StremioApi
import com.pulsestream.app.data.StreamRepository
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
class EncryptedAddonRepositoryRoundTripTest {
    private val rawUrl = "https://url-user:url-pass@fixture.invalid/embedded-path-key?api_key=query-key"
    private val passphrase = "portable fixture passphrase".toCharArray()
    private val account = "fixture-account"
    private val profile = "fixture-profile"

    @Test fun ordinaryRestoreStaysLockedUntilAuthenticatedPrivateUrlRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = StreamRepository(db, StremioApi(), SettingsStore(context))
        try {
            db.addonDao().upsert(InstalledAddon(id = "fixture.addon", name = "Fixture addon", version = "1", description = "Fixture",
                baseUrl = rawUrl, types = "movie,series", resources = "catalog,meta,stream", configurable = true, enabled = true, addedAt = 1L))
            val ordinary = repository.exportBackupJson().getOrThrow()
            listOf("url-user", "url-pass", "embedded-path-key", "query-key").forEach { assertFalse("Ordinary snapshot leaked a URL credential", ordinary.contains(it)) }
            val privateConfigs = repository.exportPortableAddonUrls()
            assertEquals(rawUrl, privateConfigs.getValue("fixture.addon").baseUrl)
            val encrypted = PortableCredentialEnvelope.encrypt(payload(privateConfigs), passphrase, account, profile)
            assertFalse(encrypted.contains("embedded-path-key"))
            assertFalse(encrypted.contains("query-key"))

            repository.importBackupJson(ordinary).getOrThrow()
            val locked = db.addonDao().getAll().single()
            assertFalse(locked.enabled)
            assertNotEquals(rawUrl, locked.baseUrl)
            assertTrue(repository.exportPortableAddonUrls().isEmpty())

            val decrypted = PortableCredentialEnvelope.decrypt(encrypted, passphrase, account, profile)
            val decoded = decodeConfigs(decrypted)
            repository.restorePortableAddonUrls(decoded).getOrThrow()
            val restored = db.addonDao().getAll().single()
            assertEquals(rawUrl, restored.baseUrl)
            assertTrue(restored.enabled)
        } finally { db.close() }
    }

    @Test fun lockedPlaceholderCannotEraseAnExistingEncryptedConfiguredUrl() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = StreamRepository(db, StremioApi(), SettingsStore(context))
        try {
            db.addonDao().upsert(InstalledAddon(id = "fixture.addon", name = "Fixture addon", version = "1", description = "Fixture",
                baseUrl = rawUrl, types = "movie", resources = "stream", enabled = true, addedAt = 1L))
            val remote = PortableCredentialEnvelope.encrypt(payload(repository.exportPortableAddonUrls()), passphrase, account, profile)
            repository.importBackupJson(repository.exportBackupJson().getOrThrow()).getOrThrow()
            val localSafeConfigs = repository.exportPortableAddonUrls()
            assertTrue(localSafeConfigs.isEmpty())
            val merged = PortableCredentialEnvelope.mergeAndEncrypt(payload(localSafeConfigs), remote, passphrase, account, profile)
            assertEquals(rawUrl, decodeConfigs(PortableCredentialEnvelope.decrypt(merged, passphrase, account, profile)).getValue("fixture.addon").baseUrl)
        } finally { db.close() }
    }

    private fun payload(configs: Map<String, PortableAddonUrlConfig>): String {
        val addons = JSONObject()
        configs.forEach { (id, config) -> addons.put(id, JSONObject().put("base_url", config.baseUrl).put("enabled", config.enabled)) }
        return JSONObject().put("format", "plavio-integration-credentials").put("version", 1)
            .put("credentials", JSONObject()).put("addon_urls", addons).toString()
    }

    private fun decodeConfigs(payload: String): Map<String, PortableAddonUrlConfig> {
        val urls = JSONObject(payload).getJSONObject("addon_urls")
        return urls.keys().asSequence().associateWith { id ->
            val value = urls.getJSONObject(id)
            PortableAddonUrlConfig(value.getString("base_url"), value.getBoolean("enabled"))
        }
    }
}
