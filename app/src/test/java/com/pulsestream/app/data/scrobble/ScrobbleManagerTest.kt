package com.pulsestream.app.data.scrobble

import com.pulsestream.app.data.PlaybackRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrobbleManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var ioScope: CoroutineScope
    private lateinit var provider: ScrobbleProvider
    private lateinit var manager: ScrobbleManager

    private fun req(url: String = "http://example.com/stream.m3u8") = PlaybackRequest(
        streamUrl = url,
        title = "Test",
        poster = null,
        videoId = "v1"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        ioScope = CoroutineScope(testDispatcher)
        provider = mockk(relaxed = true) {
            every { name } returns "TestProvider"
            every { isEnabled } returns true
        }
        manager = ScrobbleManager(listOf(provider), ioScope)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tick with zero duration does nothing`() = runTest(testDispatcher) {
        manager.tick(req(), posMs = 1000, durMs = 0, playing = true, ended = false)
        advanceUntilIdle()
        coVerify(exactly = 0) { provider.scrobbleStart(any(), any(), any()) }
    }

    @Test
    fun `tick with no enabled providers does nothing`() = runTest(testDispatcher) {
        every { provider.isEnabled } returns false
        manager.tick(req(), posMs = 1000, durMs = 60000, playing = true, ended = false)
        advanceUntilIdle()
        coVerify(exactly = 0) { provider.scrobbleStart(any(), any(), any()) }
    }

    @Test
    fun `first tick starts scrobble`() = runTest(testDispatcher) {
        val r = req()
        manager.tick(r, posMs = 1000, durMs = 60000, playing = true, ended = false)
        advanceUntilIdle()
        coVerify(exactly = 1) { provider.scrobbleStart(r, 1000, 60000) }
    }

    @Test
    fun `pause after playing sends pause`() = runTest(testDispatcher) {
        val r = req()
        manager.tick(r, posMs = 1000, durMs = 60000, playing = true, ended = false)
        advanceUntilIdle()
        manager.tick(r, posMs = 5000, durMs = 60000, playing = false, ended = false)
        advanceUntilIdle()
        coVerify(exactly = 1) { provider.scrobblePause(r, 5000, 60000) }
    }

    @Test
    fun `resume after pause sends start`() = runTest(testDispatcher) {
        val r = req()
        manager.tick(r, posMs = 1000, durMs = 60000, playing = true, ended = false)
        advanceUntilIdle()
        manager.tick(r, posMs = 5000, durMs = 60000, playing = false, ended = false)
        advanceUntilIdle()
        manager.tick(r, posMs = 5000, durMs = 60000, playing = true, ended = false)
        advanceUntilIdle()
        coVerify(exactly = 2) { provider.scrobbleStart(eq(r), any(), any()) }
    }

    @Test
    fun `stream switch stops old and starts new`() = runTest(testDispatcher) {
        val r1 = req("http://example.com/a.m3u8")
        val r2 = req("http://example.com/b.m3u8")
        manager.tick(r1, posMs = 1000, durMs = 60000, playing = true, ended = false)
        advanceUntilIdle()
        manager.tick(r2, posMs = 500, durMs = 90000, playing = true, ended = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { provider.scrobbleStop(r1, 1000, 60000) }
        coVerify(exactly = 1) { provider.scrobbleStart(r2, 500, 90000) }
    }

    @Test
    fun `ended tick sends stop at 100 percent`() = runTest(testDispatcher) {
        val r = req()
        manager.tick(r, posMs = 1000, durMs = 60000, playing = true, ended = false)
        advanceUntilIdle()
        manager.tick(r, posMs = 59000, durMs = 60000, playing = true, ended = true)
        advanceUntilIdle()

        coVerify { provider.scrobbleStop(r, 60000, 60000) }
    }

    @Test
    fun `shutdown sends final stop`() = runTest(testDispatcher) {
        val r = req()
        manager.tick(r, posMs = 10000, durMs = 60000, playing = true, ended = false)
        advanceUntilIdle()
        manager.shutdown()
        advanceUntilIdle()

        coVerify(exactly = 1) { provider.scrobbleStop(r, 10000, 60000) }
    }

    @Test
    fun `shutdown with no active stream does nothing`() = runTest(testDispatcher) {
        manager.shutdown()
        advanceUntilIdle()
        coVerify(exactly = 0) { provider.scrobbleStop(any(), any(), any()) }
    }

    @Test
    fun `throwing provider does not break other providers`() = runTest(testDispatcher) {
        val bad: ScrobbleProvider = mockk {
            every { name } returns "Bad"
            every { isEnabled } returns true
            coEvery { scrobbleStart(any(), any(), any()) } throws RuntimeException("boom")
        }
        val good: ScrobbleProvider = mockk(relaxed = true) {
            every { name } returns "Good"
            every { isEnabled } returns true
        }
        val m = ScrobbleManager(listOf(bad, good), ioScope)
        val r = req()

        m.tick(r, posMs = 1000, durMs = 60000, playing = true, ended = false)
        advanceUntilIdle()

        // Good provider still got the event despite bad provider throwing
        coVerify(exactly = 1) { good.scrobbleStart(r, 1000, 60000) }
    }

    @Test
    fun `disabled provider receives no events`() = runTest(testDispatcher) {
        val disabled: ScrobbleProvider = mockk(relaxed = true) {
            every { name } returns "Disabled"
            every { isEnabled } returns false
        }
        val m = ScrobbleManager(listOf(disabled), ioScope)

        m.tick(req(), posMs = 1000, durMs = 60000, playing = true, ended = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { disabled.scrobbleStart(any(), any(), any()) }
    }
}
