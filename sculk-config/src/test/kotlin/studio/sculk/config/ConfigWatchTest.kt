package studio.sculk.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import studio.sculk.SculkFailure
import studio.sculk.getOrThrow
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ConfigWatchTest {
    @TempDir
    lateinit var directory: Path

    private val file get() = directory.resolve("config.yml")

    private fun load(): ConfigFile<Settings> = SculkConfig.load<Settings>(file).getOrThrow()

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
    }

    @Test
    fun `the watcher reloads once per outside edit and never for Sculk's own write`() {
        val config = load()
        val seen = CopyOnWriteArrayList<Int>()
        val failures = CopyOnWriteArrayList<SculkFailure>()
        config.onChange { seen += it.maxPlayers }
        config.watch(debounceMillis = DEBOUNCE_MILLIS) { failures += it }.getOrThrow().use {
            config.save(config.value.copy(maxPlayers = 5)).getOrThrow()
            Thread.sleep(SETTLE_MILLIS)
            assertEquals(listOf(5), seen, "Sculk's own write was reloaded")
            file.writeText(file.readText().replace("max-players: 5", "max-players: 6"))
            awaitUntil { seen.size == 2 }
            Thread.sleep(SETTLE_MILLIS)
        }
        assertEquals(listOf(5, 6), seen)
        assertTrue(failures.isEmpty(), failures.toString())
    }

    @Test
    fun `a throwing listener is reported and the others still run`() {
        val config = load()
        val failures = CopyOnWriteArrayList<SculkFailure>()
        val seen = CopyOnWriteArrayList<Int>()
        config.onChange { error("listener bug") }
        config.onChange { seen += it.maxPlayers }
        config.watch(debounceMillis = DEBOUNCE_MILLIS) { failures += it }.getOrThrow().use {
            file.writeText(file.readText().replace("max-players: 100", "max-players: 7"))
            awaitUntil { seen.isNotEmpty() && failures.isNotEmpty() }
        }
        assertEquals(listOf(7), seen)
        assertTrue(failures.single() is SculkFailure.OperationFailed, failures.toString())
    }

    @Test
    fun `a rejected outside edit is reported and keeps the previous value`() {
        val config = load()
        val failures = CopyOnWriteArrayList<SculkFailure>()
        config.watch(debounceMillis = DEBOUNCE_MILLIS) { failures += it }.getOrThrow().use {
            file.writeText(file.readText().replace("max-players: 100", "max-players: 900"))
            awaitUntil { failures.isNotEmpty() }
        }
        assertEquals("max-players", (failures.single() as InvalidConfig).path)
        assertEquals(100, config.value.maxPlayers)
    }

    @Test
    fun `a closed file cannot be watched again`() {
        val config = load()
        config.close()
        assertThrows<IllegalStateException> { config.watch { } }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 50L
        const val SETTLE_MILLIS = 400L
        const val POLL_MILLIS = 20L
        const val TIMEOUT_MILLIS = 5_000L
    }
}
