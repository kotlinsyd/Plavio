package com.pulsestream.app.data

import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class PlaybackContentProbeTest {
    private data class Request(val method: String, val path: String, val headers: Map<String, String>) {
        fun header(name: String) = headers[name.lowercase()]
    }

    private data class Reply(val status: Int = 200, val headers: Map<String, String> = emptyMap(), val body: ByteArray = ByteArray(0))

    /** Tiny loopback-only HTTP/1.1 server; no Android-incompatible JDK HTTP server or live endpoints. */
    private class FixtureServer(private val respond: (Request) -> Reply) : AutoCloseable {
        private val socket = ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
        val requests = CopyOnWriteArrayList<Request>()
        val base = "http://127.0.0.1:${socket.localPort}"
        private val worker = Thread({
            while (!socket.isClosed) {
                try {
                    socket.accept().use(::serve)
                } catch (error: java.net.SocketException) {
                    if (!socket.isClosed) throw error
                }
            }
        }, "playback-probe-fixture").apply { isDaemon = true; start() }

        private fun serve(client: Socket) {
            client.soTimeout = 5000
            val input = BufferedInputStream(client.getInputStream())
            fun line(): String {
                val bytes = ArrayList<Byte>()
                while (true) {
                    val value = input.read()
                    if (value < 0 || value == '\n'.code) break
                    if (value != '\r'.code) bytes += value.toByte()
                }
                return bytes.toByteArray().toString(Charsets.ISO_8859_1)
            }
            val start = line().split(' ', limit = 3)
            if (start.size < 2) return
            val headers = linkedMapOf<String, String>()
            while (true) {
                val header = line()
                if (header.isEmpty()) break
                val colon = header.indexOf(':')
                if (colon > 0) headers[header.substring(0, colon).trim().lowercase()] = header.substring(colon + 1).trim()
            }
            val request = Request(start[0], start[1], headers)
            requests += request
            val reply = respond(request)
            val reason = when (reply.status) { 200 -> "OK"; 206 -> "Partial Content"; 302 -> "Found"; 405 -> "Method Not Allowed"; else -> "Fixture" }
            val responseHeaders = LinkedHashMap(reply.headers)
            responseHeaders.putIfAbsent("Content-Length", reply.body.size.toString())
            responseHeaders.putIfAbsent("Connection", "close")
            val output = client.getOutputStream()
            output.write("HTTP/1.1 ${reply.status} $reason\r\n".toByteArray(Charsets.ISO_8859_1))
            responseHeaders.forEach { (key, value) -> output.write("$key: $value\r\n".toByteArray(Charsets.ISO_8859_1)) }
            output.write("\r\n".toByteArray())
            if (request.method != "HEAD") output.write(reply.body)
            output.flush()
        }

        override fun close() {
            socket.close()
            worker.join(1000)
        }
    }

    private fun withServer(respond: (Request) -> Reply, test: (FixtureServer) -> Unit) {
        FixtureServer(respond).use(test)
    }

    @Test fun headRejectedFallsBackToSmallGetWithAllRequiredSameOriginHeaders() = withServer({ request ->
        if (request.path == "/channel" && request.method == "HEAD") Reply(status = 405)
        else Reply(
            status = 206,
            headers = mapOf("Content-Type" to "application/octet-stream", "Content-Range" to "bytes 0-47/48"),
            body = "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:6,\nsegment.ts\n".toByteArray()
        )
    }) { server ->
        val headers = mapOf(
            "User-Agent" to "FixturePlayer", "Referer" to "https://fixture.example/",
            "Origin" to "https://fixture.example", "Cookie" to "session=fixture"
        )
        assertEquals("application/x-mpegURL", PlaybackContentProbe.probe("${server.base}/channel", headers))
        assertEquals(listOf("HEAD", "GET"), server.requests.map { it.method })
        val head = server.requests[0]
        val get = server.requests[1]
        listOf(head, get).forEach { request ->
            assertEquals("FixturePlayer", request.header("User-Agent"))
            assertEquals("https://fixture.example/", request.header("Referer"))
            assertEquals("https://fixture.example", request.header("Origin"))
            assertEquals("session=fixture", request.header("Cookie"))
        }
        assertEquals("bytes=0-4095", get.header("Range"))
    }

    @Test fun genericHeadAndExtensionlessDashBodyAreRecognized() = withServer({ request ->
        if (request.method == "HEAD") Reply(headers = mapOf("Content-Type" to "application/octet-stream"))
        else Reply(headers = mapOf("Content-Type" to "application/octet-stream"), body = "<?xml version=\"1.0\"?>\n<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"></MPD>".toByteArray())
    }) { server ->
        assertEquals("application/dash+xml", PlaybackContentProbe.probe("${server.base}/channel", emptyMap()))
        assertEquals(listOf("HEAD", "GET"), server.requests.map { it.method })
    }

    @Test fun protectedSameOriginRedirectKeepsRequiredHeaders() {
        lateinit var server: FixtureServer
        server = FixtureServer { request ->
            if (request.path == "/redirect") Reply(status = 302, headers = mapOf("Location" to "${server.base}/playlist"))
            else Reply(headers = mapOf("Content-Type" to "application/vnd.apple.mpegurl; charset=utf-8"))
        }
        server.use {
            assertEquals("application/x-mpegURL", PlaybackContentProbe.probe("${server.base}/redirect", mapOf(
                "Authorization" to "Bearer fixture", "Cookie" to "session=fixture",
                "X-Provider-Key" to "provider-fixture", "Referer" to "https://fixture.example/",
                "Origin" to "https://fixture.example", "User-Agent" to "FixturePlayer"
            )))
            val finalRequest = server.requests.last()
            assertEquals("Bearer fixture", finalRequest.header("Authorization"))
            assertEquals("session=fixture", finalRequest.header("Cookie"))
            assertEquals("provider-fixture", finalRequest.header("X-Provider-Key"))
            assertEquals("https://fixture.example/", finalRequest.header("Referer"))
            assertEquals("https://fixture.example", finalRequest.header("Origin"))
            assertEquals("FixturePlayer", finalRequest.header("User-Agent"))
            assertEquals("/playlist", finalRequest.path)
        }
    }

    @Test fun crossOriginRedirectDoesNotLeakProviderCredentials() {
        lateinit var destination: FixtureServer
        destination = FixtureServer { Reply(headers = mapOf("Content-Type" to "application/vnd.apple.mpegurl")) }
        destination.use {
            lateinit var origin: FixtureServer
            origin = FixtureServer { request ->
                Reply(status = 302, headers = mapOf("Location" to "${destination.base}/playlist"))
            }
            origin.use {
                assertEquals("application/x-mpegURL", PlaybackContentProbe.probe("${origin.base}/redirect", mapOf(
                    "Authorization" to "Bearer fixture", "Cookie" to "session=fixture", "X-Provider-Key" to "fixture",
                    "Referer" to "https://private.example/", "Origin" to "https://private.example", "User-Agent" to "FixturePlayer"
                )))
                val request = destination.requests.single()
                assertNull(request.header("Authorization"))
                assertNull(request.header("Cookie"))
                assertNull(request.header("X-Provider-Key"))
                assertNull(request.header("Referer"))
                assertNull(request.header("Origin"))
                assertEquals("FixturePlayer", request.header("User-Agent"))
            }
        }
    }

    @Test fun errorPagesAreNotMisclassifiedAsStreams() = withServer({ request ->
        if (request.method == "HEAD") Reply(headers = mapOf("Content-Type" to "text/html"))
        else Reply(headers = mapOf("Content-Type" to "text/html"), body = "<html>Login required</html>".toByteArray())
    }) { server ->
        assertNull(PlaybackContentProbe.probe("${server.base}/bad", emptyMap()))
        assertEquals(listOf("HEAD", "GET"), server.requests.map { it.method })
    }

    @Test fun rangeRejectingLiveGatewayFallsBackToBoundedOrdinaryGet() = withServer({ request ->
        when {
            request.method == "HEAD" -> Reply(status = 405)
            request.header("Range") != null -> Reply(status = 416)
            else -> Reply(headers = mapOf("Content-Type" to "application/octet-stream"),
                body = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts\n".toByteArray())
        }
    }) { server ->
        assertEquals("application/x-mpegURL", PlaybackContentProbe.probe("${server.base}/live", mapOf(
            "User-Agent" to "FixturePlayer", "Referer" to "https://fixture.example/"
        )))
        assertEquals(listOf("HEAD", "GET", "GET"), server.requests.map { it.method })
        assertEquals("bytes=0-4095", server.requests[1].header("Range"))
        assertNull(server.requests[2].header("Range"))
        assertEquals("FixturePlayer", server.requests[2].header("User-Agent"))
        assertEquals("https://fixture.example/", server.requests[2].header("Referer"))
    }

    @Test fun authenticationFailureDoesNotTriggerOrdinaryGetRetry() = withServer({
        Reply(status = 403, headers = mapOf("Content-Type" to "text/html"))
    }) { server ->
        assertNull(PlaybackContentProbe.probe("${server.base}/protected", emptyMap()))
        assertEquals(listOf("HEAD", "GET"), server.requests.map { it.method })
    }

    @Test fun mimeMatchingIsExactNotSubstringBased() {
        assertNull(PlaybackContentProbe.recognizedMime("text/html; fake=mp4"))
        assertNull(PlaybackContentProbe.recognizedMime("application/not-dash"))
        assertEquals("video/mp4", PlaybackContentProbe.recognizedMime("Video/MP4; codecs=avc1"))
    }
}
