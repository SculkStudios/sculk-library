package studio.sculk.config

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import studio.sculk.getOrThrow
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class Bounds(val min: Int = 0, val max: Int = 10) {
    init {
        require(min <= max) { "min to be at most max" }
    }
}

@Serializable
data class Arena(val bounds: Bounds = Bounds())

@Serializable
data class Notes(val motd: String = "hi", val footer: String? = "bye")

class ConfigDecodeTest {
    @TempDir
    lateinit var directory: Path

    private val file get() = directory.resolve("config.yml")

    @Test
    fun `a value its own class rejects is reported at its key, not thrown`() {
        file.writeText("bounds:\n  min: 5\n  max: 1\n")
        val failure = SculkConfig.load<Arena>(file) as InvalidConfig
        assertEquals("bounds", failure.path)
        assertEquals(2, failure.line)
        assertEquals("min to be at most max", failure.expected)
    }

    @Test
    fun `a validation rule that throws is reported, not thrown`() {
        file.writeText("max-players: 20\n")
        val failure = SculkConfig.load<Settings>(file) {
            validate { settings -> require(settings.maxPlayers > 500) { "more than 500 players" } }
        }
        assertTrue(failure is InvalidConfig, failure.toString())
        assertEquals("config.yml: expected more than 500 players, found something else.", (failure as InvalidConfig).message)
    }

    @Test
    fun `defaults that break a validation rule are a wiring bug`() {
        assertThrows<IllegalStateException> {
            SculkConfig.load<Settings>(file) { validate { settings -> ensure(settings.maxPlayers > 500, "max-players", "over 500") { 0 } } }
        }
    }

    @Test
    fun `null is not text, but a nullable key reads it as null`() {
        file.writeText("motd: ~\n")
        val failure = SculkConfig.load<Notes>(file) as InvalidConfig
        assertEquals("motd", failure.path)
        assertEquals("text", failure.expected)
        assertEquals("null", failure.received)
        file.writeText("motd: hello\nfooter: null\n")
        assertNull(SculkConfig.load<Notes>(file).getOrThrow().value.footer)
    }

    @Test
    fun `a doubled dollar sign keeps a placeholder as literal text`() {
        file.writeText("welcome-message: Price \$\${AMOUNT} and \${AMOUNT}\n")
        val config = SculkConfig.load<Settings>(file) { environment = mapOf("AMOUNT" to "5")::get }.getOrThrow()
        assertEquals("Price \${AMOUNT} and 5", config.value.welcomeMessage)
        assertTrue("\$\${AMOUNT}" in file.readText(), file.readText())
    }

    @Test
    fun `a missing migration step is a version bump with nothing to move`() {
        file.writeText("config-version: 1\nmax-players: 10\n")
        val config = SculkConfig.load<Settings>(file) {
            version = 3
            migration(from = 2) { rename("motd", "welcome-message") }
        }.getOrThrow()
        assertEquals(10, config.value.maxPlayers)
        assertTrue("config-version: 3" in file.readText(), file.readText())
    }

    @Test
    fun `keys the schema does not read are reported with their line and kept`() {
        file.writeText("max-playres: 20\nstorage:\n  type: sqlite\n  fiel: x.db\nper-world: {}\n")
        val unknown = mutableListOf<Pair<String, Int?>>()
        SculkConfig.load<Settings>(file) { onUnknownKey = { path, line -> unknown += path to line } }.getOrThrow()
        assertEquals(listOf("max-playres" to 1, "per-world" to 5, "storage.fiel" to 4), unknown.sortedBy { it.first })
        assertTrue("max-playres: 20" in file.readText(), file.readText())
    }

    @Test
    fun `a syntax error names the line but never quotes it`() {
        file.writeText("storage:\n  type: mysql\n  password: hunter2 [\n  host: [unclosed\n")
        val failure = SculkConfig.load<Settings>(file) as InvalidConfig
        assertEquals("valid YAML", failure.expected)
        assertTrue(failure.line != null, failure.describe())
        assertTrue("hunter2" !in failure.describe() && "unclosed" !in failure.describe(), failure.describe())
    }

    @Test
    fun `an alias bomb is rejected before it is expanded`() {
        val bomb = buildString {
            append("a: &a [x, x, x, x, x, x, x, x, x]\n")
            ('b'..'k').forEachIndexed { index, name ->
                val previous = 'a' + index
                append(
                    "$name: &$name [*$previous, *$previous, *$previous, *$previous, *$previous, *$previous, *$previous, *$previous, *$previous]\n",
                )
            }
        }
        file.writeText(bomb)
        val failure = SculkConfig.load<Settings>(file) as InvalidConfig
        assertEquals("valid YAML", failure.expected)
    }
}
