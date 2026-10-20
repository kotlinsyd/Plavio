package com.pulsestream.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
class StreamProberTest {
    private data class SeenRequest(val method: String, val path: String, val headers: Map<String, String>) {
        fun header(name: String): String? = headers[name.lowercase()]
    }

    private data class Reply(
        val status: Int = 200,
        val headers: Map<String, String> = emptyMap(),
        val body: ByteArray = ByteArray(0)
    )

    /** A loopback-only HTTP fixture, intentionally independent of live services and credentials. */
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
        }, "stream-prober-fixture").apply { isDaemon = true; start() }

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
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] =
                        line.substring(separator + 1).trim()
                }
            }
            val request = SeenRequest(start[0], start[1], headers)
            requests += request
            val reply = respond(request)
            val reason = when (reply.status) {
                200 -> "OK"
                206 -> "Partial Content"
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

    private val fixtureHeaders = mapOf(
        "Authorization" to "Bearer fixture-only-token",
        "Cookie" to "session=fixture-only",
        "X-Provider-Key" to "fixture-only-key",
        "Referer" to "https://fixture.invalid/watch",
        "Origin" to "https://fixture.invalid",
        "User-Agent" to "StreamProberFixture/1.0"
    )

    private fun mediaReply() = Reply(
        headers = mapOf("Content-Type" to "application/vnd.apple.mpegurl")
    )

    @Test
    fun sameOriginRedirectRetainsSensitiveHeadersAndStreamProbesSuccessfully() = runBlocking {
        lateinit var fixture: Fixture
        fixture = Fixture { request ->
            if (request.path == "/redirect") {
                Reply(status = 302, headers = mapOf("Location" to "${fixture.baseUrl}/playlist"))
            } else mediaReply()
        }
        fixture.use {
            val stream = StreamItem(url = "${fixture.baseUrl}/redirect", headers = fixtureHeaders)

            assertSame(stream, StreamProber.findBest(listOf(stream)))
            assertEquals(listOf("/redirect", "/playlist"), fixture.requests.map { it.path })
            fixture.requests.forEach { request ->
                fixtureHeaders.forEach { (name, value) ->
                    assertEquals("$name on ${request.path}", value, request.header(name))
                }
            }
        }
    }

    @Test
    fun crossOriginRedirectStripsCredentialsAndOriginHeadersButKeepsUserAgent() = runBlocking {
        Fixture { mediaReply() }.use { destination ->
            lateinit var origin: Fixture
            origin = Fixture { _ ->
                Reply(status = 302, headers = mapOf("Location" to "${destination.baseUrl}/playlist"))
            }
            origin.use {
                val headers = fixtureHeaders
                val stream = StreamItem(url = "${origin.baseUrl}/redirect", headers = headers)

                assertSame(stream, StreamProber.findBest(listOf(stream)))
                assertEquals(listOf("/redirect"), origin.requests.map { it.path })
                headers.forEach { (name, value) -> assertEquals(value, origin.requests.single().header(name)) }
                val finalRequest = destination.requests.single()
                assertEquals("/playlist", finalRequest.path)
                listOf("Authorization", "Cookie", "X-Provider-Key", "Referer", "Origin").forEach { name ->
                    assertNull("$name leaked across origins", finalRequest.header(name))
                }
                assertEquals(headers["User-Agent"], finalRequest.header("User-Agent"))
            }
        }
    }

    @Test
    fun genericHtmlHeadAndRangedGetAreRejectedAsUnplayable() = runBlocking {
        Fixture {
            Reply(
                headers = mapOf("Content-Type" to "text/html; charset=utf-8"),
                body = "<!doctype html><html><body>fixture error page</body></html>".toByteArray()
            )
        }.use { fixture ->
            val stream = StreamItem(url = "${fixture.baseUrl}/opaque", headers = fixtureHeaders)

            assertNull(StreamProber.findBest(listOf(stream)))
            assertEquals(listOf("HEAD", "GET"), fixture.requests.map { it.method })
            assertEquals("bytes=0-1023", fixture.requests.last().header("Range"))
            fixture.requests.forEach { request ->
                fixtureHeaders.forEach { (name, value) -> assertEquals(value, request.header(name)) }
            }
            assertTrue(fixture.requests.all { it.path == "/opaque" })
        }
    }
}
