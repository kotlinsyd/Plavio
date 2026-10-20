package com.pulsestream.app.data

import android.app.Application
import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.InstalledAddon
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ForegroundStreamDiscoveryTest {
    private fun addon(id: String) = InstalledAddon(
        id, id, "1", "Fixture only", "https://$id.invalid", "tv",
        resources = "stream", addedAt = 0L
    )

    private fun repository(api: StremioApi, addons: List<InstalledAddon>): StreamRepository {
        val db = mockk<AppDatabase>(relaxed = true)
        val settings = mockk<SettingsStore>(relaxed = true)
        every { settings.torboxApiKey } returns null
        every { settings.realDebridApiKey } returns null
        coEvery { db.addonDao().getAll() } returns addons
        return StreamRepository(db, api, settings)
    }

    @Test fun liveDiscoveryDoesNotWaitForSaturatedBackgroundCatalogGate() = runBlocking {
        val source = addon("live-source")
        val api = mockk<StremioApi>()
        coEvery { api.fetchStreams(source.baseUrl, "tv", "channel:1") } returns
            StreamResult(listOf(StreamItem("https://fixture.invalid/live.m3u8")), emptyList())
        val repository = repository(api, listOf(source))
        // Model four in-flight background catalog requests without real network traffic.
        val field = StreamRepository::class.java.getDeclaredField("addonRequestGate")
        field.isAccessible = true
        val backgroundGate = field.get(repository) as Semaphore
        repeat(4) { backgroundGate.acquire() }
        try {
            val result = withTimeout(3_000) {
                repository.liveStreamResultsFlow(TvChannelRef(source.id, "channel:1", "Fixture", null))
                    .first { it.streams.isNotEmpty() }
            }
            assertEquals("https://fixture.invalid/live.m3u8", result.streams.single().url)
        } finally {
            repeat(4) { backgroundGate.release() }
        }
        coVerify(exactly = 1) { api.fetchStreams(source.baseUrl, "tv", "channel:1") }
    }

    @Test fun selectedChannelAddonGetsARequestSlotBeforeUnrelatedSlowAddons() = runBlocking {
        val preferred = addon("selected-live-source")
        val slow = (1..5).map { addon("slow-$it") }
        val api = mockk<StremioApi>()
        slow.forEach { source ->
            coEvery { api.fetchStreams(source.baseUrl, any(), any()) } coAnswers { awaitCancellation() }
        }
        coEvery { api.fetchStreams(preferred.baseUrl, "customChannel", "channel:2") } returns
            StreamResult(listOf(StreamItem("https://fixture.invalid/selected")), emptyList())
        val repository = repository(api, slow + preferred)
        val result = withTimeout(3_000) {
            repository.liveStreamResultsFlow(TvChannelRef(preferred.id, "channel:2", "Fixture", null,
                type = "customChannel"))
                .first { it.streams.isNotEmpty() }
        }
        assertEquals(preferred.id, result.streams.single().sourceId)
        assertEquals("https://fixture.invalid/selected", result.streams.single().url)
    }
}
