package studio.sculk.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.annotation.SculkInternal
import java.io.File
import java.util.logging.Logger

/**
 * What a rewrite is allowed to do to a file a server owner has edited.
 *
 * Sculk 4.5 could not serialize a config at all, so it never rewrote one: a key added in an update
 * took its default at runtime but never appeared in the file, and the owner had no way to discover
 * the setting existed. 5.0 can round-trip, which fixes that — and introduces the opposite risk,
 * because re-rendering a file is also an opportunity to lose everything in it the data class does
 * not model. These tests pin that it only ever adds.
 */
@OptIn(SculkInternal::class)
class RewriteSafetyTest {
    @TempDir
    lateinit var folder: File

    private fun config() = SculkConfig(folder, Logger.getLogger("test")) { null }

    private fun file(name: String) = File(folder, name)

    @Test
    fun `a new key is added to an existing file with its comment`() {
        file("storage.yml").writeText("backend: mysql\n")

        config().load<StorageSettings>().getOrThrow()

        val text = file("storage.yml").readText()
        assertTrue(text.contains("pool-size: 10"), "the new key appears:\n$text")
        assertTrue(text.contains("# How many connections to keep open."), "and so does its comment:\n$text")
        assertTrue(text.contains("backend: mysql"), "without disturbing the owner's value")
    }

    @Test
    fun `a key the data class does not model survives a rewrite`() {
        // A key from a newer build the owner rolled back from, a setting a sister tool writes, or
        // a typo they are about to fix. Decoding ignores it; the rewrite must not delete it.
        file("storage.yml").writeText("backend: mysql\nkept-by-hand: 42\n")

        config().load<StorageSettings>().getOrThrow()

        val text = file("storage.yml").readText()
        assertTrue(text.contains("kept-by-hand: 42"), "an unmodelled key must not be silently dropped:\n$text")
    }

    @Test
    fun `a comment the owner wrote survives a rewrite`() {
        file("storage.yml").writeText("# do not touch, ask Sam first\nbackend: mysql\n")

        config().load<StorageSettings>().getOrThrow()

        val text = file("storage.yml").readText()
        assertTrue(text.contains("do not touch, ask Sam first"), "a hand-written comment must not be lost:\n$text")
    }

    @Test
    fun `a key added inside an existing section lands in that section`() {
        file("storage.yml").writeText("backend: mysql\nmysql:\n  host: db.internal\n")

        config().load<StorageSettings>().getOrThrow()

        val text = file("storage.yml").readText()
        val lines = text.lines()
        val hostLine = lines.indexOfFirst { it.contains("host: db.internal") }
        val portLine = lines.indexOfFirst { it.trim().startsWith("port:") }

        assertTrue(portLine > hostLine, "the new nested key belongs inside mysql, not at the end:\n$text")
        assertTrue(text.contains("host: db.internal"), "the owner's value survives")
    }

    @Test
    fun `an owner value is never replaced by the default`() {
        file("storage.yml").writeText("backend: postgres\npool-size: 64\n")

        val loaded = config().load<StorageSettings>().getOrThrow()

        assertEquals("postgres", loaded.backend)
        assertEquals(64, loaded.poolSize)
        assertTrue(file("storage.yml").readText().contains("pool-size: 64"))
    }

    @Test
    fun `an up-to-date file is byte-identical after a second load`() {
        config().load<StorageSettings>().getOrThrow()
        val first = file("storage.yml").readText()

        config().load<StorageSettings>().getOrThrow()

        assertEquals(first, file("storage.yml").readText(), "a second load must change nothing")
    }

    @Test
    fun `a file that reads from the environment is still never rewritten`() {
        val source = "backend: \${DB_BACKEND:-mysql}\n"
        file("storage.yml").writeText(source)

        config().load<StorageSettings>().getOrThrow()

        assertEquals(source, file("storage.yml").readText())
    }
}
