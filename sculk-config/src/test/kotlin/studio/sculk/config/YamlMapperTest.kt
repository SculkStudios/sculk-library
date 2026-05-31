package studio.sculk.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.annotation.SculkInternal
import studio.sculk.config.annotation.ConfigFile
import studio.sculk.config.annotation.Max
import studio.sculk.config.annotation.Min
import studio.sculk.config.annotation.NotEmpty
import studio.sculk.config.yaml.YamlMapper
import java.io.File

@OptIn(SculkInternal::class)
class YamlMapperTest {
    @ConfigFile("settings.yml")
    data class Settings(
        val maxHomes: Int = 5,
        val allowFlight: Boolean = false,
        val prefix: String = "<green>Test",
    )

    @ConfigFile("validated.yml")
    data class Validated(
        @param:Min(1) val minVal: Int = 5,
        @param:Max(10) val maxVal: Int = 5,
        @param:NotEmpty val name: String = "hello",
    )

    @ConfigFile("env.yml")
    data class EnvConfig(
        val message: String = "hi",
    )

    @Test
    fun `env substitution uses default when variable is unset`(
        @TempDir dir: File,
    ) {
        val file = File(dir, "env.yml")
        file.writeText("message: \"\${SCULK_TEST_UNSET:-fallback}\"\n")

        assertEquals("fallback", YamlMapper.load(file, EnvConfig::class).message)
    }

    @Test
    fun `env substitution keeps placeholder when unset and no default`(
        @TempDir dir: File,
    ) {
        val file = File(dir, "env.yml")
        file.writeText("message: \"\${SCULK_TEST_UNSET}\"\n")

        assertEquals("\${SCULK_TEST_UNSET}", YamlMapper.load(file, EnvConfig::class).message)
    }

    @Test
    fun `load returns defaults when file does not exist`(
        @TempDir dir: File,
    ) {
        val file = File(dir, "settings.yml")
        val result = YamlMapper.load(file, Settings::class)
        assertEquals(5, result.maxHomes)
        assertEquals(false, result.allowFlight)
    }

    @Test
    fun `save and reload round-trips correctly`(
        @TempDir dir: File,
    ) {
        val file = File(dir, "settings.yml")
        val original = Settings(maxHomes = 10, allowFlight = true, prefix = "<red>Hi")
        YamlMapper.save(file, original)
        val loaded = YamlMapper.load(file, Settings::class)
        assertEquals(10, loaded.maxHomes)
        assertEquals(true, loaded.allowFlight)
        assertEquals("<red>Hi", loaded.prefix)
    }

    @Test
    fun `writeDefaults creates file with default values`(
        @TempDir dir: File,
    ) {
        val file = File(dir, "settings.yml")
        YamlMapper.writeDefaults(file, Settings::class)
        assertTrue(file.exists())
        val loaded = YamlMapper.load(file, Settings::class)
        assertEquals(5, loaded.maxHomes)
    }

    @Test
    fun `writeDefaults preserves existing values`(
        @TempDir dir: File,
    ) {
        val file = File(dir, "settings.yml")
        YamlMapper.save(file, Settings(maxHomes = 99))
        YamlMapper.writeDefaults(file, Settings::class)
        val loaded = YamlMapper.load(file, Settings::class)
        assertEquals(99, loaded.maxHomes)
    }

    @Test
    fun `validate passes valid instance`() {
        val violations = YamlMapper.validate(Validated())
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `validate catches min violation`() {
        val violations = YamlMapper.validate(Validated(minVal = 0))
        assertTrue(violations.any { it.contains("minVal") })
    }

    @Test
    fun `validate catches max violation`() {
        val violations = YamlMapper.validate(Validated(maxVal = 11))
        assertTrue(violations.any { it.contains("maxVal") })
    }

    @Test
    fun `validate catches NotEmpty violation`() {
        val violations = YamlMapper.validate(Validated(name = ""))
        assertTrue(violations.any { it.contains("name") })
    }

    @Test
    fun `validate reads parameter-target annotations`() {
        val violations = YamlMapper.validate(Validated(minVal = 0, maxVal = 11, name = ""))
        assertTrue(violations.any { it.contains("minVal") })
        assertTrue(violations.any { it.contains("maxVal") })
        assertTrue(violations.any { it.contains("name") })
    }

    @Test
    fun `camelToKebab converts correctly`() {
        assertEquals("max-homes", YamlMapper.camelToKebab("maxHomes"))
        assertEquals("allow-flight", YamlMapper.camelToKebab("allowFlight"))
        assertEquals("prefix", YamlMapper.camelToKebab("prefix"))
    }
}
