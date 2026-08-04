package studio.sculk.web.ktor

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import studio.sculk.web.HttpMethod
import studio.sculk.web.SculkWebServer
import studio.sculk.web.WebConfig
import studio.sculk.web.WebResponse
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI

/**
 * The Ktor backend, against a real socket.
 *
 * A backend tested through its own abstraction proves nothing -- the whole point of this module is
 * that it talks HTTP correctly, so these bind a port and use a real client.
 */
@OptIn(SculkInternal::class)
class KtorWebServerTest {
    private var server: SculkWebServer? = null

    @AfterEach
    fun tearDown() {
        server?.close()
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun start(configure: SculkWebServer.() -> Unit): Int {
        val port = freePort()
        val instance = KtorWebProvider().create(WebConfig(port = port, bind = "127.0.0.1"))
        instance.configure()
        val result = runBlocking { instance.start() }
        assertTrue(result is SculkResult.Success, "server did not start")
        server = instance
        return port
    }

    private fun get(port: Int, path: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        headers.forEach(connection::setRequestProperty)
        return try {
            val status = connection.responseCode
            val body = (if (status < 400) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            status to body
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `a registered route answers over http`() {
        val port = start {
            route(HttpMethod.GET, "/api/ping") { WebResponse.json("""{"pong":true}""") }
        }

        val (status, body) = get(port, "/api/ping")

        assertEquals(200, status)
        assertTrue(body.contains("pong"))
    }

    @Test
    fun `an unregistered path answers 404`() {
        val port = start { route(HttpMethod.GET, "/api/ping") { WebResponse.noContent() } }

        assertEquals(404, get(port, "/api/nothing").first)
    }

    @Test
    fun `a handler's status code reaches the client`() {
        val port = start {
            route(HttpMethod.GET, "/api/denied") { WebResponse.error(403, "not permitted") }
        }

        val (status, body) = get(port, "/api/denied")

        assertEquals(403, status)
        assertTrue(body.contains("not permitted"))
    }

    @Test
    fun `response headers reach the client`() {
        val port = start {
            route(HttpMethod.GET, "/api/h") {
                WebResponse(200, "{}", headers = mapOf("X-Test" to "value"))
            }
        }

        val connection = URI("http://127.0.0.1:$port/api/h").toURL().openConnection() as HttpURLConnection
        try {
            assertEquals("value", connection.getHeaderField("X-Test"))
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `the handler sees the request path and headers`() {
        var seenPath: String? = null
        var seenHeader: String? = null

        val port = start {
            route(HttpMethod.GET, "/api/echo") { request ->
                seenPath = request.path
                seenHeader = request.header("x-custom")
                WebResponse.noContent()
            }
        }

        get(port, "/api/echo", mapOf("X-Custom" to "hello"))

        assertEquals("/api/echo", seenPath)
        assertEquals("hello", seenHeader)
    }

    @Test
    fun `the query string is not part of the path`() {
        var seenPath: String? = null

        val port = start {
            route(HttpMethod.GET, "/api/q") { request ->
                seenPath = request.path
                WebResponse.noContent()
            }
        }

        get(port, "/api/q?page=2")

        // A path carrying its query would never match a route, and the mismatch is invisible.
        assertEquals("/api/q", seenPath)
    }

    @Test
    fun `an untrusted client cannot spoof its address with a forwarded header`() {
        var seen: String? = null

        val port = start {
            route(HttpMethod.GET, "/api/who") { request ->
                seen = request.remoteAddress
                WebResponse.noContent()
            }
        }

        get(port, "/api/who", mapOf("X-Forwarded-For" to "1.2.3.4"))

        // No proxy is trusted by default, so the header is ignored. Believing it would let any
        // client claim any address and defeat rate limiting and address-bound sessions at once.
        assertEquals("127.0.0.1", seen)
    }

    @Test
    fun `a forwarded header is honoured when the peer is a trusted proxy`() {
        var seen: String? = null
        val port = freePort()
        val instance = KtorWebProvider().create(
            WebConfig(port = port, bind = "127.0.0.1", trustedProxies = setOf("127.0.0.1")),
        )
        instance.route(HttpMethod.GET, "/api/who") { request ->
            seen = request.remoteAddress
            WebResponse.noContent()
        }
        runBlocking { instance.start() }
        server = instance

        get(port, "/api/who", mapOf("X-Forwarded-For" to "1.2.3.4, 10.0.0.1"))

        assertEquals("1.2.3.4", seen)
    }

    @Test
    fun `registering a route after start is refused rather than ignored`() {
        val port = start { route(HttpMethod.GET, "/api/ping") { WebResponse.noContent() } }
        assertTrue(port > 0)

        // A route silently not being served is far harder to diagnose than one that refuses.
        assertThrows(IllegalStateException::class.java) {
            server!!.route(HttpMethod.GET, "/api/late") { WebResponse.noContent() }
        }
    }

    @Test
    fun `binding an occupied port fails with a message naming the cause`() {
        ServerSocket(0).use { occupied ->
            val instance = KtorWebProvider().create(WebConfig(port = occupied.localPort, bind = "127.0.0.1"))
            val result = runBlocking { instance.start() }
            instance.close()

            // Asserted rather than guarded by an `is Failure` check: binding a socket that is
            // already open must fail, and a version of this test that quietly passes when it
            // succeeds is not testing anything.
            val failure = assertInstanceOf(SculkResult.Failure::class.java, result)

            // An operator reading "failed to start" learns nothing actionable. The engine binds on
            // a coroutine, so the BindException arrives wrapped -- matching on the thrown type
            // alone sent this down the generic branch and dropped the word entirely.
            assertTrue(failure.message.contains("port", ignoreCase = true), failure.message)
            assertTrue(failure.message.contains(occupied.localPort.toString()), failure.message)
        }
    }

    @Test
    fun `closing stops the server`() {
        val port = start { route(HttpMethod.GET, "/api/ping") { WebResponse.noContent() } }
        server!!.close()

        assertFalse(server!!.running)
        assertThrows(Exception::class.java) { get(port, "/api/ping") }
    }
}
