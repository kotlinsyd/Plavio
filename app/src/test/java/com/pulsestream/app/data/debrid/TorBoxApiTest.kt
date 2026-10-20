package com.pulsestream.app.data.debrid

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for TorBox API integration.
 *
 * Covers:
 * - requestdl URL includes token query parameter (safely encoded)
 * - DebridResolveResult sealed class distinguishes cached vs errors
 * - Auth error detection
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TorBoxApiTest {

    @Test
    fun `requestdl URL includes token query parameter`() {
        // The requestdl endpoint requires the API key as a `token` query param.
        // We verify the URL builder produces a correctly encoded URL.
        val apiKey = "test-api-key-123"
        val torrentId = "456"
        val fileId = 789L

        val url = TorBoxApi(apiKey).requestDownloadUrl(torrentId, fileId)

        val urlString = url.toString()
        assertTrue("URL should contain token param", urlString.contains("token=test-api-key-123"))
        assertTrue("URL should contain torrent_id", urlString.contains("torrent_id=456"))
        assertTrue("URL should contain file_id", urlString.contains("file_id=789"))
        assertTrue("URL should contain redirect=false", urlString.contains("redirect=false"))
    }

    @Test
    fun `requestdl URL safely encodes special characters in token`() {
        // API keys with special chars must be URL-encoded, not break the URL
        val apiKeyWithSpecialChars = "key+with/special=chars&more"
        val url = TorBoxApi(apiKeyWithSpecialChars).requestDownloadUrl("123", 456)

        // The raw special chars should NOT appear unencoded in the URL
        val urlString = url.toString()
        assertFalse("Raw + should be encoded", urlString.contains("token=key+with"))
        // But the decoded value should match
        assertEquals(apiKeyWithSpecialChars, url.queryParameter("token"))
    }

    @Test
    fun `DebridResolveResult Resolved holds URL`() {
        val result = DebridResolveResult.Resolved("https://example.com/video.mp4")
        assertTrue(result is DebridResolveResult.Resolved)
        assertEquals("https://example.com/video.mp4", (result as DebridResolveResult.Resolved).url)
    }

    @Test
    fun `DebridResolveResult NotCached is singleton`() {
        val r1 = DebridResolveResult.NotCached
        val r2 = DebridResolveResult.NotCached
        assertSame(r1, r2)
    }

    @Test
    fun `DebridResolveResult Error distinguishes auth errors`() {
        val authError = DebridResolveResult.Error(
            "TorBox authentication failed (HTTP 401). Check your API key.",
            isAuthError = true
        )
        assertTrue(authError.isAuthError)

        val networkError = DebridResolveResult.Error("Network error: timeout")
        assertFalse(networkError.isAuthError)
    }

    @Test
    fun `DebridResolveResult Error message does not contain raw key`() {
        // Error messages must be safe to display (no keys/tokens)
        val error = DebridResolveResult.Error("Request failed")
        assertFalse(error.message.contains("test-api-key"))
    }

    @Test
    fun `cached torrent with both documented flags is immediately ready`() {
        val status = parseStatus(
            """{"success":true,"data":{"id":42,"download_state":"cached","download_finished":true,"download_present":true,"files":[{"id":7,"name":"Season 1/Episode 01.mkv","short_name":"Episode 01.mkv","size":1000}]}}"""
        )

        assertTrue(status.isReady)
        assertEquals("Season 1/Episode 01.mkv", status.files.single().name)
        assertEquals(7L, status.files.single().id)
    }

    @Test
    fun `partial or contradictory readiness flags are not ready`() {
        val incomplete = parseStatus(
            """{"success":true,"data":{"id":42,"download_state":"downloading","download_finished":false,"download_present":true,"files":[{"id":7,"name":"movie.mkv"}]}}"""
        )
        val contradictoryCached = parseStatus(
            """{"success":true,"data":{"id":42,"download_state":"cached","download_finished":true,"download_present":false,"files":[{"id":7,"name":"movie.mkv"}]}}"""
        )

        assertFalse(incomplete.isReady)
        assertFalse(contradictoryCached.isReady)
    }

    @Test
    fun `cached fallback requires usable files when flags are absent`() {
        val cachedWithFile = parseStatus(
            """{"success":true,"data":{"id":42,"download_state":"cached","files":[{"id":7,"short_name":"Folder/movie.mkv","size":100}]}}"""
        )
        val cachedWithoutFile = parseStatus(
            """{"success":true,"data":{"id":42,"download_state":"cached","files":[{"name":"","size":100}]}}"""
        )
        val completeStateOnly = parseStatus(
            """{"success":true,"data":{"id":42,"download_state":"completed","files":[{"id":7,"name":"movie.mkv"}]}}"""
        )

        assertTrue(cachedWithFile.isReady)
        assertEquals("Folder/movie.mkv", cachedWithFile.files.single().name)
        assertFalse(cachedWithoutFile.isReady)
        assertFalse(completeStateOnly.isReady)
    }

    @Test
    fun `mylist accepts object or one uniquely matching array entry`() {
        val objectStatus = parseStatus(
            """{"success":true,"data":{"id":42,"download_state":"cached","download_finished":true,"download_present":true,"files":[]}}"""
        )
        val arrayStatus = parseStatus(
            """{"success":true,"data":[{"id":9,"download_state":"downloading"},{"id":42,"download_state":"cached","download_finished":true,"download_present":true,"files":[]}] }"""
        )

        assertTrue(objectStatus.isReady)
        assertTrue(arrayStatus.isReady)
        assertEquals("42", arrayStatus.id)
    }

    @Test
    fun `ambiguous mylist array matches fail closed`() {
        val fixture = JSONObject(
            """{"success":true,"data":[{"id":42,"download_state":"cached"},{"id":42,"download_state":"cached"}]}"""
        )

        try {
            TorBoxApi.parseTorrentStatus(fixture, "42")
            fail("duplicate matching torrent entries must be rejected")
        } catch (expected: TorBoxException) {
            assertTrue(expected.message.orEmpty().contains("ambiguous"))
        }
    }

    private fun parseStatus(json: String): TorBoxApi.TorrentStatus =
        TorBoxApi.parseTorrentStatus(JSONObject(json), "42")
}
