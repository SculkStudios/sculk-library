package studio.sculk.web

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebRequestTest {
    private fun request(headers: Map<String, String> = emptyMap()) = WebRequest(
        method = HttpMethod.GET,
        path = "/api/v1/reports",
        query = emptyMap(),
        headers = headers,
        body = "",
        remoteAddress = "203.0.113.5",
    )

    @Test
    fun `header lookup is case insensitive`() {
        // Header casing is not guaranteed by any client, and a case-sensitive lookup fails
        // intermittently depending on who is calling.
        val req = request(mapOf("Content-Type" to "application/json"))

        assertEquals("application/json", req.header("content-type"))
        assertEquals("application/json", req.header("CONTENT-TYPE"))
    }

    @Test
    fun `a missing header is null rather than blank`() {
        assertNull(request().header("Authorization"))
    }

    @Test
    fun `a cookie is read out of the cookie header`() {
        val req = request(mapOf("Cookie" to "theme=dark; session=abc123; other=x"))

        assertEquals("abc123", req.cookie("session"))
    }

    @Test
    fun `a missing cookie is null`() {
        assertNull(request(mapOf("Cookie" to "theme=dark")).cookie("session"))
        assertNull(request().cookie("session"))
    }

    @Test
    fun `an error body escapes control characters`() {
        // An error message is the cheapest place to break a JSON body, and a quote in a player
        // name is not a hypothetical.
        val response = WebResponse.error(400, "bad \"input\"\nwith a newline")

        assertTrue(response.body.contains("\\\""))
        assertTrue(response.body.contains("\\n"))
        assertFalse(response.body.contains('\n'))
    }

    @Test
    fun `the default bind is loopback`() {
        // The inconvenient default is the correct one: binding 0.0.0.0 on first enable is a
        // plaintext admin panel on the public internet.
        assertEquals("127.0.0.1", WebConfig().bind)
    }

    @Test
    fun `no proxy is trusted by default`() {
        // Believing X-Forwarded-For from an unvalidated source lets a client claim any address,
        // defeating rate limiting and address-bound sessions at once.
        assertTrue(WebConfig().trustedProxies.isEmpty())
    }

    @Test
    fun `an out-of-range port is rejected at construction`() {
        assertThrows(IllegalArgumentException::class.java) { WebConfig(port = 70000) }
    }

    @Test
    fun `a non-positive body limit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { WebConfig(maxBodyBytes = 0) }
    }

    @Test
    fun `the fake dispatches a registered route`() = runTest {
        val server = FakeWebServer()
        server.route(HttpMethod.GET, "/api/v1/reports") { WebResponse.json("""{"ok":true}""") }

        val response = server.dispatch(request())

        assertEquals(200, response.status)
    }

    @Test
    fun `the fake returns 404 for an unregistered route`() = runTest {
        assertEquals(404, FakeWebServer().dispatch(request()).status)
    }

    @Test
    fun `the fake can fail to start so the degraded path is testable`() = runTest {
        val server = FakeWebServer()
        server.startFailure = "port in use"

        assertTrue(server.start() is studio.sculk.SculkResult.Failure)
        assertFalse(server.running)
    }
}
