package com.pulsestream.app.ui.screens

import com.pulsestream.app.data.CatalogRow
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.StreamRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveLoadingTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<StreamRepository>()
    private val settings = mockk<SettingsStore>()
    private val cached = CatalogRow("fixture", "Fixture", "https://fixture.invalid", "Channels", "tv", "live",
        listOf(MetaPreview("channel", "tv", "Fixture channel")))

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        every { repository.settings } returns settings
        every { settings.m3uPlaylists } returns emptyList()
        coEvery { repository.loadTvRowsCached() } returns listOf(cached)
    }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `saved channels render while network refresh is pending and duplicate loads coalesce`() = runTest {
        val fresh = CompletableDeferred<List<CatalogRow>>()
        var m3uStarted = false
        coEvery { repository.loadTvRows() } coAnswers { fresh.await() }
        coEvery { repository.loadM3uRows() } coAnswers {
            m3uStarted = true
            StreamRepository.M3uLoadResult(emptyList(), emptyList())
        }
        val vm = LiveViewModel(repository)
        vm.load() // Mirrors init + ON_RESUME.
        runCurrent()
        assertEquals(listOf(cached), (vm.uiState as LiveUiState.Content).rows)
        assertTrue("M3U must start without waiting for addon catalogs", m3uStarted)
        coVerify(exactly = 1) { repository.loadTvRows() }
        val updated = cached.copy(catalogName = "Updated")
        fresh.complete(listOf(updated))
        runCurrent()
        assertEquals(listOf(updated), (vm.uiState as LiveUiState.Content).rows)
    }

    @Test fun `refresh failure preserves saved channels and does not expose exception details`() = runTest {
        coEvery { repository.loadTvRows() } throws IllegalStateException("fixture-sensitive-detail")
        coEvery { repository.loadM3uRows() } returns StreamRepository.M3uLoadResult(emptyList(), emptyList())
        val vm = LiveViewModel(repository)
        runCurrent()
        val state = vm.uiState as LiveUiState.Content
        assertEquals(listOf(cached), state.rows)
        assertTrue(state.m3uErrors.single().contains("showing saved channels"))
        assertFalse(state.m3uErrors.joinToString().contains("fixture-sensitive-detail"))
    }

    @Test fun `successful empty refresh clears stale cached channels`() = runTest {
        coEvery { repository.loadTvRows() } returns emptyList()
        coEvery { repository.loadM3uRows() } returns StreamRepository.M3uLoadResult(emptyList(), emptyList())
        val vm = LiveViewModel(repository)
        runCurrent()
        assertEquals(LiveUiState.Empty, vm.uiState)
    }
}
