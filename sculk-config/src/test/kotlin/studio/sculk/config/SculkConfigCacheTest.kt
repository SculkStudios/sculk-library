package studio.sculk.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.SculkResult
import studio.sculk.config.annotation.ConfigFile
import java.io.File
import java.util.logging.Logger

/**
 * Pins the load/reload contract. `load` caching is the whole point of the API, but it is also
 * the easy way to write a `/reload` command that silently does nothing — so both halves are
 * asserted together.
 */
class SculkConfigCacheTest {
    @ConfigFile("cached.yml")
    data class Cached(val greeting: String = "hello", val maxHomes: Int = 3)

    private fun config(dir: File) = SculkConfig.create(dir, Logger.getLogger("SculkConfigCacheTest"))

    @Test
    fun `load caches, so an edit on disk is not picked up`(@TempDir dir: File) {
        val config = config(dir)
        assertEquals("hello", config.load<Cached>().greeting)

        File(dir, "cached.yml").writeText("greeting: goodbye\nmax-homes: 3\n")

        assertEquals("hello", config.load<Cached>().greeting, "load must serve the cached instance")
    }

    @Test
    fun `reload re-reads the file and updates what load returns`(@TempDir dir: File) {
        val config = config(dir)
        config.load<Cached>()

        File(dir, "cached.yml").writeText("greeting: goodbye\nmax-homes: 9\n")

        val reloaded = config.reload<Cached>()
        assertTrue(reloaded is SculkResult.Success, "reload failed: $reloaded")
        assertEquals("goodbye", (reloaded as SculkResult.Success).value.greeting)

        // The cache has to be refreshed too, or every other holder keeps stale settings.
        assertEquals("goodbye", config.load<Cached>().greeting)
        assertEquals(9, config.load<Cached>().maxHomes)
    }

    @Test
    fun `reload works even when load was never called first`(@TempDir dir: File) {
        val config = config(dir)

        assertTrue(config.reload<Cached>() is SculkResult.Success)
    }
}
