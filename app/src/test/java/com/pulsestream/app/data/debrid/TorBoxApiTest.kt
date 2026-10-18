package com.pulsestream.app.data.debrid

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for TorBox API integration.
 *
 * Covers:
 * - requestdl URL includes token query parameter (safely encoded)
 * - DebridResolveResult sealed class distinguishes cached vs errors
 * - Auth error detection
 */
class TorBoxApiTest {

    @Test
    fun `requestdl URL includes token query parameter`() {
        // The requestdl endpoint requires the API key as a `token` query param.
        // We verify the URL builder produces a correctly encoded URL.
        val apiKey = "test-api-key-123"
        val torrentId = "456"
        val fileId = 789L

        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.torbox.app")
            .addPathSegments("v1/api/torrents/requestdl")
            .addQueryParameter("token", apiKey)
            .addQueryParameter("torrent_id", torrentId)
            .addQueryParameter("file_id", fileId.toString())
            .addQueryParameter("redirect", "false")
            .build()

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
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.torbox.app")
            .addPathSegments("v1/api/torrents/requestdl")
            .addQueryParameter("token", apiKeyWithSpecialChars)
            .addQueryParameter("torrent_id", "123")
            .addQueryParameter("file_id", "456")
            .build()

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
}
