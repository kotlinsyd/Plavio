package com.pulsestream.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class TorBoxSettingsPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun provideTestOnlyKeystoreBoundary() {
        // Robolectric has no hardware AndroidKeyStore. Keep real AES/GCM and
        // preferences, replacing only key retrieval with a deterministic fake.
        val store = mockk<KeyStore>(relaxed = true)
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        every { store.getKey(any(), any()) } returns key
        mockkStatic(KeyStore::class)
        every { KeyStore.getInstance("AndroidKeyStore") } returns store
    }

    @After
    fun releaseTestOnlyKeystoreBoundary() {
        unmockkStatic(KeyStore::class)
    }

    @Before
    @After
    fun clearSettingsPreferences() {
        context.getSharedPreferences("pulse_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("pulse_secret_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `TorBox key is trimmed encrypted and available after SettingsStore recreation`() {
        val rawFakeKey = "  fixture-torbox-key-not-a-credential  "
        val expectedKey = "fixture-torbox-key-not-a-credential"
        val settings = SettingsStore(context)

        settings.torboxApiKey = rawFakeKey

        assertEquals(expectedKey, settings.torboxApiKey)
        val secretPrefs = context.getSharedPreferences("pulse_secret_settings", Context.MODE_PRIVATE)
        val storedCiphertext = secretPrefs.getString("torbox_api_key", null)
        assertNotNull("TorBox key should be written to encrypted preferences", storedCiphertext)
        assertNotEquals("secret must not be stored as plaintext", expectedKey, storedCiphertext)
        assertFalse("stored secret must not expose the fake key", storedCiphertext!!.contains(expectedKey))
        assertFalse(
            "legacy/general settings preferences must not contain the TorBox key",
            context.getSharedPreferences("pulse_settings", Context.MODE_PRIVATE).all
                .containsKey("torbox_api_key")
        )

        // A new store instance reads the same encrypted value back from persisted preferences.
        assertEquals(expectedKey, SettingsStore(context).torboxApiKey)
    }

    @Test
    fun `blank or null TorBox key removes the encrypted value`() {
        val secretPrefs = context.getSharedPreferences("pulse_secret_settings", Context.MODE_PRIVATE)
        val settings = SettingsStore(context)

        settings.torboxApiKey = "fixture-removal-key-not-a-credential"
        assertNotNull(secretPrefs.getString("torbox_api_key", null))

        settings.torboxApiKey = "   "
        assertNull(settings.torboxApiKey)
        assertFalse(secretPrefs.contains("torbox_api_key"))
        assertFalse(
            context.getSharedPreferences("pulse_settings", Context.MODE_PRIVATE).all
                .containsKey("torbox_api_key")
        )

        settings.torboxApiKey = "fixture-removal-key-not-a-credential"
        settings.torboxApiKey = null
        assertNull(SettingsStore(context).torboxApiKey)
        assertFalse(secretPrefs.contains("torbox_api_key"))
    }
}
