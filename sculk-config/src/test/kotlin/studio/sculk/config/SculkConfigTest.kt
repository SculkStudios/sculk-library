package studio.sculk.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import studio.sculk.SculkResult
import studio.sculk.Secret
import studio.sculk.getOrThrow
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class Settings(
    @Comment("Shown to players when they join.")
    val welcomeMessage: String = "<primary>Welcome!",
    @Range(min = 1, max = 500)
    val maxPlayers: Int = 100,
    @Comment("Where player data is stored.")
    val storage: Storage = Storage.Sqlite(),
    val worlds: List<String> = listOf("world"),
    val mode: Mode = Mode.SURVIVAL,
)

enum class Mode { SURVIVAL, HARD_CORE }

@Serializable
sealed interface Storage {
    @Serializable
    @SerialName("sqlite")
    data class Sqlite(val file: String = "data.db") : Storage

    @Serializable
    @SerialName("mysql")
    data class MySql(
        val host: String = "localhost",
        @Range(min = 1, max = 65535) val port: Int = 3306,
        @NotBlank val password: ConfigSecret = Secret("change-me"),
    ) : Storage
}

@Serializable
data class NoDefaults(val required: String)

class SculkConfigTest {
    @TempDir
    lateinit var directory: Path

    private val file get() = directory.resolve("config.yml")

    private fun load(
        options: ConfigOptions<Settings>.() -> Unit = {
        },
    ): SculkResult<ConfigFile<Settings>> = SculkConfig.load<Settings>(file, options)

    @Test
    fun `a missing file is generated from defaults with comments, kebab-case keys and a version`() {
        val config = load { header = listOf("Example plugin configuration.") }.getOrThrow()
        assertEquals(Settings(), config.value)
        val text = file.readText()
        assertTrue("# Example plugin configuration." in text, text)
        assertTrue("config-version: 1" in text, text)
        assertTrue("# Shown to players when they join.\nwelcome-message:" in text, text)
        assertTrue("max-players: 100" in text, text)
        assertTrue("type: sqlite" in text, text)
        assertTrue("mode: survival" in text, text)
    }

    @Test
    fun `an operator's comments and unknown keys survive, and missing keys are added`() {
        file.writeText(
            """
            # My server's settings, do not touch!
            welcome-message: Hi there   # friendly
            legacy-option: keep me
            storage:
              type: sqlite
              file: players.db
            """.trimIndent(),
        )
        val config = load().getOrThrow()
        assertEquals("Hi there", config.value.welcomeMessage)
        assertEquals("players.db", (config.value.storage as Storage.Sqlite).file)
        val text = file.readText()
        assertTrue("# My server's settings, do not touch!" in text, text)
        assertTrue("# friendly" in text, text)
        assertTrue("legacy-option: keep me" in text, text)
        assertTrue("max-players: 100" in text, text)
        assertTrue(text.indexOf("welcome-message") < text.indexOf("legacy-option"), text)
    }

    @Test
    fun `a wrong value names the file, the key, the line, what was expected and what was found`() {
        file.writeText("max-players: 900\n")
        val failure = load() as InvalidConfig
        assertEquals("config.yml: max-players (line 1): expected an integer between 1 and 500, found 900.", failure.message)
    }

    @Test
    fun `nested errors carry the full path`() {
        file.writeText("storage:\n  type: mysql\n  port: seventy\n")
        val failure = load() as InvalidConfig
        assertEquals("config.yml: storage.port (line 3): expected an integer between 1 and 65535, found 'seventy'.", failure.message)
    }

    @Test
    fun `a secret is never echoed in an error`() {
        file.writeText("storage:\n  type: mysql\n  password: '   '\n")
        val failure = load() as InvalidConfig
        assertEquals("<redacted>", failure.received)
        assertFalse("   '" in failure.message)
    }

    @Test
    fun `an unknown sealed type lists the choices`() {
        file.writeText("storage:\n  type: postgres\n")
        val failure = load() as InvalidConfig
        assertEquals("storage.type", failure.path)
        assertEquals("type to be one of mysql, sqlite", failure.expected)
    }

    @Test
    fun `enums accept any case and either separator`() {
        file.writeText("mode: Hard-Core\n")
        assertEquals(Mode.HARD_CORE, load().getOrThrow().value.mode)
    }

    @Test
    fun `environment placeholders resolve on load and are never baked in on save`() {
        file.writeText("storage:\n  type: mysql\n  host: \${DB_HOST:-db.local}\n  password: \${DB_PASSWORD}\n")
        val environment = mapOf("DB_PASSWORD" to "s3cret")
        val config = load { this.environment = environment::get }.getOrThrow()
        val storage = config.value.storage as Storage.MySql
        assertEquals("db.local", storage.host)
        assertEquals("s3cret", storage.password.reveal())
        config.save(config.value.copy(maxPlayers = 42)).getOrThrow()
        val text = file.readText()
        assertTrue("\${DB_PASSWORD}" in text, text)
        assertFalse("s3cret" in text, text)
        assertTrue("max-players: 42" in text, text)
    }

    @Test
    fun `an unset environment variable without a default explains itself`() {
        file.writeText("welcome-message: \${MISSING_VAR}\n")
        val failure = load { environment = { null } } as InvalidConfig
        assertEquals("environment variable MISSING_VAR to be set", failure.expected)
    }

    @Test
    fun `migrations upgrade the tree before decoding and back up the original`() {
        val original = "config-version: 1\nmotd: Old welcome # note\nmax-players: 10\n"
        file.writeText(original)
        val config = load {
            version = 2
            migration(from = 1) { rename("motd", "welcome-message") }
        }.getOrThrow()
        assertEquals("Old welcome", config.value.welcomeMessage)
        val text = file.readText()
        assertTrue("config-version: 2" in text, text)
        assertTrue("welcome-message: Old welcome # note" in text, text)
        assertEquals(original, directory.resolve("config.yml.v1.bak").readText())
    }

    @Test
    fun `a file from a newer plugin is refused`() {
        file.writeText("config-version: 3\n")
        val failure = load { version = 2 } as InvalidConfig
        assertEquals("This file was written by a newer version of the plugin.", failure.hint)
    }

    @Test
    fun `a failed reload keeps the previous value and a good one replaces it`() {
        val config = load().getOrThrow()
        val seen = mutableListOf<Int>()
        config.onChange { seen += it.maxPlayers }
        file.writeText("max-players: [oops\n")
        val broken = config.reload()
        assertTrue(broken is InvalidConfig && broken.expected == "valid YAML", broken.toString())
        assertEquals(100, config.value.maxPlayers)
        file.writeText("max-players: 7\n")
        assertEquals(7, config.reload().getOrThrow().maxPlayers)
        assertEquals(listOf(7), seen)
    }

    @Test
    fun `validation rules report the key and its line`() {
        file.writeText("worlds:\n  - world\n  - world\n")
        val failure = load {
            validate { settings ->
                ensure(settings.worlds.toSet().size == settings.worlds.size, "worlds", "names without duplicates") { settings.worlds }
            }
        } as InvalidConfig
        assertEquals("config.yml: worlds (line 2): expected names without duplicates, found [world, world].", failure.message)
    }

    @Test
    fun `a type without defaults cannot generate a file`() {
        assertThrows<IllegalStateException> { SculkConfig.load<NoDefaults>(directory.resolve("x.yml")) }
    }
}
