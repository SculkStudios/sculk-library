package studio.sculk.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.config.annotation.ConfigFile
import java.io.File
import java.util.logging.Logger

class ConfigMigrationTest {
    @ConfigFile("settings.yml")
    data class VersionedSettings(
        val configVersion: Int = 2,
        val spawnWorld: String = "world",
        val startingCoins: Int = 100,
    )

    @Test
    fun `registered migrations run before typed config load`(
        @TempDir dir: File,
    ) {
        File(dir, "settings.yml").writeText(
            """
            config-version: 1
            spawn-world-name: legacy_world
            """.trimIndent(),
        )

        val config = SculkConfig.create(dir, Logger.getLogger("test"))
        config.migrations<VersionedSettings> {
            from(1).to(2) { rename("spawn-world-name", "spawn-world") }
        }

        val loaded = config.load<VersionedSettings>()

        assertEquals(2, loaded.configVersion)
        assertEquals("legacy_world", loaded.spawnWorld)
        assertEquals(100, loaded.startingCoins)
    }
}
