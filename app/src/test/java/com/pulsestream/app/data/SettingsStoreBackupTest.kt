package com.pulsestream.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SettingsStoreBackupTest {
    @Test
    fun `explicit empty settings restore portable defaults without touching local-only state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("pulse_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val settings = SettingsStore(context)
        settings.autoplay = false
        settings.autoPlayNext = false
        settings.m3uPlaylists = listOf("https://channels.example/list.m3u")

        // `settings: {}` is parsed as an empty, present map and must replace
        // portable preferences; omitted settings are handled separately by the repository.
        settings.importAllPrefs(emptyMap())

        assertTrue(settings.autoplay)
        assertTrue(settings.autoPlayNext)
        assertTrue(settings.m3uPlaylists.isEmpty())
        assertEquals(PlayerChoice.BUILT_IN.name, settings.preferredPlayer)
    }
}
