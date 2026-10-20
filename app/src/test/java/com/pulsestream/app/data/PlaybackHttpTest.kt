package com.pulsestream.app.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class PlaybackHttpTest {
    @Test fun credentialScopeIncludesSchemeHostAndPort() {
        val origin = "https://media.example/video".toHttpUrl()
        assertTrue(PlaybackHttp.sameOrigin(origin, "https://media.example/segment".toHttpUrl()))
        assertFalse(PlaybackHttp.sameOrigin(origin, "https://cdn.example/segment".toHttpUrl()))
        assertFalse(PlaybackHttp.sameOrigin(origin, "http://media.example/segment".toHttpUrl()))
        assertFalse(PlaybackHttp.sameOrigin(origin, "https://media.example:8443/segment".toHttpUrl()))
        assertFalse(PlaybackHttp.sameOrigin(null, origin))
    }
    @Test fun crossOriginStripsAllAddonCredentialsButKeepsRange() {
        val original = "https://media.example/video".toHttpUrl()
        val request = okhttp3.Request.Builder().url("https://cdn.example/segment")
            .header("Authorization", "secret").header("Cookie", "session=secret")
            .header("X-Custom-Key", "secret").header("Referer", "https://private.example")
            .header("Range", "bytes=10-").header("User-Agent", "test").build()
        val scoped = PlaybackHttp.scopedRequest(request, original)
        assertNull(scoped.header("Authorization"))
        assertNull(scoped.header("Cookie"))
        assertNull(scoped.header("X-Custom-Key"))
        assertNull(scoped.header("Referer"))
        assertEquals("bytes=10-", scoped.header("Range"))
        assertEquals("test", scoped.header("User-Agent"))
    }

}
