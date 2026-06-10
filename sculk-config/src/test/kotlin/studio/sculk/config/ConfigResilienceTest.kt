package studio.sculk.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.annotation.SculkInternal
import studio.sculk.config.annotation.ConfigFile
import studio.sculk.config.yaml.YamlMapper
import java.io.File
import java.util.logging.Logger

/**
 * The config system's job is to never let an edit silently do nothing and never let a broken
 * file take the plugin down. These tests pin that contract.
 */
@OptIn(SculkInternal::class)
class ConfigResilienceTest {
    enum class Mode { OnlinePlayers, ServerWide }

    @ConfigFile("resilient.yml")
    data class Resilient(
        val maxHomes: Int = 5,
        val allowFlight: Boolean = false,
        val mode: Mode = Mode.OnlinePlayers,
        val nested: Nested = Nested(),
        val entries: List<Nested> = emptyList(),
    )

    data class Nested(val displayName: String = "default", val slot: Int = 1)

    // ------------------------------------------------------------------
    // camelCase fallback
    // ------------------------------------------------------------------

    @Test
    fun `camelCase keys load the same as kebab-case keys`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText(
            """
            maxHomes: 9
            allowFlight: true
            nested:
              displayName: "hello"
            """.trimIndent(),
        )

        val loaded = YamlMapper.load(file, Resilient::class)

        assertEquals(9, loaded.maxHomes)
        assertEquals(true, loaded.allowFlight)
        assertEquals("hello", loaded.nested.displayName)
    }

    @Test
    fun `kebab-case keys win when both forms are present`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText("max-homes: 3\nmaxHomes: 9\n")

        // Note: the camelCase duplicate is reported as unknown? No — both map to the same
        // parameter; kebab is read first, and the camel key is a known alias, not a typo.
        val warnings = mutableListOf<String>()
        val loaded = YamlMapper.load(file, Resilient::class) { warnings.add(it) }

        assertEquals(3, loaded.maxHomes)
        assertTrue(warnings.isEmpty(), "known camelCase aliases must not be reported: $warnings")
    }

    // ------------------------------------------------------------------
    // Unknown keys
    // ------------------------------------------------------------------

    @Test
    fun `typo keys are reported with a did-you-mean suggestion`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText("max-hoems: 9\n")

        val warnings = mutableListOf<String>()
        val loaded = YamlMapper.load(file, Resilient::class) { warnings.add(it) }

        assertEquals(5, loaded.maxHomes)
        assertTrue(warnings.any { it.contains("max-hoems") && it.contains("did you mean 'max-homes'") }, "$warnings")
    }

    @Test
    fun `unknown nested keys are reported with their full path`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText("nested:\n  dsiplay-name: \"x\"\n")

        val warnings = mutableListOf<String>()
        YamlMapper.load(file, Resilient::class) { warnings.add(it) }

        assertTrue(warnings.any { it.contains("nested.dsiplay-name") && it.contains("display-name") }, "$warnings")
    }

    @Test
    fun `config-version is never reported as unknown`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText("config-version: 3\n")

        val warnings = mutableListOf<String>()
        YamlMapper.load(file, Resilient::class) { warnings.add(it) }

        assertTrue(warnings.isEmpty(), "$warnings")
    }

    // ------------------------------------------------------------------
    // Bad values fall back to defaults with a warning instead of crashing
    // ------------------------------------------------------------------

    @Test
    fun `unknown enum value keeps the default and lists valid options`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText("mode: Everybody\n")

        val warnings = mutableListOf<String>()
        val loaded = YamlMapper.load(file, Resilient::class) { warnings.add(it) }

        assertEquals(Mode.OnlinePlayers, loaded.mode)
        assertTrue(warnings.any { it.contains("Everybody") && it.contains("OnlinePlayers") && it.contains("ServerWide") }, "$warnings")
    }

    @Test
    fun `enum matching stays case-insensitive`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText("mode: serverwide\n")

        assertEquals(Mode.ServerWide, YamlMapper.load(file, Resilient::class).mode)
    }

    @Test
    fun `quoted numbers and booleans coerce`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText("max-homes: \"7\"\nallow-flight: \"true\"\n")

        val loaded = YamlMapper.load(file, Resilient::class)

        assertEquals(7, loaded.maxHomes)
        assertEquals(true, loaded.allowFlight)
    }

    @Test
    fun `garbage numbers keep the default with a warning`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText("max-homes: lots\n")

        val warnings = mutableListOf<String>()
        val loaded = YamlMapper.load(file, Resilient::class) { warnings.add(it) }

        assertEquals(5, loaded.maxHomes)
        assertTrue(warnings.any { it.contains("max-homes") && it.contains("lots") }, "$warnings")
    }

    @Test
    fun `scalar where a section is expected keeps the default`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText("nested: oops\n")

        val warnings = mutableListOf<String>()
        val loaded = YamlMapper.load(file, Resilient::class) { warnings.add(it) }

        assertEquals("default", loaded.nested.displayName)
        assertTrue(warnings.any { it.contains("nested") }, "$warnings")
    }

    @Test
    fun `one broken list entry is dropped without losing the rest`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText(
            """
            entries:
              - display-name: "first"
              - oops
              - display-name: "third"
            """.trimIndent(),
        )

        val warnings = mutableListOf<String>()
        val loaded = YamlMapper.load(file, Resilient::class) { warnings.add(it) }

        assertEquals(listOf("first", "third"), loaded.entries.map { it.displayName })
        assertTrue(warnings.any { it.contains("entries[1]") }, "$warnings")
    }

    // ------------------------------------------------------------------
    // Manager-level: a broken file must not crash plugin enable
    // ------------------------------------------------------------------

    @Test
    fun `malformed yaml falls back to defaults and leaves the file untouched`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        val broken = "max-homes: [unclosed\n  nested: ::: not yaml\n"
        file.writeText(broken)

        val config = SculkConfig.create(dir, Logger.getLogger("ConfigResilienceTest"))
        val loaded = config.load(Resilient::class.java)

        assertEquals(5, loaded.maxHomes)
        assertEquals(broken, file.readText(), "a broken file must never be overwritten")
    }

    @Test
    fun `reload after fixing a broken file picks up the new values`(@TempDir dir: File) {
        val file = File(dir, "resilient.yml")
        file.writeText("max-homes: [unclosed\n")

        val config = SculkConfig.create(dir, Logger.getLogger("ConfigResilienceTest"))
        assertEquals(5, config.load(Resilient::class.java).maxHomes)

        file.writeText("max-homes: 12\n")
        val reloaded = config.reload(Resilient::class.java)

        assertTrue(reloaded is studio.sculk.SculkResult.Success)
        assertEquals(12, config.load(Resilient::class.java).maxHomes)
    }
}
