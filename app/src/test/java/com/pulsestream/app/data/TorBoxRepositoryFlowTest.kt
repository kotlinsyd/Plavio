package com.pulsestream.app.data

import android.app.Application
import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.InstalledAddon
import com.pulsestream.app.data.debrid.DebridResolveResult
import com.pulsestream.app.data.debrid.TorBoxApi
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TorBoxRepositoryFlowTest {
    private val hash = "0123456789abcdef0123456789abcdef01234567"
    private val addon = InstalledAddon("fixture.torrentio", "Torrentio fixture", "1", "Local fixture only", "https://fixture.invalid", "movie,series", resources = "stream", addedAt = 0L)
    private lateinit var api: StremioApi
    private lateinit var settings: SettingsStore
    private lateinit var repository: StreamRepository

    @Before fun setup() {
        val db = mockk<AppDatabase>(relaxed = true)
        api = mockk()
        settings = mockk(relaxed = true)
        coEvery { db.addonDao().getAll() } returns listOf(addon)
        every { settings.torboxApiKey } returns "fixture-key-not-real"
        every { settings.realDebridApiKey } returns null
        repository = StreamRepository(db, api, settings)
        mockkConstructor(TorBoxApi::class)
        SourceDiagnostics.clear()
    }

    @After fun cleanup() { unmockkConstructor(TorBoxApi::class) }

    private fun torrent() = StreamItem("infohash:$hash", title = "Fixture episode", infoHash = hash, fileIdx = 2,
        filePath = "Season 1/E03.mkv", headers = mapOf("User-Agent" to "FixturePlayer"))

    @Test fun customLiveResourceTypeAndRequiredHeadersSurviveRepositoryResolution() = runBlocking {
        val fixture = StremioParser.parseStreams("""{"streams":[{"url":"https://fixture.invalid/live","headers":{"User-Agent":"FixturePlayer","Referer":"https://fixture.invalid/","Origin":"https://fixture.invalid"},"behaviorHints":{"mimeType":"application/vnd.apple.mpegurl"}}]}""")
        coEvery { api.fetchStreams(addon.baseUrl, "customChannel", "channel:42") } returns fixture
        val channel = TvChannelRef(addon.id, "channel:42", "Fixture Live", null, type = "customChannel", catalogId = "live-news")
        val stream = repository.liveStreamResultsFlow(channel).toList().last().streams.single()
        assertEquals("FixturePlayer", stream.headers["User-Agent"])
        assertEquals("https://fixture.invalid/", stream.headers["Referer"])
        assertEquals("https://fixture.invalid", stream.headers["Origin"])
        assertEquals("application/vnd.apple.mpegurl", stream.mimeType)
        assertEquals(addon.id, stream.sourceId)
        coVerify(exactly = 1) { api.fetchStreams(addon.baseUrl, "customChannel", "channel:42") }
        coVerify(exactly = 0) { api.fetchStreams(any(), "tv", any()) }
    }

    @Test fun cachedTorrentIsReplacedWithPlayableLinkAndExactEpisodeIdentityIsPreserved() = runBlocking {
        coEvery { api.fetchStreams(addon.baseUrl, "series", "fixture:1:3") } returns StreamResult(listOf(torrent()), emptyList())
        coEvery { anyConstructed<TorBoxApi>().resolveCachedDetailed(hash, 2, "Season 1/E03.mkv") } returns
            DebridResolveResult.Resolved("https://cdn.fixture.invalid/episode3.mkv")
        val emissions = repository.streamResultsFlow("series", "fixture:1:3").toList()
        assertTrue(emissions.any { result -> result.streams.any { it.url.startsWith("infohash:") } })
        val stream = emissions.last().streams.single()
        assertEquals("https://cdn.fixture.invalid/episode3.mkv", stream.url)
        assertEquals(2, stream.fileIdx)
        assertEquals("Season 1/E03.mkv", stream.filePath)
        assertEquals("FixturePlayer", stream.headers["User-Agent"])
        assertNull(stream.infoHash)
        assertTrue(stream.sourceName.orEmpty().contains("TorBox"))
        coVerify(exactly = 1) { anyConstructed<TorBoxApi>().resolveCachedDetailed(hash, 2, "Season 1/E03.mkv") }
    }

    @Test fun uncachedTorrentNeverBecomesAFakePlayableLink() = runBlocking {
        coEvery { api.fetchStreams(any(), any(), any()) } returns StreamResult(listOf(torrent()), emptyList())
        coEvery { anyConstructed<TorBoxApi>().resolveCachedDetailed(any(), any(), any()) } returns DebridResolveResult.NotCached
        val result = repository.streamResultsFlow("series", "fixture:1:3").toList().last()
        assertTrue(result.streams.single().url.startsWith("infohash:"))
        assertTrue(SourceDiagnostics.entries.value.any { it.outcome == "no_results" })
    }

    @Test fun invalidTorboxKeyProducesSafeAuthenticationDiagnostic() = runBlocking {
        coEvery { api.fetchStreams(any(), any(), any()) } returns StreamResult(listOf(torrent()), emptyList())
        coEvery { anyConstructed<TorBoxApi>().resolveCachedDetailed(any(), any(), any()) } returns
            DebridResolveResult.Error("Fixture authentication error", isAuthError = true)
        repository.streamResultsFlow("series", "fixture:1:3").toList()
        assertTrue(SourceDiagnostics.entries.value.any { it.outcome == "authentication_needed" })
        val export = SourceDiagnostics.export()
        assertFalse(export.contains("fixture-key-not-real"))
        assertFalse(export.contains(hash))
        assertFalse(export.contains("Fixture episode"))
    }
}
