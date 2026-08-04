package studio.sculk.web.ktor

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.web.SculkWeb
import studio.sculk.web.WebConfig

/**
 * Guards the ServiceLoader descriptor.
 *
 * Worth its own test because the descriptor is a plain text file that no compiler checks: rename
 * [KtorWebProvider] or move its package and everything still builds, then discovery returns nothing
 * at runtime and the dashboard is simply absent with no error pointing at the cause.
 */
class SculkWebDiscoveryTest {
    @Test
    fun `descriptor names a class that exists and implements the SPI`() {
        val descriptor =
            checkNotNull(
                javaClass.classLoader.getResource("META-INF/services/studio.sculk.web.SculkWebProvider"),
            ) { "The ServiceLoader descriptor is missing from the built resources." }

        val named = descriptor.readText().trim().lines().first().trim()

        // Loaded reflectively on purpose: a compile-time reference would keep passing after the
        // descriptor drifted, which is the exact failure this test exists to catch.
        val type = Class.forName(named)
        assertTrue(
            studio.sculk.web.SculkWebProvider::class.java.isAssignableFrom(type),
            "$named does not implement SculkWebProvider",
        )
    }

    @Test
    fun `create finds the ktor backend`() {
        val result = SculkWeb.create(WebConfig(port = 0), listOf(javaClass.classLoader))

        assertTrue(result.isSuccess, "Discovery failed: $result")
        result.getOrNull()!!.use { assertFalse(it.running) }
    }

    @Test
    fun `create fails by name when no backend is visible`() {
        // A parent-only loader that cannot see this module's jar, standing in for a plugin that
        // forgot the backend dependency.
        val blind = ClassLoader.getPlatformClassLoader()

        val result = SculkWeb.create(WebConfig(port = 0), listOf(blind))

        assertFalse(result.isSuccess)
        val message = (result as SculkResult.Failure).message
        assertTrue(
            message.contains("sculk-web-ktor"),
            "The failure must name the missing dependency, was: $message",
        )
    }

    @Test
    fun `a disabled server refuses to start rather than pretending`() = runTest {
        val server = SculkWeb.disabled("web.enabled is false")

        assertFalse(server.running)
        val started = server.start()
        assertFalse(started.isSuccess)
        assertEquals(0, server.port)
        server.close()
    }
}
