package studio.sculk.config

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.getOrThrow
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class Limits(
    val perWorld: Map<String, Int> = mapOf("world" to 10, "nether" to 5),
    val byMode: Map<Mode, Int> = mapOf(Mode.SURVIVAL to 1),
)

class ConfigSaveTest {
    @TempDir
    lateinit var directory: Path

    private val file get() = directory.resolve("config.yml")

    private fun load(
        options: ConfigOptions<Settings>.() -> Unit = {
        },
    ): ConfigFile<Settings> = SculkConfig.load<Settings>(file, options).getOrThrow()

    @Test
    fun `a save that breaks an annotation check is refused and changes nothing`() {
        val config = load()
        val before = file.readText()
        val failure = config.save(config.value.copy(maxPlayers = 900))
        assertTrue(failure is InvalidConfig, failure.toString())
        assertEquals("max-players", (failure as InvalidConfig).path)
        assertEquals(before, file.readText())
        assertEquals(100, config.value.maxPlayers)
    }

    @Test
    fun `a save that breaks a validation rule is refused and changes nothing`() {
        val config = load { validate { settings -> ensure(settings.worlds.isNotEmpty(), "worlds", "at least one world") { "none" } } }
        val before = file.readText()
        val failure = config.save(config.value.copy(worlds = emptyList()))
        assertEquals("worlds", (failure as InvalidConfig).path)
        assertEquals(before, file.readText())
        assertEquals(listOf("world"), config.value.worlds)
    }

    @Test
    fun `a save into a deleted file writes it again with the schema version the plugin writes`() {
        val config = load { version = 3 }
        Files.delete(file)
        config.save(config.value.copy(maxPlayers = 30)).getOrThrow()
        assertTrue("config-version: 3" in file.readText(), file.readText())
        assertTrue("max-players: 30" in file.readText(), file.readText())
    }

    @Test
    fun `a save refuses to overwrite an edit made on disk since the file was read`() {
        val config = load()
        val edited = file.readText().replace("max-players: 100", "max-players: 7")
        file.writeText(edited)
        val failure = config.save(config.value.copy(maxPlayers = 30))
        assertTrue(failure is ConfigConflict, failure.toString())
        assertEquals(edited, file.readText())
        assertEquals(100, config.value.maxPlayers)
        assertEquals(7, config.reload().getOrThrow().maxPlayers)
        config.save(config.value.copy(maxPlayers = 30)).getOrThrow()
        assertTrue("max-players: 30" in file.readText(), file.readText())
    }

    @Test
    fun `a save refuses while an outside edit that does not load is still on disk`() {
        val config = load()
        file.writeText("max-players: [oops\n")
        assertTrue(config.reload() is InvalidConfig)
        assertTrue(config.save(config.value.copy(maxPlayers = 30)) is ConfigConflict)
        assertEquals("max-players: [oops\n", file.readText())
    }

    @Test
    fun `a save keeps the operator's comments, key order and unknown keys`() {
        file.writeText("# Server settings\nlegacy-option: keep me # still read by an old tool\nmax-players: 20 # cap\n")
        val config = load()
        config.save(config.value.copy(maxPlayers = 30)).getOrThrow()
        val text = file.readText()
        assertTrue(text.startsWith("# Server settings\n"), text)
        assertTrue("legacy-option: keep me # still read by an old tool" in text, text)
        assertTrue("max-players: 30 # cap" in text, text)
        assertTrue(text.indexOf("legacy-option") < text.indexOf("max-players"), text)
    }

    @Test
    fun `a save never puts file contents in its failure`() {
        val config = load()
        file.writeText("storage:\n  type: mysql\n  password: hunter2\n  host: [unclosed\n")
        val failure = config.save(config.value)
        assertTrue(failure is SculkFailure, failure.toString())
        assertFalse("hunter2" in (failure as SculkFailure).describe(), failure.describe())
        val reloaded = config.reload()
        assertTrue(reloaded is InvalidConfig, reloaded.toString())
        assertFalse("hunter2" in (reloaded as InvalidConfig).describe(), reloaded.describe())
    }

    @Test
    fun `a map entry removed in code is removed from the file`() {
        val limits = SculkConfig.load<Limits>(file).getOrThrow()
        limits.save(limits.value.copy(perWorld = mapOf("world" to 12))).getOrThrow()
        val text = file.readText()
        assertTrue("world: 12" in text, text)
        assertFalse("nether" in text, text)
        assertEquals(mapOf("world" to 12), SculkConfig.load<Limits>(file).getOrThrow().value.perWorld)
    }

    @Test
    fun `a map entry the operator wrote differently is not added a second time`() {
        file.writeText("per-world:\n  world: 3\nby-mode:\n  Hard-Core: 2\n")
        val limits = SculkConfig.load<Limits>(file).getOrThrow()
        assertEquals(mapOf(Mode.HARD_CORE to 2), limits.value.byMode)
        assertEquals(1, Regex("(?i)hard.core").findAll(file.readText()).count(), file.readText())
    }

    @Test
    fun `a map entry's comment survives a save that changes another entry`() {
        file.writeText("per-world:\n  # the main world\n  world: 3\n  nether: 1\n")
        val limits = SculkConfig.load<Limits>(file).getOrThrow()
        limits.save(limits.value.copy(perWorld = mapOf("world" to 3, "end" to 2))).getOrThrow()
        val text = file.readText()
        assertTrue("# the main world\n  world: 3" in text, text)
        assertTrue("end: 2" in text, text)
        assertFalse("nether" in text, text)
    }

    @Test
    fun `text that looks like a placeholder is saved escaped and reads back unchanged`() {
        val config = load { environment = { "resolved" } }
        config.save(config.value.copy(welcomeMessage = "Use \${HOME} literally")).getOrThrow()
        assertTrue("\$\${HOME}" in file.readText(), file.readText())
        assertEquals("Use \${HOME} literally", config.reload().getOrThrow().welcomeMessage)
    }

    @Test
    fun `a failed write leaves no temporary file behind`() {
        val target = directory.resolve("taken.yml")
        Files.createDirectories(target)
        target.resolve("inside").writeText("x")
        val failed = runCatching { ConfigLoader.write(target, "a: 1\n") }
        assertTrue(failed.isFailure)
        assertFalse(directory.resolve(".taken.yml.tmp").exists())
    }

    @Test
    fun `a write the file system refuses leaves the file as it was`() {
        file.writeText("a: 1\n")
        assertThrows<AccessDeniedException> {
            ConfigLoader.write(file, "a: 2\n") { _, to -> throw AccessDeniedException(to.toString()) }
        }
        assertEquals("a: 1\n", file.readText())
        assertFalse(directory.resolve(".config.yml.tmp").exists())
    }

    @Test
    fun `a file no rename can replace, such as a bind mount, is rewritten in place`() {
        file.writeText("a: 1\n")
        ConfigLoader.write(file, "a: 2\n") { from, to ->
            throw AtomicMoveNotSupportedException(from.toString(), to.toString(), "cross-device")
        }
        assertEquals("a: 2\n", file.readText())
        ConfigLoader.write(file, "a: 3\n") { _, to -> throw FileSystemException(to.toString(), null, "Device or resource busy") }
        assertEquals("a: 3\n", file.readText())
        assertFalse(directory.resolve(".config.yml.tmp").exists())
    }

    @Test
    fun `a float is written as the float, not as the double it widens to`() {
        val rates = SculkConfig.load<Rates>(file).getOrThrow()
        assertTrue(Regex("(?m)^chance: 0\\.1$").containsMatchIn(file.readText()), file.readText())
        rates.save(rates.value.copy(chance = 0.3f)).getOrThrow()
        assertTrue(Regex("(?m)^chance: 0\\.3$").containsMatchIn(file.readText()), file.readText())
        assertEquals(0.3f, SculkConfig.load<Rates>(file).getOrThrow().value.chance)
    }

    @Test
    fun `a listener may reload from another thread without deadlocking`() {
        val config = load()
        val started = AtomicBoolean()
        var finished = false
        config.onChange {
            if (!started.compareAndSet(false, true)) return@onChange
            val other = Thread { finished = config.reload() is SculkResult.Success }
            other.start()
            other.join(TIMEOUT_MILLIS)
        }
        config.save(config.value.copy(maxPlayers = 8)).getOrThrow()
        assertTrue(finished)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 2_000L
    }
}
