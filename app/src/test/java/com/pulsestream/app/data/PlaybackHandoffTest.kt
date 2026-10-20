@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsestream.app.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaybackHandoffTest {
    private data class SeenRequest(val method: String, val path: String, val headers: Map<String, String>) {
        fun header(name: String): String? = headers[name.lowercase()]
    }

    private data class Reply(
        val status: Int = 200,
        val headers: Map<String, String> = emptyMap(),
        val body: ByteArray = ByteArray(0)
    )

    /** Local HTTP/1.1 fixture; it accepts one request per connection and never uses external services. */
    private class Fixture(private val respond: (SeenRequest) -> Reply) : AutoCloseable {
        private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val baseUrl = "http://127.0.0.1:${server.localPort}"
        val requests = CopyOnWriteArrayList<SeenRequest>()
        private val worker = Thread({
            while (!server.isClosed) {
                try {
                    server.accept().use(::serve)
                } catch (_: SocketException) {
                    if (!server.isClosed) throw IllegalStateException("Loopback fixture unexpectedly stopped")
                }
            }
        }, "playback-handoff-fixture").apply { isDaemon = true; start() }

        private fun serve(socket: Socket) {
            socket.soTimeout = 5_000
            val input = BufferedInputStream(socket.getInputStream())
            fun readLine(): String {
                val bytes = ByteArrayOutputStream()
                while (true) {
                    val byte = input.read()
                    if (byte < 0 || byte == '\n'.code) break
                    if (byte != '\r'.code) bytes.write(byte)
                }
                return bytes.toByteArray().toString(Charsets.ISO_8859_1)
            }

            val start = readLine().split(' ', limit = 3)
            if (start.size < 2) return
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = readLine()
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
            val request = SeenRequest(start[0], start[1], headers)
            requests += request
            val reply = respond(request)
            val reason = when (reply.status) {
                200 -> "OK"
                302 -> "Found"
                else -> "Fixture"
            }
            val responseHeaders = LinkedHashMap(reply.headers)
            responseHeaders.putIfAbsent("Content-Length", reply.body.size.toString())
            responseHeaders.putIfAbsent("Connection", "close")
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 ${reply.status} $reason\r\n".toByteArray(Charsets.ISO_8859_1))
            responseHeaders.forEach { (name, value) ->
                output.write("$name: $value\r\n".toByteArray(Charsets.ISO_8859_1))
            }
            output.write("\r\n".toByteArray())
            if (request.method != "HEAD") output.write(reply.body)
            output.flush()
        }

        override fun close() {
            server.close()
            worker.join(1_000)
        }
    }

    private val addonHeaders = mapOf(
        "Authorization" to "Bearer fixture-token",
        "Cookie" to "session=fixture",
        "X-Provider-Key" to "fixture-key",
        "Referer" to "https://provider.example/watch",
        "Origin" to "https://provider.example",
        "User-Agent" to "PlavioHandoffFixture/1.0",
        "Range" to "bytes=99-"
    )

    private fun playerHeaders(): Map<String, String> = PlaybackHeaders.buildPlaybackHeaders(addonHeaders, isTv = false)

    /** Mirror PlayerScreen's relevant MIME precedence: recognized request/probe MIME, then full-URL inference. */
    private fun mediaSource(url: String, mimeType: String?, headers: Map<String, String>): MediaSource {
        val dataSourceFactory = PlaybackHttp.factory(url, headers)
        val inferredMime = PlaybackContentProbe.recognizedMime(mimeType) ?: PlaybackHeaders.inferMimeType(url)
        val item = MediaItem.Builder().setUri(url).apply {
            inferredMime?.let(::setMimeType)
        }.build()
        return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(item)
    }

    private fun readManifest(factory: DataSource.Factory, url: String): String {
        val source = factory.createDataSource()
        return try {
            source.open(DataSpec(Uri.parse(url)))
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(512)
            while (true) {
                val count = source.read(buffer, 0, buffer.size)
                if (count == C.RESULT_END_OF_INPUT) break
                if (count > 0) output.write(buffer, 0, count)
            }
            output.toByteArray().toString(Charsets.UTF_8)
        } finally {
            source.close()
        }
    }

    private fun assertPlayerHeaders(request: SeenRequest, expected: Map<String, String>) {
        expected.forEach { (name, value) -> assertEquals("header $name", value, request.header(name)) }
        // Caller Range is filtered by PlaybackHeaders; it must not override Media3's request range policy.
        assertNull(request.header("Range"))
    }

    @Test
    fun inferredHlsMimeSelectsHlsSourceAndPlaybackRequestsManifestWithPlayerHeaders() {
        Fixture { request ->
            Reply(
                headers = mapOf("Content-Type" to "application/vnd.apple.mpegurl"),
                body = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts\n#EXT-X-ENDLIST\n".toByteArray()
            )
        }.use { fixture ->
            val url = "${fixture.baseUrl}/proxy?url=channel.m3u8"
            val headers = playerHeaders()
            val mime = PlaybackHeaders.inferMimeType(url)
            assertEquals(MimeTypes.APPLICATION_M3U8, mime)

            val source = mediaSource(url, null, headers)
            assertTrue("expected HlsMediaSource, got ${source.javaClass.name}", source is HlsMediaSource)
            assertTrue(readManifest(PlaybackHttp.factory(url, headers), url).startsWith("#EXTM3U"))

            val request = fixture.requests.single()
            assertEquals("GET", request.method)
            assertPlayerHeaders(request, headers)
        }
    }

    @Test
    fun encodedHlsHintSelectsHlsAndPreservesOriginalSignedUrl() {
        Fixture { _ ->
            Reply(
                headers = mapOf("Content-Type" to "application/vnd.apple.mpegurl"),
                body = "#EXTM3U\n#EXT-X-ENDLIST\n".toByteArray()
            )
        }.use { fixture ->
            // The encoded dot hides the extension from naive URL suffix checks; the signature query must stay untouched.
            val url = "${fixture.baseUrl}/api/manifest?sig=abc%2Fdef&format=playlist%2Em3u8"
            val headers = playerHeaders()
            val mime = PlaybackHeaders.inferMimeType(url)
            assertEquals(MimeTypes.APPLICATION_M3U8, mime)

            val source = mediaSource(url, null, headers)
            assertTrue("expected HlsMediaSource, got ${source.javaClass.name}", source is HlsMediaSource)
            assertTrue(readManifest(PlaybackHttp.factory(url, headers), url).startsWith("#EXTM3U"))

            val request = fixture.requests.single()
            assertEquals("GET", request.method)
            assertEquals("${url.removePrefix(fixture.baseUrl)}", request.path)
            assertPlayerHeaders(request, headers)
        }
    }

    @Test
    fun probedExtensionlessDashMimeSelectsDashSourceAndProbeAndPlaybackHeadersMatch() {
        Fixture { _ ->
            Reply(
                headers = mapOf("Content-Type" to "application/dash+xml; charset=utf-8"),
                body = "<?xml version=\"1.0\"?><MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"></MPD>".toByteArray()
            )
        }.use { fixture ->
            val url = "${fixture.baseUrl}/channel/live"
            val playerHeaders = playerHeaders()
            val probedMime = PlaybackContentProbe.probe(url, addonHeaders)
            assertEquals(MimeTypes.APPLICATION_MPD, probedMime)
            val source = mediaSource(url, probedMime, playerHeaders)
            assertTrue("expected DashMediaSource, got ${source.javaClass.name}", source is DashMediaSource)

            assertEquals("<?xml version=\"1.0\"?><MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"></MPD>",
                readManifest(PlaybackHttp.factory(url, playerHeaders), url))
            assertEquals(listOf("HEAD", "GET"), fixture.requests.map { it.method })
            fixture.requests.forEach { assertPlayerHeaders(it, playerHeaders) }
            playerHeaders.forEach { (name, value) ->
                assertEquals("probe/playback parity for $name", fixture.requests[0].header(name), fixture.requests[1].header(name))
                assertEquals(value, fixture.requests[0].header(name))
            }
        }
    }

    @Test
    fun crossOriginRedirectDropsCredentialsButKeepsTransportUserAgent() {
        Fixture { _ ->
            Reply(
                status = 200,
                headers = mapOf("Content-Type" to "application/vnd.apple.mpegurl"),
                body = "#EXTM3U\n#EXT-X-ENDLIST\n".toByteArray()
            )
        }.use { destination ->
            lateinit var origin: Fixture
            origin = Fixture { _ -> Reply(status = 302, headers = mapOf("Location" to "${destination.baseUrl}/manifest.m3u8")) }
            origin.use {
                val url = "${origin.baseUrl}/redirect"
                val headers = playerHeaders()
                val mime = MimeTypes.APPLICATION_M3U8
                val source = mediaSource(url, mime, headers)
                assertTrue(source is HlsMediaSource)
                assertTrue(readManifest(PlaybackHttp.factory(url, headers), url).startsWith("#EXTM3U"))

                val redirectRequest = origin.requests.single()
                assertPlayerHeaders(redirectRequest, headers)
                val redirectedRequest = destination.requests.single()
                listOf("Authorization", "Cookie", "X-Provider-Key", "Referer", "Origin").forEach {
                    assertNull("$it leaked on cross-origin redirect", redirectedRequest.header(it))
                }
                assertEquals(headers["User-Agent"], redirectedRequest.header("User-Agent"))
            }
        }
    }

    @Test
    fun unsupportedTransportSchemeIsRejectedBeforeAnyHttpFixtureRequest() {
        Fixture { _ -> Reply() }.use { fixture ->
            val unsupportedUrl = "rtmp://127.0.0.1:${fixture.baseUrl.substringAfterLast(':')}/live"
            val dataSource = PlaybackHttp.factory(unsupportedUrl, playerHeaders()).createDataSource()
            var rejected = false
            try {
                dataSource.open(DataSpec(Uri.parse(unsupportedUrl)))
            } catch (_: Exception) {
                rejected = true
            } finally {
                dataSource.close()
            }
            assertTrue("PlaybackHttp's HTTP transport should reject RTMP", rejected)
            assertFalse("unsupported URI must not produce an HTTP request", fixture.requests.isNotEmpty())
        }
    }
}
